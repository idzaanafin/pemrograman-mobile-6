package com.example.tugas6;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA = 10;

    private PreviewView cameraPreview;
    private ImageView photoResult;
    private VideoView videoResult;
    private Button photoModeButton;
    private Button videoModeButton;
    private Button captureButton;
    private Button retakeButton;
    private Button saveButton;
    private TextView modeLabel;
    private TextView statusLabel;
    private Chronometer videoTimer;

    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;
    private File pendingFile;
    private boolean videoMode;
    private boolean previewingResult;
    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraPreview = findViewById(R.id.camera_preview);
        photoResult = findViewById(R.id.photo_result);
        videoResult = findViewById(R.id.video_result);
        photoModeButton = findViewById(R.id.button_photo_mode);
        videoModeButton = findViewById(R.id.button_video_mode);
        captureButton = findViewById(R.id.button_capture);
        retakeButton = findViewById(R.id.button_retake);
        saveButton = findViewById(R.id.button_save);
        modeLabel = findViewById(R.id.mode_label);
        statusLabel = findViewById(R.id.status_label);
        videoTimer = findViewById(R.id.video_timer);
        cameraExecutor = Executors.newSingleThreadExecutor();

        photoModeButton.setOnClickListener(view -> setMode(false));
        videoModeButton.setOnClickListener(view -> setMode(true));
        captureButton.setOnClickListener(view -> capture());
        retakeButton.setOnClickListener(view -> retake());
        saveButton.setOnClickListener(view -> savePendingFile());
        setMode(false);

        if (hasRequiredPermissions()) {
            startCamera();
        } else {
            requestCameraPermissions();
        }
    }

    private boolean hasRequiredPermissions() {
        boolean cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        boolean audioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        boolean storageGranted = android.os.Build.VERSION.SDK_INT >= 29
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
        return cameraGranted && audioGranted && storageGranted;
    }

    private void requestCameraPermissions() {
        if (android.os.Build.VERSION.SDK_INT < 29) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    REQUEST_CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> providerFuture =
                ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();
                Preview preview = new Preview.Builder().build();
                imageCapture = new ImageCapture.Builder().build();
                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
                bindUseCases(provider, preview);
            } catch (Exception exception) {
                statusLabel.setText(R.string.camera_start_failed);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindUseCases(ProcessCameraProvider provider, Preview preview) {
        provider.unbindAll();
        provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview,
                imageCapture, videoCapture);
    }

    private void setMode(boolean useVideo) {
        if (activeRecording != null) {
            return;
        }
        videoMode = useVideo;
        photoModeButton.setSelected(!useVideo);
        videoModeButton.setSelected(useVideo);
        modeLabel.setText(useVideo ? R.string.video_mode : R.string.photo_mode);
        captureButton.setText(useVideo ? R.string.start_recording : R.string.take_photo);
        statusLabel.setText(useVideo ? R.string.video_ready : R.string.photo_ready);
    }

    private void capture() {
        if (previewingResult || imageCapture == null || videoCapture == null) {
            return;
        }
        if (videoMode) {
            startRecording();
        } else {
            takePhoto();
        }
    }

    private void takePhoto() {
        pendingFile = createTempFile(".jpg");
        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(pendingFile).build();
        imageCapture.takePicture(options, cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        runOnUiThread(() -> showResult(R.string.photo_review));
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        runOnUiThread(() -> showError(R.string.capture_failed));
                    }
                });
    }

    private void startRecording() {
        pendingFile = createTempFile(".mp4");
        FileOutputOptions outputOptions = new FileOutputOptions.Builder(pendingFile).build();
        activeRecording = videoCapture.getOutput().prepareRecording(this, outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(this), event -> {
                    if (event instanceof VideoRecordEvent.Start) {
                        videoTimer.setBase(SystemClock.elapsedRealtime());
                        videoTimer.setVisibility(View.VISIBLE);
                        videoTimer.start();
                        captureButton.setText(R.string.stop_recording);
                        statusLabel.setText(R.string.recording);
                    } else if (event instanceof VideoRecordEvent.Finalize) {
                        activeRecording = null;
                        videoTimer.stop();
                        videoTimer.setVisibility(View.GONE);
                        if (((VideoRecordEvent.Finalize) event).hasError()) {
                            showError(R.string.capture_failed);
                        } else {
                            showResult(R.string.video_review);
                        }
                    }
                });
        captureButton.setOnClickListener(view -> stopRecording());
    }

    private void stopRecording() {
        if (activeRecording != null) {
            activeRecording.stop();
            captureButton.setOnClickListener(view -> capture());
        }
    }

    private void showResult(int message) {
        previewingResult = true;
        cameraPreview.setVisibility(View.GONE);
        if (videoMode) {
            videoResult.setVisibility(View.VISIBLE);
            videoResult.setVideoURI(Uri.fromFile(pendingFile));
            videoResult.start();
        } else {
            photoResult.setVisibility(View.VISIBLE);
            photoResult.setImageBitmap(BitmapFactory.decodeFile(pendingFile.getAbsolutePath()));
        }
        captureButton.setVisibility(View.GONE);
        photoModeButton.setVisibility(View.GONE);
        videoModeButton.setVisibility(View.GONE);
        retakeButton.setVisibility(View.VISIBLE);
        saveButton.setVisibility(View.VISIBLE);
        statusLabel.setText(message);
    }

    private void retake() {
        deletePendingFile();
        previewingResult = false;
        cameraPreview.setVisibility(View.VISIBLE);
        photoResult.setVisibility(View.GONE);
        videoResult.stopPlayback();
        videoResult.setVisibility(View.GONE);
        captureButton.setVisibility(View.VISIBLE);
        photoModeButton.setVisibility(View.VISIBLE);
        videoModeButton.setVisibility(View.VISIBLE);
        retakeButton.setVisibility(View.GONE);
        saveButton.setVisibility(View.GONE);
        captureButton.setOnClickListener(view -> capture());
        setMode(videoMode);
    }

    private void savePendingFile() {
        if (pendingFile == null || !pendingFile.exists()) {
            showError(R.string.capture_failed);
            return;
        }
        String extension = videoMode ? ".mp4" : ".jpg";
        String name = "TUGAS6_" + new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.US).format(new Date()) + extension;
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, videoMode ? "video/mp4" : "image/jpeg");
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DCIM + File.separator + "tugas_6");
        } else {
            File directory = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    "tugas_6");
            if (!directory.exists() && !directory.mkdirs()) {
                showError(R.string.save_failed);
                return;
            }
            values.put(MediaStore.MediaColumns.DATA,
                    new File(directory, name).getAbsolutePath());
        }
        try {
            android.net.Uri uri = getContentResolver().insert(
                    videoMode ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IllegalStateException("MediaStore returned null URI");
            }
            try (java.io.OutputStream output = getContentResolver().openOutputStream(uri);
                 java.io.InputStream input = new java.io.FileInputStream(pendingFile)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }
            deletePendingFile();
            previewingResult = false;
            statusLabel.setText(getString(R.string.saved_to_folder));
            retake();
            Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            showError(R.string.save_failed);
        }
    }

    private File createTempFile(String suffix) {
        return new File(getCacheDir(), "capture_" + System.currentTimeMillis() + suffix);
    }

    private void deletePendingFile() {
        if (pendingFile != null) {
            pendingFile.delete();
            pendingFile = null;
        }
    }

    private void showError(int message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        deletePendingFile();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA && grantResults.length >= 2
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED
                && (android.os.Build.VERSION.SDK_INT >= 29 || grantResults[2]
                == PackageManager.PERMISSION_GRANTED)) {
            startCamera();
        } else {
            statusLabel.setText(R.string.camera_permission_required);
        }
    }

    @Override
    protected void onDestroy() {
        if (activeRecording != null) {
            activeRecording.stop();
        }
        cameraExecutor.shutdown();
        super.onDestroy();
    }
}
