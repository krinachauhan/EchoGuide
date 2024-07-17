package com.example.echoguide;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView;

import com.example.echoguide.ml.SsdMobilenetV11Metadata1;

import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


public class objectDetectionActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private List<String> labels;
    private List<Integer> colors = Arrays.asList(
            Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
            Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED);
    private Paint paint = new Paint();
    private ImageProcessor imageProcessor;
    private Bitmap bitmap;
    private ImageView imageView;
    private CameraDevice cameraDevice;
    private Handler handler;
    private CameraManager cameraManager;
    private TextureView textureView;
    private SsdMobilenetV11Metadata1 model;
    private TextToSpeech textToSpeech;
    private boolean isTextToSpeechInitialized = false;

    private boolean objectDetected = false;
    private static final long DELAY_BEFORE_OBJECT_DETECTION_MS = 600; // take 0.6 seconds delay to open the first activity to take time to detect the proper and clear object
    private static final long REOPEN_DELAY_MS = 900; // after 0.9 seconds reopen object detection activity again

    private Handler reopenHandler = new Handler();

    // after give time reopen the object detecte activity
    private Runnable reopenRunnable = new Runnable() {
        @Override
        public void run() {
            // Reopen the camera view activity for new object detection
            recreate();
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_object_detection);
        getPermission();

        textToSpeech = new TextToSpeech(this, this); // check once that you implements TextToSpeech.OnInitListener on main class, above main class

        try {

            // called assets/labels2.txt for gujarati
            labels = FileUtil.loadLabels(this, "labels.txt");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        imageProcessor = new ImageProcessor.Builder().add(new ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build();

        try {
            model = SsdMobilenetV11Metadata1.newInstance(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        HandlerThread handlerThread = new HandlerThread("videoThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        imageView = findViewById(R.id.imageView);

        textureView = findViewById(R.id.textureView);



        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
                //openCamera();

                // Delay before starting object detection, like if object is detect and speak the app will take some moments the detect new object
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        openCamera(); // Start object detection after delay
                    }
                }, DELAY_BEFORE_OBJECT_DETECTION_MS);
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {}

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
                if (!objectDetected) {
                    bitmap = textureView.getBitmap();
                    TensorImage image = TensorImage.fromBitmap(bitmap);
                    image = imageProcessor.process(image);

                    SsdMobilenetV11Metadata1.Outputs outputs = model.process(image);
                    float[] locations = outputs.getLocationsAsTensorBuffer().getFloatArray();
                    float[] classes = outputs.getClassesAsTensorBuffer().getFloatArray();
                    float[] scores = outputs.getScoresAsTensorBuffer().getFloatArray();
                    //float[] numberOfDetections = outputs.getNumberOfDetectionsAsTensorBuffer().getFloatArray();

                    Bitmap mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                    Canvas canvas = new Canvas(mutable);

                    int h = mutable.getHeight();
                    int w = mutable.getWidth();
                    paint.setTextSize(h / 15f);
                    paint.setStrokeWidth(h / 85f);
                    int x = 0;
                    for (int i = 0; i < scores.length; i++) {
                        x = i * 4;
                        if (scores[i] > 0.5) {
                            paint.setColor(colors.get(i));
                            paint.setStyle(Paint.Style.STROKE);
                            canvas.drawRect(new RectF(locations[x + 1] * w, locations[x] * h, locations[x + 3] * w, locations[x + 2] * h), paint);
                            paint.setStyle(Paint.Style.FILL);
                            canvas.drawText(labels.get((int) classes[i]) + " " + scores[i], locations[x + 1] * w, locations[x] * h, paint);
                            objectDetected = true; // Mark that object as detected
                            reopenHandler.postDelayed(reopenRunnable, REOPEN_DELAY_MS); // Schedule reopening of activity

                            // speak(labels.get((int) classes[i])); // Speak the detected object label
                            // Speak detected object
                            if (isTextToSpeechInitialized) {
                                speak(labels.get((int) classes[i]));
                            }

                            break; // Stop processing once an object is detected
                        }
                    }

                    imageView.setImageBitmap(mutable);
                }
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        model.close();
        textToSpeech.shutdown(); // Shutdown TextToSpeech after object is detect

        reopenHandler.removeCallbacks(reopenRunnable); // Remove any pending reopening callbacks

    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cameraManager.openCamera(cameraManager.getCameraIdList()[0], new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        cameraDevice = camera;

                        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
                        Surface surface = new Surface(surfaceTexture);

                        try {
                            CameraDevice.StateCallback stateCallback = this;
                            CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            captureRequestBuilder.addTarget(surface);

                            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(@NonNull CameraCaptureSession session) {
                                    try {
                                        session.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }

                                @Override
                                public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
                            }, handler);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {}

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {}
                }, handler);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 101);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            getPermission();
        }
    }
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.US); // Set the language to Gujarati for text to speech - "gu"

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("Text To Speech", "language is not supported"); // check the log if language is not working
            } else {
                isTextToSpeechInitialized = true; // Set the flag to true after successful initialization
            }
        } else {
            Log.e("Text To Speech", "Initialization failed..!");
        }
    }

    private void speak(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && isTextToSpeechInitialized) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }
}