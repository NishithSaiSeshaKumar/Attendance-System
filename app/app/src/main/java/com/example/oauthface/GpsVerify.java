package com.example.oauthface;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

public class GpsVerify extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private TextView latitudeTextView, longitudeTextView, resultTextView;
    private GeofenceManager geofenceManager;
    private Button locateMeButton,completeGPSVerificationButton;
    private Spinner selectloc;
    /* College */
    private double[] collegex = {80.5475027, 80.5473042, 80.5471862, 80.5478407, 80.5483074, 80.5488384, 80.5492408, 80.54945, 80.5498952, 80.5503137, 80.5506355, 80.5508716, 80.5511237, 80.5513275, 80.5515099, 80.5520571, 80.5512846, 80.5510271, 80.5508179, 80.5506892, 80.5503083, 80.5501688, 80.5498684, 80.5488921, 80.5486668, 80.5488116, 80.5487633, 80.5483932, 80.5481786, 80.5480981, 80.5478031, 80.547669};
    private double[] collegey = {16.233334, 16.2328498, 16.2326283, 16.2323193, 16.2320669, 16.2318403, 16.2320309, 16.2323193, 16.2320721, 16.2318918, 16.2317682, 16.2320463, 16.2324326, 16.2327262, 16.2329992, 16.2336997, 16.2342147, 16.2340035, 16.2339057, 16.2337615, 16.2331795, 16.232958, 16.2325923, 16.2328498, 16.2330249, 16.2332928, 16.233576, 16.2335966, 16.2333855, 16.2331949, 16.233334, 16.2334679};

    /* home */
    private double[] homex = {80.6568546,80.6568412,80.6571966,80.6572073,80.6568546}; // First and last points are the same
    private double[] homey = {16.2320746,16.2317437,16.2317501,16.2320708,16.2320746}; // First and last points are the same

    private double[] x,y;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gps_verify);

        // Initialize views
        latitudeTextView = findViewById(R.id.latitudeTV);
        longitudeTextView = findViewById(R.id.longitudeTV);
        resultTextView = findViewById(R.id.statusTV);
        locateMeButton = findViewById(R.id.Locate);
        completeGPSVerificationButton = findViewById(R.id.CompleteGPS);
        selectloc = findViewById(R.id.selectloc);

        // Initialize GeofenceManager
        geofenceManager = new GeofenceManager(this);

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.spinner_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        selectloc.setAdapter(adapter);
        selectloc.setSelection(0);
        // Set a listener to handle selection changes
        selectloc.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                                              @Override
          public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
              // Handle the spinner item selection
              if (position == 1) { // Home selected
                    x = homex;
                    y = homey;
              } else if (position == 2) { // College selected
                    x=collegex;
                    y=collegey;
              }
          }
            public void onNothingSelected(AdapterView<?> parentView) {
                // Handle when nothing is selected (optional)
// keep option 0 and make toast reminding to select
                Toast.makeText(GpsVerify.this, "Please select a locality", Toast.LENGTH_SHORT).show();
            }

        });


                                              // Request location permissions
        requestLocationPermissions();



        // Handle "Locate Me" button click

        locateMeButton.setOnClickListener(view -> handleLocateMeButtonClick(x,y));

        // Handle "Complete GPS Verification" button click
        completeGPSVerificationButton.setOnClickListener(view -> handleCompleteGPSVerificationButtonClick());
    }

    // Request location permissions
    private void requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            startLocationUpdates();
        }
    }

    // Handle permission request result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Location permission is required.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Start location updates
    private void startLocationUpdates() {
        locationRequest = new LocationRequest()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5000); // Update every 5 seconds

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null) {
                    Location location = locationResult.getLastLocation();
                    if (location != null) {
                        double latitude = location.getLatitude();
                        double longitude = location.getLongitude();
                        // Update latitude and longitude TextViews
                        latitudeTextView.setText("Latitude: " + latitude);
                        longitudeTextView.setText("Longitude: " + longitude);
                    }
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    // Handle "Locate Me" button click
    private void handleLocateMeButtonClick(double[] x,double[] y) {
        if (x == null || y == null || x.length == 0 || y.length == 0) {
            Toast.makeText(this, "Please select a locality from the dropdown.", Toast.LENGTH_SHORT).show();
            return;
        }
        // Check if location permissions are granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
//        check if GPS is enabled or else prompt to enabled
        if (!geofenceManager.isGPSEnabled()) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();
                latitudeTextView.setText("Latitude: " + latitude);
                longitudeTextView.setText("Longitude: " + longitude);

                // Verify if the location is within the geofence
                boolean isInside = geofenceManager.checkIfInsidePolygon(longitude, latitude,x,y);
                // Update the "Result" TextView
                resultTextView.setText(isInside ? "Inside Geofence" : "Outside Geofence");
                if (isInside) {
                    completeGPSVerificationButton.setEnabled(true);
                    completeGPSVerificationButton.setVisibility(View.VISIBLE);
                    locateMeButton.setVisibility(View.INVISIBLE);
                    SharedPreferences sharedPreferences = getSharedPreferences("UserData", MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();

                    // Get current latitude and longitude
                    String latitude1 = latitudeTextView.getText().toString().replace("Latitude: ", "");
                    String longitude1 = longitudeTextView.getText().toString().replace("Longitude: ", "");

                    // Save to SharedPreferences
                    editor.putString("LATITUDE", latitude1);
                    editor.putString("LONGITUDE", longitude1);
                    editor.apply();
                }
                else {
                    completeGPSVerificationButton.setEnabled(false);
                    completeGPSVerificationButton.setVisibility(View.GONE);
                    locateMeButton.setVisibility(View.VISIBLE);
                }
            } else {
                Toast.makeText(this, "Unable to fetch location.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Handle "Complete GPS Verification" button click
    private void handleCompleteGPSVerificationButtonClick() {


        // Navigate to the next activity
        Intent intent = new Intent(this, FaceVerification.class);
//        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        fusedLocationClient.removeLocationUpdates(locationCallback); // Stop location updates
    }
}
