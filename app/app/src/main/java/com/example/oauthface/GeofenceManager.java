package com.example.oauthface;

import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;

public class GeofenceManager {

    private Context context;
    private boolean insidePremises = false;
//    double[] x = {80.5475027, 80.5473042, 80.5471862, 80.5478407, 80.5483074, 80.5488384, 80.5492408, 80.54945, 80.5498952, 80.5503137, 80.5506355, 80.5508716, 80.5511237, 80.5513275, 80.5515099, 80.5520571, 80.5512846, 80.5510271, 80.5508179, 80.5506892, 80.5503083, 80.5501688, 80.5498684, 80.5488921, 80.5486668, 80.5488116, 80.5487633, 80.5483932, 80.5481786, 80.5480981, 80.5478031, 80.547669};
//    double[] y = {16.233334, 16.2328498, 16.2326283, 16.2323193, 16.2320669, 16.2318403, 16.2320309, 16.2323193, 16.2320721, 16.2318918, 16.2317682, 16.2320463, 16.2324326, 16.2327262, 16.2329992, 16.2336997, 16.2342147, 16.2340035, 16.2339057, 16.2337615, 16.2331795, 16.232958, 16.2325923, 16.2328498, 16.2330249, 16.2332928, 16.233576, 16.2335966, 16.2333855, 16.2331949, 16.233334, 16.2334679};

    public GeofenceManager(Context context) {
        this.context = context;
    }
/// Geofencing using ray casting algorithm
//    write a function to check if gps enabled or else prompt to enable gps
    public boolean isGPSEnabled() {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean isEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!isEnabled) {
            // Prompt user to enable GPS
            new AlertDialog.Builder(context)
                    .setTitle("Enable GPS")
                    .setMessage("GPS is required for this feature. Please enable GPS.")
                    .setPositiveButton("Settings", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        context.startActivity(intent);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        }
        return isEnabled;
    }
    public boolean checkIfInsidePolygon(double longitude, double latitude,double[] x,double[] y) {
        // Polygon coordinates (latitude and longitude)
        /* College */
//        double[] x = {80.5475027, 80.5473042, 80.5471862, 80.5478407, 80.5483074, 80.5488384, 80.5492408, 80.54945, 80.5498952, 80.5503137, 80.5506355, 80.5508716, 80.5511237, 80.5513275, 80.5515099, 80.5520571, 80.5512846, 80.5510271, 80.5508179, 80.5506892, 80.5503083, 80.5501688, 80.5498684, 80.5488921, 80.5486668, 80.5488116, 80.5487633, 80.5483932, 80.5481786, 80.5480981, 80.5478031, 80.547669};
//        double[] y = {16.233334, 16.2328498, 16.2326283, 16.2323193, 16.2320669, 16.2318403, 16.2320309, 16.2323193, 16.2320721, 16.2318918, 16.2317682, 16.2320463, 16.2324326, 16.2327262, 16.2329992, 16.2336997, 16.2342147, 16.2340035, 16.2339057, 16.2337615, 16.2331795, 16.232958, 16.2325923, 16.2328498, 16.2330249, 16.2332928, 16.233576, 16.2335966, 16.2333855, 16.2331949, 16.233334, 16.2334679};

        /* home */
//        double[] x = {80.6568546,80.6568412,80.6571966,80.6572073,80.6568546}; // First and last points are the same
//        double[] y = {16.2320746,16.2317437,16.2317501,16.2320708,16.2320746}; // First and last points are the same

        boolean result = isPointInPolygon(longitude, latitude, x, y);

        if (result && !insidePremises) {
//            Toast.makeText(context, "You are inside the premises!", Toast.LENGTH_SHORT).show();
            insidePremises = true;
            return true; // User is inside the polygon
        } else if (!result && insidePremises) {
            // User just exited the premises
//            Toast.makeText(context, "Not Inside premises", Toast.LENGTH_SHORT).show();
            insidePremises = false;
        }
        return result; // Return whether the user is inside the polygon
    }

    private boolean isPointInPolygon(double pointX, double pointY, double[] polygonX, double[] polygonY) {
        int n = polygonX.length;
        boolean inside = false;

        for (int i = 0, j = n - 1; i < n; j = i++) {
            if ((polygonY[i] > pointY) != (polygonY[j] > pointY) &&
                    (pointX < (polygonX[j] - polygonX[i]) * (pointY - polygonY[i]) / (polygonY[j] - polygonY[i]) + polygonX[i])) {
                inside = !inside;
            }
        }
        return inside;
    }
}