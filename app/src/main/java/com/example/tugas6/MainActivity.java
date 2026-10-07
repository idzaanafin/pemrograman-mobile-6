package com.example.tugas6;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private ImageView photoPreview;
    private TextView photoStatus;
    private Uri pendingPhotoUri;
    private String pendingPhotoPath;

    private final ActivityResultLauncher<String> storagePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    launchCamera();
                } else {
                    Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK) {
                            deletePendingPhoto();
                            photoStatus.setText(R.string.photo_cancelled);
                            return;
                        }

                        if (pendingPhotoUri != null) {
                            if (android.os.Build.VERSION.SDK_INT >= 29) {
                                ContentValues values = new ContentValues();
                                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                                getContentResolver().update(pendingPhotoUri, values, null, null);
                            }
                            photoPreview.setImageURI(pendingPhotoUri);
                            photoStatus.setText(getString(R.string.photo_saved, displayPath()));
                            Toast.makeText(this, R.string.photo_loaded, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, R.string.photo_not_available, Toast.LENGTH_SHORT).show();
                        }
                        pendingPhotoUri = null;
                        pendingPhotoPath = null;
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        photoPreview = findViewById(R.id.image_preview);
        photoStatus = findViewById(R.id.photo_status);
        Button takePhotoButton = findViewById(R.id.button_take_photo);
        takePhotoButton.setOnClickListener(view -> openCamera());
    }

    private void openCamera() {
        if (android.os.Build.VERSION.SDK_INT < 29
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        launchCamera();
    }

    private void launchCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, R.string.camera_not_available, Toast.LENGTH_SHORT).show();
            return;
        }
        pendingPhotoUri = createPhotoUri();
        if (pendingPhotoUri == null) {
            Toast.makeText(this, R.string.photo_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhotoUri);
        cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        cameraLauncher.launch(cameraIntent);
    }

    private Uri createPhotoUri() {
        String fileName = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_DCIM + File.separator + "tugas_6");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        } else {
            File directory = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    "tugas_6");
            if (!directory.exists() && !directory.mkdirs()) {
                return null;
            }
            pendingPhotoPath = new File(directory, fileName).getAbsolutePath();
            values.put(MediaStore.Images.Media.DATA, pendingPhotoPath);
        }

        return getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    private String displayPath() {
        return "DCIM/tugas_6/" + (pendingPhotoPath == null ? "foto tersimpan" :
                new File(pendingPhotoPath).getName());
    }

    private void deletePendingPhoto() {
        if (pendingPhotoUri != null) {
            getContentResolver().delete(pendingPhotoUri, null, null);
        }
        pendingPhotoUri = null;
        pendingPhotoPath = null;
    }
}
