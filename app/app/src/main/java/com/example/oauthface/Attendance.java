package com.example.oauthface;

public class Attendance {
    private String longitude;
    private String latitude;
    private String userId;
    private String timestamp;

    // Default constructor required for Firebase
    public Attendance() {
    }

    public Attendance(String longitude, String latitude, String userId, String timestamp) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.userId = userId;
        this.timestamp = timestamp;
    }

    // Getters and setters
    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
