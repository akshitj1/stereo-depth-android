package com.example.inferdepth;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.bumptech.glide.Glide;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ProfileDepthActivity extends AppCompatActivity {
    public static final String TAG = "ProfileDepthActivity";
    private final int OPEN_DIRECTORY_REQUEST_CODE = 0xf11e;
    private final Uri SINTEL_BASE_URI = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ASintelDataset");
    private ImageView disparityView;
    private DisparityDataset sintelDataset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        disparityView = findViewById(R.id.disparity_view);

        // ref: https://stackoverflow.com/questions/32431723/read-external-storage-permission-for-android
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            Log.d(TAG, "permission granted");
        else {
            Log.e(TAG, "unable to attain external storage permissions");
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, OPEN_DIRECTORY_REQUEST_CODE);
        }
        DocumentFile sintelBase = DocumentFile.fromTreeUri(this, SINTEL_BASE_URI);
        DocumentFile profileDatasetBase = sintelBase.findFile("profile_dataset");
        sintelDataset = new DisparityDataset(profileDatasetBase);

        Executor inferenceThread =  Executors.newSingleThreadExecutor();
        inferenceThread.execute(new Runnable() {
            @Override
            public void run() {
                profileDepthInference(sintelDataset);
            }
        });
    }

    private boolean isDirAccessible(Uri dirUri) {
        for (UriPermission perm : getContentResolver().getPersistedUriPermissions()) {
            if (perm.getUri().toString().equals(dirUri.toString())) {
                Log.d(TAG, "Directory accessible: " + dirUri.getPath());
                return true;
            }
        }
        Log.e(TAG, "Directory not accessible: " + dirUri.toString());
        return false;
    }

    private void openDirectory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, "content://com.android.externalstorage.documents/tree/primary%3ASintelDataset");
        startActivityForResult(intent, OPEN_DIRECTORY_REQUEST_CODE);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_DIRECTORY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Uri directoryUri = data.getData();

            getContentResolver().takePersistableUriPermission(
                    directoryUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
    }

    private void setImageView(ImageView imageView, Bitmap image) {
        Glide.with(getBaseContext())
                .load(image)
                .override(512, 512)
                .fitCenter()
                .into(imageView);
    }

    private void profileDepthInference(final DisparityDataset dataset) {
        try {
            DepthPredictor depthPredictor = new DepthPredictor(this, DepthPredictor.Device.GPU, 2);
            for(DatasetEntry entry: dataset.getEntries()){
                Bitmap disparityImage = depthPredictor.predictDepth(readImage(entry.leftImage), readImage(entry.rightImage));
                Log.i(TAG, "depth successfully predicted");
                runOnUiThread(() -> disparityView.setImageBitmap(disparityImage));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Bitmap readImage(DocumentFile imgPath) throws IOException {
        Bitmap img = ImageDecoder.decodeBitmap(ImageDecoder.createSource(this.getContentResolver(), imgPath.getUri()));
        // convert to format supported by TensorImage
        return img.copy(Bitmap.Config.ARGB_8888, false);
    }

}