package com.camerax.opencv;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * author: Denny.CM
 * date:   2021/10/14
 */
public class MainActivity extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2 {
    private final String TAG = "CameraXOpenCV";

    private ImageCapture imageCapture = null;
    public static File outputDirectory;
    private ExecutorService cameraExecutor;
    private Camera cameraBack;
    private CameraControl cameraBackControl = null;
    public static long photoTime = 0;

    private Preview mPreview = null;
    private TextView camera_blur = null;

    public static final int MESSAGE_UPDATE_UI = 1;
    private ExecutorService edgeCalcService;
    private EdgeCalculation edgeCalculation;

    private boolean divideImage = false;

    private final BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                Log.i(TAG, "OpenCV loaded successfully");
            } else {
                super.onManagerConnected(status);
            }
        }
    };

    //===============
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Context mContext = this.getApplicationContext();

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, Configuration.REQUIRED_PERMISSIONS,
                    Configuration.REQUEST_CODE_PERMISSIONS);
        }

        Button camera_capture_button = findViewById(R.id.camera_capture_button);
        camera_capture_button.setOnClickListener(view -> takePhoto());

        Button camera_capture_raw_button = findViewById(R.id.camera_read_image_button);
        camera_capture_raw_button.setOnClickListener(view -> ReadPhotos());

        camera_blur = findViewById(R.id.camera_blur);

        SwitchMaterial split_switch = findViewById(R.id.split_switch);
        split_switch.setOnCheckedChangeListener((compoundButton, isChecked) -> divideImage = isChecked);

        outputDirectory = getOutputDirectory();
        cameraExecutor = Executors.newSingleThreadExecutor();
        edgeCalcService = Executors.newFixedThreadPool(1);
        edgeCalculation = new EdgeCalculation(mContext, UpdateUIHandler);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.i(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.i(TAG, "onCameraViewStarted W/H: " + width + "/" + height);
    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame cvCameraViewFrame) {
        //===============
        Mat rgba = cvCameraViewFrame.rgba();
        Size sizeRgba = rgba.size();
        Log.i(TAG, "sizeRgba W/H: " + sizeRgba.width + "/" + sizeRgba.height);
        return null;
    }

    static class Configuration {
        public static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
        public static final int REQUEST_CODE_PERMISSIONS = 10;
        public static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Configuration.REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : Configuration.REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private File getOutputDirectory() {
        File mediaDir = new File(getExternalMediaDirs()[0], getString(R.string.app_name));
        boolean isExist = mediaDir.exists() || mediaDir.mkdir();
        return isExist ? mediaDir : null;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider processCameraProvider = cameraProviderFuture.get();
                PreviewView viewFinder = findViewById(R.id.viewFinder);

                mPreview = new Preview.Builder().build();
                //It seems not work to set preview size?!
                mPreview.setSurfaceProvider(viewFinder.getSurfaceProvider());
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                imageCapture = new ImageCapture.Builder().build();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().build();
                imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalyzer());
                processCameraProvider.unbindAll();
                cameraBack = processCameraProvider.bindToLifecycle(
                        MainActivity.this, cameraSelector, mPreview,
                        imageCapture, imageAnalysis);
                cameraBackControl = cameraBack.getCameraControl();

                viewFinder.setOnTouchListener((view, motionEvent) -> {
                    switch (motionEvent.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            MeteringPointFactory factory = viewFinder.getMeteringPointFactory();
                            MeteringPoint point = factory.createPoint(motionEvent.getX(), motionEvent.getY());
                            FocusMeteringAction action = new FocusMeteringAction.Builder(point).build();
                            cameraBackControl.startFocusAndMetering(action);
                            break;
                        case MotionEvent.ACTION_UP:
                            view.performClick();
                            break;
                        default:
                            break;
                    }
                    return true;
                });

            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "Use case binding failed: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture != null) {
            photoTime = System.currentTimeMillis();
            File photoFile = new File(outputDirectory,
                    new SimpleDateFormat(Configuration.FILENAME_FORMAT, Locale.US)
                            .format(photoTime) + ".jpg");

            ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions
                    .Builder(photoFile)
                    .build();

            imageCapture.takePicture(outputFileOptions,
                    ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults
                                                         outputFileResults) {
                            //Uri savedUri = Uri.fromFile(photoFile);
                            String msg = "Photo capture succeeded: " + photoFile.getAbsolutePath();
                            Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
                            Log.i(TAG, msg);
                            isBlurredImage(photoFile, true);
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            Log.e(TAG, "Photo capture failed: " + exception.getMessage());
                        }
            });
        }
    }

    private void ReadPhotos() {
        String photoDir = outputDirectory.getAbsolutePath() + "/photos";
        File photoDirectory = new File(photoDir);
        File[] photoFiles = photoDirectory.listFiles();
        if(photoFiles != null && photoFiles.length > 0) {
            for (File photoFile : photoFiles)
            {
                if(photoFile.exists()) {
                    Log.i(TAG, "photoFile:" + photoFile.getName());
                    isBlurredImage(photoFile, false);
                }
            }
        }
    }

    private static class ImageAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy image) {
        }
    }

    private synchronized void isBlurredImage(File blurDetectFile, boolean isCapturedImage) {
        Mat matPhoto = Imgcodecs.imread(blurDetectFile.getAbsolutePath(),
                Imgcodecs.IMREAD_COLOR);

        edgeCalcService.submit(() -> edgeCalculation.calculateCoffVar(
                blurDetectFile,
                matPhoto, isCapturedImage, divideImage));
    }

    private final Handler UpdateUIHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what == MESSAGE_UPDATE_UI) {
                String strBlur = (String) msg.obj;
                camera_blur.setText(strBlur);
            }
        }
    };
}