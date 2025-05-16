package com.example.oauthface;

import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.concurrent.ExecutionException;

public class AuthManager {
    public static final int RC_SIGN_IN = 123;
    private FirebaseAuth auth;
    private GoogleSignInClient googleSignInClient;
    private AppCompatActivity activity;
    private FirebaseUser user;
    private DatabaseReference mDatabase;

    private String userId;
    private boolean hasRegistered;
    private DataSnapshot dataSnapshot;

    private String userEmail;
    public AuthManager(AppCompatActivity activity) {
        this.activity = activity;
        initializeFirebaseAuth();
    }

    private void initializeFirebaseAuth() {
        auth = FirebaseAuth.getInstance();
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(activity.getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(activity, options);
    }

    public void signIn() {
        Intent intent = googleSignInClient.getSignInIntent();
        activity.startActivityForResult(intent, RC_SIGN_IN);
    }

    public void handleSignInResult(Intent data) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount signInAccount = task.getResult(ApiException.class);
            AuthCredential authCredential = GoogleAuthProvider.getCredential(signInAccount.getIdToken(), null);
            auth.signInWithCredential(authCredential).addOnCompleteListener(activity, new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if (task.isSuccessful()) {
                        user = auth.getCurrentUser();
//                        Toast.makeText(activity, "Signed in successfully!", Toast.LENGTH_SHORT).show();
                        // Callback or event to notify the activity of successful sign-in
                    } else {
                        Toast.makeText(activity, "Failed to sign in: " + task.getException(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (ApiException e) {
            Log.e("AuthManager", "Sign-in failed", e);
//            Toast.makeText(activity, "Failed to sign in", Toast.LENGTH_SHORT).show();
        }
    }
    public void signOut() {
        auth.signOut();
        googleSignInClient.signOut().addOnCompleteListener(activity, new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                Toast.makeText(activity, "Signed out successfully", Toast.LENGTH_SHORT).show();
                // Callback or event to notify the activity of successful sign-out
            }
        });
    }
//    handle sign out

    public boolean isUserSignedIn() {
        return auth.getCurrentUser() != null;
    }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }
//    In RealTimeDatabase
public void verifyUserAuthorization(OnAuthorizationCompleteListener listener) {
    FirebaseUser user = getCurrentUser();
    if (user == null) {
        listener.onComplete(false, null, false);
        return;
    }
    String userEmail = user.getEmail();
    if (userEmail == null) {
        Toast.makeText(activity, "User email is null.", Toast.LENGTH_SHORT).show();
        listener.onComplete(false, null, false);
        return;
    }

    mDatabase = FirebaseDatabase.getInstance().getReference("users");
    mDatabase.orderByChild("email").equalTo(userEmail).addListenerForSingleValueEvent(new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
            if (dataSnapshot.exists()) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String userId = snapshot.child("ID").getValue(String.class);
                    boolean hasRegistered = snapshot.child("hasregistered").getValue(Boolean.class);
                    listener.onComplete(true, userId, hasRegistered);
                    return;
                }
            } else {
                // User does not exist in the database
                Toast.makeText(activity, "User not authorized.", Toast.LENGTH_SHORT).show();
                listener.onComplete(false, null, false);
            }
        }

        @Override
        public void onCancelled(@NonNull DatabaseError error) {
            Toast.makeText(activity, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            listener.onComplete(false, null, false);
        }
    });
}
    // Callback interface for authorization
    public interface OnAuthorizationCompleteListener {
        void onComplete(boolean isAuthorized, String userId, boolean hasRegistered);
    }
    public String getUserId() {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            return null; // User is not signed in
        }
        String userEmail = user.getEmail();
        if (userEmail == null) {
            return null; // User email is null
        }
        mDatabase = FirebaseDatabase.getInstance().getReference("users");
        try {
            DataSnapshot dataSnapshot = Tasks.await(mDatabase.orderByChild("email").equalTo(userEmail).get());
            if (dataSnapshot.exists()) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    return snapshot.child("ID").getValue(String.class); // Return the user's ID
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            Toast.makeText(activity, "Database error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return null; // User ID not found
    }

    public boolean getHasRegistered() {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            return false; // User is not signed in
        }
        String userEmail = user.getEmail();
        if (userEmail == null) {
            return false; // User email is null
        }
        mDatabase = FirebaseDatabase.getInstance().getReference("users");
        try {
            DataSnapshot dataSnapshot = Tasks.await(mDatabase.orderByChild("email").equalTo(userEmail).get());
            if (dataSnapshot.exists()) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    return Boolean.TRUE.equals(snapshot.child("hasregistered").getValue(Boolean.class)); // Return hasRegistered status
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            Toast.makeText(activity, "Database error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return false; // hasRegistered not found
    }}
