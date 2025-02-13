package com.example.echoguide;

import android.Manifest;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.echoguide.tflite.Classifier;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.example.echoguide.env.ImageUtils;
import com.example.echoguide.env.Logger;
import com.example.echoguide.tflite.Classifier.Recognition;
import com.example.echoguide.tflite.Classifier.Device;
import com.example.echoguide.tflite.Classifier.Model;

//import org.tensorflow.lite.backdoor.it.env.ImageUtils;
////import org.tensorflow.lite.backdoor.it.env.Logger;
//import org.tensorflow.lite.backdoor.it.tflite.Classifier.Device;
//import org.tensorflow.lite.backdoor.it.tflite.Classifier.Model;
//import org.tensorflow.lite.backdoor.it.tflite.Classifier.Recognition;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;




public abstract class CameraActivity extends AppCompatActivity
        implements OnImageAvailableListener,
        Camera.PreviewCallback,
        View.OnClickListener,
        AdapterView.OnItemSelectedListener, TextToSpeech.OnInitListener {

    private TextToSpeech textToSpeech;
    private static final Logger LOGGER = new Logger();

    private static final int PERMISSIONS_REQUEST = 1;

    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    protected int previewWidth = 0;
    protected int previewHeight = 0;
    private Handler handler;
    private HandlerThread handlerThread;
    private boolean useCamera2API;
    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;
    private LinearLayout bottomSheetLayout;
    private LinearLayout gestureLayout;
    private BottomSheetBehavior sheetBehavior;
    protected TextView recognitionTextView, recognition1TextView, recognition2TextView, recognitionValueTextView, recognition1ValueTextView, recognition2ValueTextView;
    protected TextView frameValueTextView, cropValueTextView, cameraResolutionTextView, rotationTextView, inferenceTimeTextView;
    protected ImageView bottomSheetArrowImageView;
    private ImageView plusImageView, minusImageView;
    private Spinner modelSpinner;
    private Spinner deviceSpinner;
    private TextView threadsTextView;

    // private Model model = Model.QUANTIZED;
    private Model model = Model.FLOAT;
    private Device device = Device.CPU;
    private int numThreads = -1;
    // for use audio file
    MediaPlayer ten, fifty, one_hun, five_hun, one_thou;
    private final Map<String, MediaPlayer> currencyAudioMap = new HashMap<>();
    private boolean canDetectCurrency = true;
    //    private final Handler handler = new Handler();
    private static final long RESET_DELAY = 5000; // 3 seconds delay

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        LOGGER.d("onCreate " + this);
        super.onCreate(null);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_currancy_det);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        if (hasPermission()) {
            setFragment();
        } else {
            requestPermission();
        }
        textToSpeech = new TextToSpeech(this, this);

        threadsTextView = findViewById(R.id.threads);
        plusImageView = findViewById(R.id.plus);
        minusImageView = findViewById(R.id.minus);
        modelSpinner = findViewById(R.id.model_spinner);
        deviceSpinner = findViewById(R.id.device_spinner);
        bottomSheetLayout = findViewById(R.id.bottom_sheet_layout);
        gestureLayout = findViewById(R.id.gesture_layout);
        sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
        bottomSheetArrowImageView = findViewById(R.id.bottom_sheet_arrow);


//
//        currencyAudioMap.put("10", MediaPlayer.create(this, R.raw.dashrup));
//        currencyAudioMap.put("20", MediaPlayer.create(this, R.raw.vishrup));
//        currencyAudioMap.put("50", MediaPlayer.create(this, R.raw.pachasrup));
//        currencyAudioMap.put("100", MediaPlayer.create(this, R.raw.sorup));
//        currencyAudioMap.put("200", MediaPlayer.create(this, R.raw.besorup));
//        currencyAudioMap.put("500", MediaPlayer.create(this, R.raw.pachsorup));
//

        ViewTreeObserver vto = gestureLayout.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                            gestureLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        } else {
                            gestureLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                        //                int width = bottomSheetLayout.getMeasuredWidth();
                        int height = gestureLayout.getMeasuredHeight();

                        sheetBehavior.setPeekHeight(height);
                    }
                });
        sheetBehavior.setHideable(false);

        sheetBehavior.setBottomSheetCallback(
                new BottomSheetBehavior.BottomSheetCallback() {
                    @Override
                    public void onStateChanged(@NonNull View bottomSheet, int newState) {
                        switch (newState) {
                            case BottomSheetBehavior.STATE_HIDDEN:
                                break;
                            case BottomSheetBehavior.STATE_EXPANDED: {
                                bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_down);
                            }
                            break;
                            case BottomSheetBehavior.STATE_COLLAPSED: {
                                bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                            }
                            break;
                            case BottomSheetBehavior.STATE_DRAGGING:
                                break;
                            case BottomSheetBehavior.STATE_SETTLING:
                                bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                                break;
                        }
                    }

                    @Override
                    public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                    }
                });

        recognitionTextView = findViewById(R.id.detected_item);
        recognitionValueTextView = findViewById(R.id.detected_item_value);
        recognition1TextView = findViewById(R.id.detected_item1);
        recognition1ValueTextView = findViewById(R.id.detected_item1_value);
        recognition2TextView = findViewById(R.id.detected_item2);
        recognition2ValueTextView = findViewById(R.id.detected_item2_value);

        frameValueTextView = findViewById(R.id.frame_info);
        cropValueTextView = findViewById(R.id.crop_info);
        cameraResolutionTextView = findViewById(R.id.view_info);
        rotationTextView = findViewById(R.id.rotation_info);
        inferenceTimeTextView = findViewById(R.id.inference_info);

        modelSpinner.setOnItemSelectedListener(this);
        deviceSpinner.setOnItemSelectedListener(this);

        plusImageView.setOnClickListener(this);
        minusImageView.setOnClickListener(this);

        model = Model.valueOf(modelSpinner.getSelectedItem().toString().toUpperCase());
        device = Device.valueOf(deviceSpinner.getSelectedItem().toString());
        numThreads = Integer.parseInt(threadsTextView.getText().toString().trim());
    }

    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }


    /**
     * Callback for android.hardware.Camera API
     */
    @Override
    public void onPreviewFrame(final byte[] bytes, final Camera camera) {
        if (isProcessingFrame) {
            LOGGER.w("Dropping frame!");
            return;
        }

        try {
            // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null) {
                Camera.Size previewSize = camera.getParameters().getPreviewSize();
                previewHeight = previewSize.height;
                previewWidth = previewSize.width;
                rgbBytes = new int[previewWidth * previewHeight];
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 90);
                }
            }
        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
            return;
        }

        isProcessingFrame = true;
        yuvBytes[0] = bytes;
        yRowStride = previewWidth;

        imageConverter =
                new Runnable() {
                    @Override
                    public void run() {
                        ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);
                    }
                };

        postInferenceCallback =
                new Runnable() {
                    @Override
                    public void run() {
                        camera.addCallbackBuffer(bytes);
                        isProcessingFrame = false;
                    }
                };
        processImage();
    }

    /**
     * Callback for Camera2 API
     */
    @Override
    public void onImageAvailable(final ImageReader reader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }
        if (rgbBytes == null) {
            rgbBytes = new int[previewWidth * previewHeight];
        }
        try {
            final Image image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            if (isProcessingFrame) {
                image.close();
                return;
            }
            isProcessingFrame = true;
            Trace.beginSection("imageAvailable");
            final Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter =
                    new Runnable() {
                        @Override
                        public void run() {
                            ImageUtils.convertYUV420ToARGB8888(
                                    yuvBytes[0],
                                    yuvBytes[1],
                                    yuvBytes[2],
                                    previewWidth,
                                    previewHeight,
                                    yRowStride,
                                    uvRowStride,
                                    uvPixelStride,
                                    rgbBytes);
                        }
                    };

            postInferenceCallback =
                    new Runnable() {
                        @Override
                        public void run() {
                            image.close();
                            isProcessingFrame = false;
                        }
                    };

            processImage();
        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
            Trace.endSection();
            return;
        }
        Trace.endSection();
    }

    @Override
    public synchronized void onStart() {
        LOGGER.d("onStart " + this);
        super.onStart();
    }

    @Override
    public synchronized void onResume() {
        LOGGER.d("onResume " + this);
        super.onResume();

        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());



    }

    @Override
    public synchronized void onPause() {
        LOGGER.d("onPause " + this);

        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            LOGGER.e(e, "Exception!");
        }

        super.onPause();
    }

//    @Override
//    public synchronized void onStop() {
//        LOGGER.d("onStop " + this);
//        super.onStop();
//    }

//    @Override
//    public synchronized void onDestroy() {
//        LOGGER.d("onDestroy " + this);
//        super.onDestroy();
//    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Shutdown TextToSpeech when activity is destroyed
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }


    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language if needed
            int result = textToSpeech.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language not supported");
            }
        } else {
            Log.e("TTS", "Initialization failed");
        }
    }




    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode, final String[] permissions, final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST) {
            if (allPermissionsGranted(grantResults)) {
                setFragment();
            } else {
                requestPermission();
            }
        }
    }

    private static boolean allPermissionsGranted(final int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
                Toast.makeText(
                                CameraActivity.this,
                                "Camera permission is required for this demo",
                                Toast.LENGTH_LONG)
                        .show();
            }
            requestPermissions(new String[]{PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
        }
    }

    // Returns true if the device supports the required hardware level, or better.
    private boolean isHardwareLevelSupported(
            CameraCharacteristics characteristics, int requiredLevel) {
        int deviceLevel = 0;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        }
        if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            return requiredLevel == deviceLevel;
        }
        // deviceLevel is not LEGACY, can use numerical sort
        return requiredLevel <= deviceLevel;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private String chooseCamera() {
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                final StreamConfigurationMap map =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (map == null) {
                    continue;
                }

                // Fallback to camera1 API for internal cameras that don't have full support.
                // This should help with legacy situations where using the camera2 API causes
                // distorted or otherwise broken previews.
                useCamera2API =
                        (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                                || isHardwareLevelSupported(
                                characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
                LOGGER.i("Camera API lv2?: %s", useCamera2API);
                return cameraId;
            }
        } catch (CameraAccessException e) {
            LOGGER.e(e, "Not allowed to access camera");
        }

        return null;
    }

    protected void setFragment() {
        String cameraId = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            cameraId = chooseCamera();
        }

        Fragment fragment;
        if (useCamera2API) {
            CameraConnectionFragment camera2Fragment =
                    CameraConnectionFragment.newInstance(
                            new CameraConnectionFragment.ConnectionCallback() {
                                @Override
                                public void onPreviewSizeChosen(final Size size, final int rotation) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        previewHeight = size.getHeight();
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        previewWidth = size.getWidth();
                                    }
                                    CameraActivity.this.onPreviewSizeChosen(size, rotation);
                                }
                            },
                            this,
                            getLayoutId(),
                            getDesiredPreviewFrameSize());

            camera2Fragment.setCamera(cameraId);
            fragment = camera2Fragment;
        } else {
            fragment =
                    new LegacyCameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize());
        }

        getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
    }

    protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

   /* @UiThread
    protected void showResultsInBottomSheet(List<Recognition> results) {
        if (canDetectCurrency && results != null && !results.isEmpty()) {
            Recognition recognition = results.get(0);
            if (recognition != null) {
                if (recognition.getTitle() != null) {
                    String currency = recognition.getTitle();
                    recognitionTextView.setText(currency);
                    if (recognition.getConfidence() != null) {
                        recognitionValueTextView.setText(
                                String.format("%.2f", (100 * recognition.getConfidence())) + "%");
                    }

                    // Check if the detected currency has a corresponding audio file
                    if (currencyAudioMap.containsKey(currency)) {
                        MediaPlayer currencyAudio = currencyAudioMap.get(currency);
                        currencyAudio.start();

                        // Prevent further currency detection for 5 seconds
                        canDetectCurrency = false;
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                canDetectCurrency = true;
                            }
                        }, RESET_DELAY);
                    } else {
                        // If no corresponding audio file found, stop any currently playing audio
                        stopAllAudio();
                    }
                }
            }
        }
    }*/



//    @UiThread
//    protected void showResultsInBottomSheet(List<Recognition> results) {
//        if (canDetectCurrency && results != null && !results.isEmpty()) {
//            Recognition recognition = results.get(0);
//            if (recognition != null) {
//                if (recognition.getTitle() != null) {
//                    String currency = recognition.getTitle();
//                    recognitionTextView.setText(currency);
//                    if (recognition.getConfidence() != null) {
//                        recognitionValueTextView.setText(
//                                String.format("%.2f", (100 * recognition.getConfidence())) + "%");
//                    }
//
//                    // Speak the detected currency
//                    speakCurrency(currency);
//                }
//            }
//        }
//    }

//    @UiThread
//    protected void showResultsInBottomSheet(List<Recognition> results) {
//        if (canDetectCurrency && results != null && !results.isEmpty()) {
//            Recognition recognition = results.get(0);
//            if (recognition != null) {
//                String currency = recognition.getTitle();
//                if (currency != null && !currency.isEmpty()) { // Check if currency is not empty
//                    recognitionTextView.setText(currency);
//                    if (recognition.getConfidence() != null) {
//                        recognitionValueTextView.setText(
//                                String.format("%.2f", (100 * recognition.getConfidence())) + "%");
//                    }
//
//                    // Speak the detected currency
//                    speakCurrency(currency);
//                }
//            }
//        }
//    }



    @UiThread
    protected void showResultsInBottomSheet(List<Recognition> results) {
        if (canDetectCurrency && results != null && !results.isEmpty()) {
            Recognition recognition = results.get(0);
            if (recognition != null) {
                String currency = recognition.getTitle();
                if (isIndianCurrency(currency)) { // Check if recognized currency is Indian currency
                    recognitionTextView.setText(currency);
                    if (recognition.getConfidence() != null) {
                        recognitionValueTextView.setText(
                                String.format("%.2f", (100 * recognition.getConfidence())) + "%");
                    }

                    // Speak the detected currency
                    speakCurrency(currency);

                    // Prevent further currency detection until reset
                    canDetectCurrency = false;
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            canDetectCurrency = true;
                        }
                    }, RESET_DELAY);
                }
            }
        }
    }


    // Method to check if the recognized currency is Indian currency
    private boolean isIndianCurrency(String currency) {
        // Define Indian currency denominations
        String[] indianCurrencies = {"10", "20", "50", "100", "200", "500"};

        // Check if recognized currency is in the Indian currency list
        for (String indianCurrency : indianCurrencies) {
            if (currency != null && currency.equals(indianCurrency)) {
                return true;
            }
        }
        return false;
    }





    // Method to speak the detected currency
    private void speakCurrency(String currency) {
        if (textToSpeech != null && !textToSpeech.isSpeaking()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech.speak(currency, TextToSpeech.QUEUE_FLUSH, null, "currency");
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        // Stop speaking when activity is stopped
        if (textToSpeech != null && textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }
    }


//
//
//    // Method to stop all currently playing audio
//    private void stopAllAudio() {
//        for (MediaPlayer audio : currencyAudioMap.values()) {
//            if (audio.isPlaying()) {
//                audio.stop();
//            }
//        }
//    }

    protected void showFrameInfo(String frameInfo) {
        frameValueTextView.setText(frameInfo);
    }

    protected void showCropInfo(String cropInfo) {
        cropValueTextView.setText(cropInfo);
    }

    protected void showCameraResolution(String cameraInfo) {
        cameraResolutionTextView.setText(cameraInfo);
    }

    protected void showRotationInfo(String rotation) {
        rotationTextView.setText(rotation);
    }

    protected void showInference(String inferenceTime) {
        inferenceTimeTextView.setText(inferenceTime);
    }

    protected Classifier.Model getModel() {
        return model;
    }

    private void setModel(Model model) {
        if (this.model != model) {
            LOGGER.d("Updating  model: " + model);
            this.model = model;
            onInferenceConfigurationChanged();
        }
    }

    protected Classifier.Device getDevice() {
        return device;
    }

    private void setDevice(Device device) {
        if (this.device != device) {
            LOGGER.d("Updating  device: " + device);
            this.device = device;
            final boolean threadsEnabled = device == Device.CPU;
            plusImageView.setEnabled(threadsEnabled);
            minusImageView.setEnabled(threadsEnabled);
            threadsTextView.setText(threadsEnabled ? String.valueOf(numThreads) : "N/A");
            onInferenceConfigurationChanged();
        }
    }

    protected int getNumThreads() {
        return numThreads;
    }

    private void setNumThreads(int numThreads) {
        if (this.numThreads != numThreads) {
            LOGGER.d("Updating  numThreads: " + numThreads);
            this.numThreads = numThreads;
            onInferenceConfigurationChanged();
        }
    }

    protected abstract void processImage();

    protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

    protected abstract int getLayoutId();

    protected abstract Size getDesiredPreviewFrameSize();

    protected abstract void onInferenceConfigurationChanged();

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.plus) {
            String threads = threadsTextView.getText().toString().trim();
            int numThreads = Integer.parseInt(threads);
            if (numThreads >= 9) return;
            setNumThreads(++numThreads);
            threadsTextView.setText(String.valueOf(numThreads));
        } else if (v.getId() == R.id.minus) {
            String threads = threadsTextView.getText().toString().trim();
            int numThreads = Integer.parseInt(threads);
            if (numThreads == 1) {
                return;
            }
            setNumThreads(--numThreads);
            threadsTextView.setText(String.valueOf(numThreads));
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        if (parent == modelSpinner) {
            setModel(Model.valueOf(parent.getItemAtPosition(pos).toString().toUpperCase()));
        } else if (parent == deviceSpinner) {
            setDevice(Device.valueOf(parent.getItemAtPosition(pos).toString()));
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing.
    }
}
