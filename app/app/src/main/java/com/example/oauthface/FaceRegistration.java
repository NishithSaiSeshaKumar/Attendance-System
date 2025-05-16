package com.example.oauthface;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.gson.Gson;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FaceRegistration extends AppCompatActivity {
    private ImageView faceImageView;
    private Button captureImageButton;
    private Button importImageButton;
    private Button saveFaceButton;
    private Button completeRegistrationButton;
    private Bitmap currentFaceBitmap;
    private Interpreter tfLite;
//    Binder
    public HashMap<String, float[][]> registeredFaces = new HashMap<>();
    private String userId;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_registration);
        SharedPreferences sharedPreferences = getSharedPreferences("UserData", MODE_PRIVATE);
        userId = sharedPreferences.getString("USER_ID", null);
        faceImageView = findViewById(R.id.faceImageView);
        captureImageButton = findViewById(R.id.captureImageButton);
        importImageButton = findViewById(R.id.importImageButton);
        saveFaceButton = findViewById(R.id.saveFaceButton);
        completeRegistrationButton = findViewById(R.id.completeRegistrationButton);

        captureImageButton.setOnClickListener(v -> captureImage());
        importImageButton.setOnClickListener(v -> importImage());
        saveFaceButton.setOnClickListener(v -> saveFace());
        completeRegistrationButton.setOnClickListener(v -> completeRegistration());

        try {
            tfLite = new Interpreter(loadModelFile(this, "mobile_face_net.tflite"));
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load model", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    public static MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(context.getAssets().openFd(modelPath).getFileDescriptor());
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = context.getAssets().openFd(modelPath).getStartOffset();
        long declaredLength = context.getAssets().openFd(modelPath).getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    private void captureImage() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, 1);
        }
    }

    private void importImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, 2);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == 1) {
                Bundle extras = data.getExtras();
                Bitmap bitmap = (Bitmap) extras.get("data");
                processImage(bitmap);
            } else if (requestCode == 2) {
                Uri imageUri = data.getData();
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                    processImage(bitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void processImage(Bitmap bitmap) {
        FaceDetect.detectFaces(bitmap, this, (faces, croppedFace) -> {
            if (faces.size() > 0) {
                currentFaceBitmap = croppedFace;
                faceImageView.setImageBitmap(currentFaceBitmap);
                saveFaceButton.setEnabled(true);
            } else {
                Toast.makeText(this, "No face detected", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void saveFace() {
        if (currentFaceBitmap != null) {
            float[][] embeddings = getFaceEmbeddings(currentFaceBitmap);
            if (embeddings != null) {
                registeredFaces.put(userId, embeddings);
                insertToSP(registeredFaces);
                Toast.makeText(this, "Face saved", Toast.LENGTH_SHORT).show();
                completeRegistrationButton.setEnabled(true);
            }
        }
    }

    private void completeRegistration() {
        if (userId != null) {
            DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("users").child(userId);
            databaseReference.child("hasregistered").setValue(true).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Registration completed successfully!", Toast.LENGTH_SHORT).show();
                    // Navigate to the next activity
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Failed to update registration status.", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Toast.makeText(this, "User ID is null. Cannot complete registration.", Toast.LENGTH_SHORT).show();
        }
    }
    private float[][] getFaceEmbeddings(Bitmap faceBitmap) {
        ByteBuffer imgData = ByteBuffer.allocateDirect(1 * 112 * 112 * 3 * 4);
        imgData.order(ByteOrder.nativeOrder());
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(faceBitmap, 112, 112, false);
        int[] intValues = new int[112 * 112];
        scaledBitmap.getPixels(intValues, 0, 112, 0, 0, 112, 112);
        imgData.rewind();
        for (int i = 0; i < 112; ++i) {
            for (int j = 0; j < 112; ++j) {
                int pixelValue = intValues[i * 112 + j];
                imgData.putFloat((((pixelValue >> 16) & 0xFF) - 128.0f) / 128.0f);
                imgData.putFloat((((pixelValue >> 8) & 0xFF) - 128.0f) / 128.0f);
                imgData.putFloat(((pixelValue & 0xFF) - 128.0f) / 128.0f);
            }
        }
        float[][] embeddings = new float[1][192];
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, embeddings);
        tfLite.runForMultipleInputsOutputs(new Object[]{imgData}, outputMap);
        return embeddings;
    }

    private void insertToSP(HashMap<String, float[][]> map) {
        String jsonString = new Gson().toJson(map);
        SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("map", jsonString);
        editor.apply();
    }
}
