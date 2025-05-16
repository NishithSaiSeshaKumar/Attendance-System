package com.example.oauthface;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;


import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

// Suggested App Names : "AttendEase ","VerifyMe ",
public class MainActivity extends AppCompatActivity {
    private TextView registrationStatusTextView,Welcome;
//    private TextView verificationCountTextView;
    private Button registrationButton;
    private Button verificationButton;
    private  Button SignIN,SignOUT;
    private int verificationCount = 0;
    private static final int REQUEST_PERMISSIONS = 1;

    // Oauth
    private AuthManager authManager;
    private FirebaseUser user;
//    user = FirebaseAuth.getInstance().getCurrentUser();

    private void requestPermissions() {
        String[] permissions = {
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
    }

    private void updateUI() {
        if (authManager.isUserSignedIn()) {
            authManager.verifyUserAuthorization(new AuthManager.OnAuthorizationCompleteListener() {
                @Override
                public void onComplete(boolean isAuthorized, String userId, boolean hasRegistered) {
                    runOnUiThread(() -> {
                        if (isAuthorized) {
//                            Toast.makeText(MainActivity.this, "Signed in successfully!", Toast.LENGTH_SHORT).show();
                            Welcome.setText("Welcome " + userId + "!");
                            SharedPreferences sharedPreferences = getSharedPreferences("UserData", MODE_PRIVATE);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("USER_ID", userId);
                            editor.apply();
                            SignIN.setVisibility(View.INVISIBLE);

                            HashMap<String, float[][]> registeredFaces = readFromSP();
                            boolean hasEmbeddings = !registeredFaces.isEmpty();


                            if (hasRegistered && hasEmbeddings) {
                                registrationButton.setVisibility(View.INVISIBLE);
//                                Toast.makeText(MainActivity.this, "Already Registered", Toast.LENGTH_SHORT).show();
                                verificationButton.setVisibility(View.VISIBLE);
                            } else {
                                registrationButton.setVisibility(View.VISIBLE);
                                verificationButton.setVisibility(View.VISIBLE);
                            }
                        } else {
                            Welcome.setText("Unauthorized User\nPlease Contact Admin");
                            Toast.makeText(MainActivity.this, "Unauthorized user. Contact Admin!", Toast.LENGTH_SHORT).show();
                            authManager.signOut();
                            SignIN.setVisibility(View.VISIBLE);
                            SignOUT.setVisibility(View.INVISIBLE);
                            registrationButton.setVisibility(View.INVISIBLE);
                            verificationButton.setVisibility(View.INVISIBLE);
                        }
                    });
                }
            });
        } else {
            Welcome.setText("Sign in to continue");
            SignIN.setVisibility(View.VISIBLE);
            SignOUT.setVisibility(View.INVISIBLE);
            registrationButton.setVisibility(View.INVISIBLE);
            verificationButton.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//-----------OAUTH------------
        authManager = new AuthManager(this);
        Welcome = findViewById(R.id.Welcome);
        SignIN = findViewById(R.id.SignIN);
        SignIN.setOnClickListener(view -> {
            authManager.signIn();
                updateUI();
        });
        SignOUT = findViewById(R.id.SignOUT);
        SignOUT.setOnClickListener(view -> {
            authManager.signOut();
        // Clear face registration data too
            SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.clear();
            editor.apply();
            updateUI();
        });

        // Face Registration and Verification
        registrationStatusTextView = findViewById(R.id.registrationStatusTextView);
        // verificationCountTextView = findViewById(R.id.verificationCountTextView);
        registrationButton = findViewById(R.id.registrationButton);
        verificationButton = findViewById(R.id.verificationButton);

        updateRegistrationStatus();

        registrationButton.setOnClickListener(v -> {
            requestPermissions();
            Intent intent = new Intent(MainActivity.this, FaceRegistration.class);
            startActivity(intent);
        });

        verificationButton.setOnClickListener(v -> {
            requestPermissions();
            // sleep 5 seconds
//            try {
//                Thread.sleep(5000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            Intent intent = new Intent(MainActivity.this, GpsVerify.class);
            startActivity(intent);
        });
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
//        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
//        int count = sharedPreferences.getInt("verificationCount", 0);
//        verificationCountTextView.setText("Verification Count: " + count);
        updateUI();
        updateRegistrationStatus();
    }

    private void updateRegistrationStatus() {
        HashMap<String, float[][]> registeredFaces = readFromSP();
        if (registeredFaces.isEmpty()) {
            registrationStatusTextView.setText("Registration Status: Not Registered");
        } else {
            registrationStatusTextView.setText("Registration Status: Registered");
        }
    }

    private HashMap<String, float[][]> readFromSP() {
        SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
        String defValue = new Gson().toJson(new HashMap<String, float[][]>());
        String json = sharedPreferences.getString("map", defValue);
        return new Gson().fromJson(json, new TypeToken<HashMap<String, float[][]>>() {}.getType());
    }

    public void incrementVerificationCount() {
        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        int count = sharedPreferences.getInt("verificationCount", 0);
        count++;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("verificationCount", count);
        editor.apply();
//        verificationCountTextView.setText("Verification Count: " + count);
    }
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AuthManager.RC_SIGN_IN) {
            authManager.handleSignInResult(data);
            updateUI();
        }
    }

}
