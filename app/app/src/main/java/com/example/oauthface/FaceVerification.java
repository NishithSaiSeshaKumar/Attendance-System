package com.example.oauthface;


import static com.example.oauthface.FaceRegistration.loadModelFile;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Firebase;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import android.content.SharedPreferences;

import org.tensorflow.lite.Interpreter;

public class FaceVerification extends AppCompatActivity {
    private ImageView faceImageView;
    private Button captureImageButton;
    private Button verifyFaceButton;
    private Button completeVerificationButton;
    private TextView resultTextView;
    private Bitmap currentFaceBitmap;
    private Interpreter tfLite;
    private SharedPreferences sharedPreferences;
    private String longitude, latitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_verification);

        faceImageView = findViewById(R.id.faceImageView);
        captureImageButton = findViewById(R.id.captureImageButton);
        verifyFaceButton = findViewById(R.id.verifyFaceButton);
        completeVerificationButton = findViewById(R.id.completeVerificationButton);
        resultTextView = findViewById(R.id.resultTextView);

        captureImageButton.setOnClickListener(v -> captureImage());
        verifyFaceButton.setOnClickListener(v -> verifyFace());
        completeVerificationButton.setOnClickListener(v -> completeVerification());
        sharedPreferences = getSharedPreferences("UserData", MODE_PRIVATE);

        try {
            tfLite = new Interpreter(loadModelFile(this, "mobile_face_net.tflite"));
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load model", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void captureImage() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, 1);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == 1) {
                Bundle extras = data.getExtras();
                Bitmap bitmap = (Bitmap) extras.get("data");
                processImage(bitmap);
            }
        }
        else {
            Toast.makeText(this, "Failed to capture image Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    private void processImage(Bitmap bitmap) {
        FaceDetect.detectFaces(bitmap, this, (faces, croppedFace) -> {
            if (faces.size() > 0) {
                currentFaceBitmap = croppedFace;
                faceImageView.setVisibility(View.VISIBLE);
                faceImageView.setImageBitmap(currentFaceBitmap);
                verifyFaceButton.setEnabled(true);
            } else {
                Toast.makeText(this, "No face detected", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void verifyFace() {
        if (currentFaceBitmap != null) {
            float[][] embeddings = getFaceEmbeddings(currentFaceBitmap);
            if (embeddings != null) {
                HashMap<String, float[][]> registeredFaces = readFromSP();
                String matchedName = findNearest(embeddings[0], registeredFaces);
                if (matchedName != null) {
                    resultTextView.setText("Verified: " + matchedName);
//                    ((MainActivity) MainActivity.context).incrementVerificationCount();
                    completeVerificationButton.setEnabled(true);
                    completeVerificationButton.setVisibility(View.VISIBLE);

                } else {
                    resultTextView.setText("Not Verified");
                    completeVerificationButton.setEnabled(false);
                    completeVerificationButton.setVisibility(View.GONE);
                }
            }
        }
    }

    private void completeVerification() {
        longitude = sharedPreferences.getString("LONGITUDE","Recheck GPS");  // Default is 0.0f if not found
        latitude = sharedPreferences.getString("LATITUDE", " ");    // Default is 0.0f if not found
        if (longitude.equals("Recheck GPS") || latitude.equals(" ") ){
            Toast.makeText(this, "Please recheck GPS", Toast.LENGTH_SHORT).show();
            return;
        }else{
            postattendance();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        }

    }
    private void postattendance(){
        String userId = sharedPreferences.getString("USER_ID", "");
        String isoTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"));
//        String longitude = sharedPreferences.getString("LONGITUDE","Recheck GPS");  // Default is 0.0f if not found
//        String latitude = sharedPreferences.getString("latitude", " ");    // Default is 0.0f if not found
        Attendance attendance = new Attendance(longitude, latitude, userId, isoTimestamp);
        // Get Firebase database reference
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference attendanceRef = database.getReference("attendance");
        attendanceRef.push().setValue(attendance).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    Toast.makeText(FaceVerification.this, "Attendance Submitted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(FaceVerification.this, "Failed to Submit Attendance", Toast.LENGTH_SHORT).show();
                }
            }
        });
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

    private HashMap<String, float[][]> readFromSP() {
        SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
        String defValue = new Gson().toJson(new HashMap<String, float[][]>());
        String json = sharedPreferences.getString("map", defValue);
        return new Gson().fromJson(json, new TypeToken<HashMap<String, float[][]>>() {}.getType());
    }

    private String findNearest(float[] embeddings, HashMap<String, float[][]> registeredFaces) {
        String matchedName = null;
        float minDistance = Float.MAX_VALUE;
        for (Map.Entry<String, float[][]> entry : registeredFaces.entrySet()) {
            float distance = calculateDistance(embeddings, entry.getValue()[0]);
            if (distance < minDistance) {
                minDistance = distance;
                matchedName = entry.getKey();
            }
        }
        return minDistance < 1.0f ? matchedName : null;
    }

    private float calculateDistance(float[] emb1, float[] emb2) {
        float distance = 0;
        for (int i = 0; i < emb1.length; i++) {
            float diff = emb1[i] - emb2[i];
            distance += diff * diff;
        }
        return (float) Math.sqrt(distance);
    }
}
