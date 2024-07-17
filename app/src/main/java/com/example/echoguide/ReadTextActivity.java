package com.example.echoguide;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.IOException;
import java.util.Locale;

public class ReadTextActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private SurfaceView cameraPreview;
    private TextView textResult;
    private CameraSource cameraSource;
    private TextToSpeech textToSpeech;
    private static final int PERMISSION_CAMERA = 100;
    private static final long CAMERA_DURATION = 5000; // 10 seconds


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_read_text);

        cameraPreview = findViewById(R.id.cameraPreview);
        textResult = findViewById(R.id.textResult);

        // Initialize TextToSpeech
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Handle language not supported
                    Toast.makeText(this, "Text-to-speech language not supported", Toast.LENGTH_SHORT).show();
                }
            } else {
                // Handle TextToSpeech initialization failure
                Toast.makeText(this, "Text-to-speech initialization failed", Toast.LENGTH_SHORT).show();
            }
        });

        // Initialize SurfaceView and add callback
        SurfaceHolder surfaceHolder = cameraPreview.getHolder();
        surfaceHolder.addCallback(this);

        // Check and request camera permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA);
        } else {
            startCameraSource();

            // Stop the camera source after CAMERA_DURATION milliseconds
            new Handler().postDelayed(() -> {
                stopCameraSource();
                // Retrieve recognized text and speak it
                String recognizedText = textResult.getText().toString().trim();
                if (!recognizedText.isEmpty()) {
                    new Handler().postDelayed(() -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            textToSpeech.speak(recognizedText, TextToSpeech.QUEUE_FLUSH, null, null);
                        }
                    }, 5000); // 5000 milliseconds (5 seconds) pause
                }
            }, CAMERA_DURATION);
        }
    }

    private void stopCameraSource() {
        if (cameraSource != null) {
            cameraSource.stop();
        }
    }

    private void startCameraSource() {
        final TextRecognizer textRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();
        if (!textRecognizer.isOperational()) {
            Toast.makeText(this, "Text recognition could not be set up", Toast.LENGTH_SHORT).show();
        } else {
            cameraSource = new CameraSource.Builder(getApplicationContext(), textRecognizer)
                    .setFacing(CameraSource.CAMERA_FACING_BACK)
                    .setRequestedPreviewSize(1280, 1024)
                    .setAutoFocusEnabled(true)
                    .setRequestedFps(2.0f)
                    .build();

            cameraPreview.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
                    try {
                        if (ActivityCompat.checkSelfPermission(ReadTextActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return;
                        }
                        cameraSource.start(surfaceHolder);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(ReadTextActivity.this, "Error opening camera", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {
                    // Handle surface changes (e.g., orientation changes)
                    // Not needed for this example
                }

                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
                    if (cameraSource != null) {
                        cameraSource.stop();
                    }
                }
            });

            textRecognizer.setProcessor(new Detector.Processor<TextBlock>() {
                @Override
                public void release() {
                    // Implement any cleanup here
                }

                @Override
                public void receiveDetections(Detector.Detections<TextBlock> detections) {
                    final SparseArray<TextBlock> items = detections.getDetectedItems();
                    if (items.size() != 0) {
                        StringBuilder stringBuilder = new StringBuilder();
                        for (int i = 0; i < items.size(); i++) {
                            TextBlock item = items.valueAt(i);
                            stringBuilder.append(item.getValue());
                            stringBuilder.append("\n");
                        }
                        final String detectedText = stringBuilder.toString().trim();
                        displayTextResult(detectedText);
                    }
                }
            });
        }
    }

    private void displayTextResult(String text) {
        runOnUiThread(() -> {
            textResult.setText(text);

            // Speak the result
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
        // CameraSource will start in the surfaceCreated callback
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        // Handle surface changes (e.g., orientation changes)
        // Not needed for this example
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
        if (cameraSource != null) {
            cameraSource.stop();
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraSource();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}
