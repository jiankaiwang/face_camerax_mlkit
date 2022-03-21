package com.example.faces;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.graphics.Rect;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceLandmark;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity
        implements View.OnClickListener, ImageAnalysis.Analyzer {

    Button btnTakePicture;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private LinearLayout linearLayout;
    private PreviewView previewView;

    private static final int CAMERA_REQUEST_CODE = 10;
    private static final int AUDIO_REQUEST_CODE = 10;
    private int LENS_SELECTOR = CameraSelector.LENS_FACING_FRONT;

    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private TextView resultView;

    private File imgDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Faces");

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_REQUEST_CODE);
    }

    private boolean hasStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            int REQUEST_CODE_PERMISSION_STORAGE = 100;
            String[] permissions = {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };

            for (String str : permissions) {
                if (this.checkSelfPermission(str) != PackageManager.PERMISSION_GRANTED) {
                    this.requestPermissions(permissions, REQUEST_CODE_PERMISSION_STORAGE);
                    return;
                }
            }
        }
    }

    private boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                AUDIO_REQUEST_CODE
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnTakePicture = findViewById(R.id.btnTakePicture);
        resultView = findViewById(R.id.resultView);
        previewView = findViewById(R.id.previewView);
        linearLayout = findViewById(R.id.linearLayout);

        if (!hasCameraPermission()) {
            requestPermission();
        }

        if (!hasStoragePermission()) {
            requestStoragePermission();
        }

        if (!hasAudioPermission()) {
            requestAudioPermission();
        }

        btnTakePicture.setOnClickListener(this);

        startProcessCamera();
    }

    @Override
    public void onClick(View view) {
        switch(view.getId()) {
            case R.id.btnTakePicture:
                createPicture();
                break;
        }
    }

    private void createPicture() {

        if(! imgDir.exists()) {
            imgDir.mkdir();
        }
        String timestamp = String.valueOf(new Date().getTime());
        File file = new File(imgDir, timestamp + ".jpg");

        imageCapture.takePicture(
                new ImageCapture.OutputFileOptions.Builder(file).build(),
                getExecutor(),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Toast.makeText(MainActivity.this, "The image has been saved successfully.", Toast.LENGTH_SHORT).show();
//                        Log.d("OnImageSaved", file.getAbsolutePath());
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(MainActivity.this, "Error on saving the image: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    protected void startProcessCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                startCameraX(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, getExecutor());
    }

    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    @SuppressLint("RestrictedApi")
    private void startCameraX(ProcessCameraProvider cameraProvider) {

        // camera selector use case
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(LENS_SELECTOR)
                .build();

        // preview use case
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // image capture use case
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        // image analysis use case
        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(getExecutor(), this);

        // make sure all occupying are unbinding
        cameraProvider.unbindAll();

        // bind all above to the life cycle
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture, imageAnalysis);
    }

    private void detectFaces(InputImage image, ImageProxy imageProxy) {
        // Real-time contour detection
        FaceDetectorOptions realTimeOpts =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                        .setMinFaceSize(0.15f)
                        .enableTracking()
                        .build();

        // get detector
        FaceDetector detector = FaceDetection.getClient(realTimeOpts);

        // run the detector
        Task<List<Face>> result =
                detector.process(image)
                        .addOnSuccessListener(
                                new OnSuccessListener<List<Face>>() {
                                    @Override
                                    public void onSuccess(List<Face> faces) {
                                        // Task completed successfully
                                        for (Face face : faces) {
                                            Rect bounds = face.getBoundingBox();
                                            resultView.setText("Face Detection" + "\n" +
                                                    "Top    : " + String.valueOf(bounds.top) + ", " +
                                                    "Left   : " + String.valueOf(bounds.left) + ", " +
                                                    "Bottom : " + String.valueOf(bounds.bottom) + ", " +
                                                    "Right  : " + String.valueOf(bounds.right) + "\n");
                                            float rotY = face.getHeadEulerAngleY();  // Head is rotated to the right rotY degrees
                                            float rotZ = face.getHeadEulerAngleZ();  // Head is tilted sideways rotZ degrees
                                            resultView.setText(resultView.getText() +
                                                    "rotY   : " + String.valueOf(rotY) + ", " + "rotZ   :" + String.valueOf(rotZ) + "\n");

                                            // If landmark detection was enabled (mouth, ears, eyes, cheeks, and
                                            // nose available):
                                            FaceLandmark leftEar = face.getLandmark(FaceLandmark.LEFT_EAR);
                                            if (leftEar != null) {
                                                PointF leftEarPos = leftEar.getPosition();
                                                resultView.setText(resultView.getText() +
                                                        "leftEarPos : " + String.valueOf(leftEarPos) + "\n");
                                            }

                                            // If classification was enabled:
                                            if (face.getSmilingProbability() != null) {
                                                float smileProb = face.getSmilingProbability();
                                                resultView.setText(resultView.getText() +
                                                        "smileProb : " + String.valueOf(smileProb) + "\n");
                                            }
                                            if (face.getRightEyeOpenProbability() != null) {
                                                float rightEyeOpenProb = face.getRightEyeOpenProbability();
                                                resultView.setText(resultView.getText() +
                                                        "rightEyeOpenProb : " + String.valueOf(rightEyeOpenProb) + "\n");
                                            }

                                            // If face tracking was enabled:
                                            if (face.getTrackingId() != null) {
                                                int id = face.getTrackingId();
                                                resultView.setText(resultView.getText() +
                                                        "Face Tracking ID : " + String.valueOf(id) + "\n");
                                            }

                                        }
                                        imageProxy.close();
                                    }
                                })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.d("OnFailureListener", "Failed in detecting the face ..." + e.getMessage());
                                        e.printStackTrace();
                                        imageProxy.close();
                                    }
                                });
    }

    @Override
    @SuppressLint("UnsafeOptInUsageError")
    public void analyze(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            // Pass image to an ML Kit Vision API
            detectFaces(image, imageProxy);
        }
    }
}