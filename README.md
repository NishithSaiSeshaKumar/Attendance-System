# Smart & Secure Attendance System

A combined **Android** (Java) + **Flask** solution for secure, on-device **face recognition** and **GPS geofencing** attendance, with a web-based admin dashboard.

---

## Features
- **Android App**:  
  - Gmail login & single-device binding  
  - MobileFaceNet face verification (TensorFlow Lite)  
  - GPS geofence check (Raycasting algorithm)  
  - Logs to Firebase Firestore  

- **Flask Dashboard**:  
  - Admin login  
  - User approval & device reset  
  - Geofence management  
  - Attendance monitoring & CSV export  

---

## Quickstart

1. **Android App** (`attendance-project/app`):  
   - Open in Android Studio  
   - Add `google-services.json`  
   - Run on device/emulator  

2. **Flask Dashboard** (`attendance-project/dashboard`):  
   ```bash
   cd dashboard
   python -m venv venv
   source venv/bin/activate  # or venv\Scripts\activate
   pip install -r requirements.txt
   flask run --port=5000
   ```
Access at [http:127.0.0.1:5000](http://localhost:5000)

Repo Layout

attendance-project/
├── app/         # Android app
└── dashboard/   # Flask admin panel
