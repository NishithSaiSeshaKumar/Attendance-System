from flask import Flask, jsonify, render_template, request, redirect, session, url_for, flash, make_response
import firebase_admin
from firebase_admin import credentials, db
import os
from datetime import datetime, timedelta
from functools import wraps
import time
from io import StringIO
import csv
from collections import defaultdict
from datetime import datetime, timedelta
import json

app = Flask(__name__)
app.secret_key = os.urandom(24)

# Initialize Firebase
cred = credentials.Certificate("febsevth-firebase-adminsdk-fbsvc-27ccf2c96c.json")
firebase_admin.initialize_app(cred, {
    'databaseURL': "https://febsevth-default-rtdb.firebaseio.com/"
})

# Helper function to check login
def login_required(f):
    @wraps(f)
    def wrapper(*args, **kwargs):
        if not session.get('logged_in'):
            return redirect('/')
        return f(*args, **kwargs)
    return wrapper

@app.route('/')
def login_page():
    if session.get('logged_in'):
        return redirect('/dashboard')
    user_id = request.cookies.get('user_id')
    if user_id:
        session['logged_in'] = True
        session['user_id'] = user_id
        return redirect('/dashboard')
    
    return render_template('login.html')

@app.route('/login', methods=['POST'])
def login():
    data = request.get_json()
    ID = data.get('ID')
    password = data.get('password')
    remember_me = data.get('remember_me', False)

    ref = db.reference('admins')
    users = ref.get()

    if ID in users:
        user_data = users[ID]
        if user_data['ID'] == ID and user_data['password'] == password:
            session['logged_in'] = True
            session['user_id'] = ID

            response = jsonify({"success": True})
            if remember_me:
                response.set_cookie('user_id', ID, max_age=7 * 24 * 60 * 60)  # 7 days
            return response, 200

    return jsonify({"success": False, "message": "Invalid credentials"}), 401

@app.route('/logout')
def logout():
    session.clear()
    response = redirect('/')
    response.delete_cookie('user_id')
    return response

def point_in_polygon(point, polygon):
    x, y = point
    n = len(polygon)
    inside = False

    p1x, p1y = polygon[0]
    for i in range(n + 1):
        p2x, p2y = polygon[i % n]
        if y > min(p1y, p2y):
            if y <= max(p1y, p2y):
                if x <= max(p1x, p2x):
                    if p1y != p2y:
                        xints = (y - p1y) * (p2x - p1x) / (p2y - p1y) + p1x
                    if p1x == p2x or x <= xints:
                        inside = not inside
        p1x, p1y = p2x, p2y

    return inside

# Dashboard Route
@app.route('/dashboard')
@login_required
def dashboard():
    ref = db.reference('/')
    try:
        # Fetch total users
        users = ref.child('users').get() or {}
        total_users = len(users)

        # Fetch today's attendance
        today_date = datetime.now().strftime('%Y-%m-%d')
        today_attendance = ref.child('attendance').child(today_date).get() or {}
        # Ensure timestamps are parsed correctly
        present_today = 0
        for record in today_attendance.values():
            try:
                record_date = datetime.fromisoformat(record['timestamp']).strftime('%Y-%m-%d')
                if record_date == today_date:
                    present_today += 1
            except Exception as e:
                print(f"Error parsing timestamp: {e}")
        # Calculate absentees
        absentees = total_users - present_today

        # Weekly attendance data
        attendance_data = []
        for i in range(6, -1, -1):
            date = (datetime.now() - timedelta(days=i)).strftime('%Y-%m-%d')
            day_data = ref.child('attendance').child(date).get() or {}

            # Count attendance for the day
            attendance_count = 0
            for record in day_data.values():
                try:
                    record_date = datetime.fromisoformat(record['timestamp']).strftime('%Y-%m-%d')
                    if record_date == date:
                        attendance_count += 1
                except Exception as e:
                    print(f"Error parsing timestamp for weekly data: {e}")

            attendance_data.append({
                'date': date,
                'count': attendance_count
            })
        # Geofence compliance
        active_geofence = ref.child('active_geofence').get()
        if active_geofence:
            geofence = ref.child(f'geofences/{active_geofence}').get()
            if geofence:
                inside_count = 0
                outside_count = 0
                poly_points = list(zip(geofence['latitudes'], geofence['longitudes']))

                for record in today_attendance.values():
                    point = (float(record['latitude']), float(record['longitude']))
                    if point_in_polygon(point, poly_points):
                        inside_count += 1
                    else:
                        outside_count += 1

                geofence_compliance = {
                    'inside': inside_count,
                    'outside': outside_count
                }
        # Geofence compliance (placeholder for now)
        geofence_compliance = {
            'inside': 85,  # Example data
            'outside': 15  # Example data
        }

        # Recent attendance
        recent_attendance = []
        for record_id, record in sorted(today_attendance.items(), key=lambda x: x[1]['timestamp'], reverse=True)[:10]:
            recent_attendance.append({
                'user': record.get('userId'),
                'check_in_time': datetime.fromisoformat(record['timestamp']).strftime('%H:%M:%S'),
                'status': 'Present',  # Example status
                'geofence': 'Inside' if record.get('latitude') and record.get('longitude') else 'Outside'
            })

        stats = {
            'users_count': total_users,
            'present_today': present_today,
            'absentees': absentees,
            'geofences_count': len(ref.child('geofences').get() or {})
        }

        return render_template(
            'dashboard.html',
            stats=stats,
            attendance_data=attendance_data,
            geofence_compliance=geofence_compliance,
            recent_attendance=recent_attendance
        )
    except Exception as e:
        flash(f'Error loading dashboard: {str(e)}', 'error')
        return render_template('dashboard.html')

# Users Management Route
@app.route('/users')
@login_required
def users_management():
    ref = db.reference('users')
    users = ref.get() or {}
    return render_template('users.html', users=users)
# Add these new routes to your existing main.py

@app.route('/api/users', methods=['POST'])
@login_required
def add_user():
    try:
        user_data = request.json
        
        # Required fields validation
        if not all([user_data.get('user_id'), user_data.get('name'), user_data.get('email')]):
            return jsonify({"success": False, "message": "Missing required fields"}), 400
            
        ref = db.reference(f'users/{user_data["user_id"]}')
        
        # Check if user already exists
        if ref.get():
            return jsonify({"success": False, "message": "User ID already exists"}), 400
            
        # Create new user
        ref.set({
            'email': user_data['email'],
            'hasregistered': False,
            'created_at': datetime.now().isoformat(),
            'created_by': session.get('user_id')
        })
        
        return jsonify({"success": True}), 201
        
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500

@app.route('/api/users/<user_id>', methods=['GET', 'PUT', 'DELETE'])
@login_required
def manage_user(user_id):
    try:
        ref = db.reference(f'users/{user_id}')
        
        if request.method == 'GET':
            user = ref.get()
            if not user:
                return jsonify({"success": False, "message": "User not found"}), 404
            return jsonify(user), 200
            
        elif request.method == 'PUT':
            user_data = request.json
            
            # Get existing user data
            existing_user = ref.get() or {}
            
            # Update only allowed fields
            updated_data = {
                'email': user_data.get('email', existing_user.get('email')),
                'hasregistered': user_data.get('hasregistered', existing_user.get('hasregistered', True)),  # Corrected key
                'updated_at': datetime.now().isoformat(),
                'updated_by': session.get('user_id')
            }
            
            ref.update(updated_data)
            return jsonify({"success": True}), 200
            
        elif request.method == 'DELETE':
            # Verify user exists first
            if not ref.get():
                return jsonify({"success": False, "message": "User not found"}), 404
                
            ref.delete()
            return jsonify({"success": True}), 200
            
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500
    

# Geofence Management Route
@app.route('/geofences')
@login_required
def geofence_page():
    geofences_ref = db.reference('geofences')
    active_ref = db.reference('active_geofence')
    return render_template('geofences.html', 
                         geofences=geofences_ref.get() or {},
                         active_geofence=active_ref.get())

def get_coordinates(txt):
    result = txt.split(",")
    latitudes = [float(i.split()[0]) for i in result]
    longitudes = [float(i.split()[1]) for i in result]
    if latitudes[0] == latitudes[-1] and longitudes[0] == longitudes[-1]:
        return "Valid Coordinates", longitudes, latitudes
    else:
        return "Invalid Coordinates", longitudes, latitudes


@app.route('/api/geofences', methods=['POST'])
@login_required
def add_geofence():
    try:
        data = request.json
        locality = data.get('locality')
        coordinates = data.get('coordinates')

        # Validate required fields
        if not all([locality, coordinates]):
            return jsonify({"success": False, "message": "Missing required fields"}), 400

        # Validate and process coordinates
        status, latitudes, longitudes = get_coordinates(coordinates)
        if status == "Invalid Coordinates":
            return jsonify({"success": False, "message": "Coordinates must form a closed polygon."}), 400

        # Ensure at least 3 points are provided
        if len(latitudes) < 3 or len(longitudes) < 3:
            return jsonify({"success": False, "message": "A geofence requires at least 3 points."}), 400

        # Create a new geofence
        new_id = f"geofence_{str(int(time.time()))[-3:]}"
        db.reference(f'geofences/{new_id}').set({
            "locality": locality,
            "latitudes": latitudes,
            "longitudes": longitudes,
        })
        return jsonify({"success": True, "id": new_id}), 201
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500    

@app.route('/api/geofences/<geofence_id>', methods=['GET'])
@login_required
def get_geofence(geofence_id):
    try:
        geofence = db.reference(f'geofences/{geofence_id}').get()
        if not geofence:
            return jsonify({"success": False, "message": "Geofence not found"}), 404
        return jsonify({"success": True, "geofence": geofence}), 200
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500

@app.route('/api/geofences/<geofence_id>', methods=['DELETE'])
@login_required
def delete_geofence(geofence_id):
    try:
        db.reference(f'geofences/{geofence_id}').delete()
        # Check if this was the active geofence
        active_ref = db.reference('active_geofence')
        if active_ref.get() == geofence_id:
            active_ref.delete()
        return jsonify({"success": True}), 200
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500


@app.route('/api/active_geofence', methods=['POST'])
@login_required
def set_active_geofence():
    try:
        geofence_id = request.json.get('geofence_id')
        if not geofence_id:
            return jsonify({"success": False, "message": "Geofence ID required"}), 400
        
        db.reference('active_geofence').set(geofence_id)
        return jsonify({"success": True}), 200
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500

# Attendance Route
@app.route('/attendance')
@login_required
def attendance_page():
    # Always fetch the latest attendance data from Firebase
    attendance_ref = db.reference('attendance')
    local_attendance_db = attendance_ref.get() or {}

    # Get filters from query parameters
    date_filter = request.args.get('date', None)
    user_filter = request.args.get('user', None)

    # Filter attendance data locally
    filtered_data = []
    for record_id, record in local_attendance_db.items():
        # Convert timestamp to date and time
        user_id = record.get('userId') or record.get('userID')
        record['userId'] = user_id 
        record_datetime = datetime.fromisoformat(record['timestamp'])
        record_date = record_datetime.strftime('%Y-%m-%d')
        record_time = record_datetime.strftime('%H:%M:%S')

        # Add date and time to the record
        record['date'] = record_date
        record['time'] = record_time

        # Apply filters
        if (not date_filter or record_date == date_filter) and (not user_filter or str(user_id) == user_filter):
            filtered_data.append(record)

    # Fetch user list for the dropdown
    users_ref = db.reference('users')
    users = users_ref.get() or {}

    return render_template('attendance.html', attendance=filtered_data, users=users, default_date=date_filter)


@app.template_filter('datetimeformat')
def datetimeformat(value, format='%Y-%m-%d %H:%M:%S'):
    try:
        return datetime.fromisoformat(value).strftime(format)
    except Exception:
        return value  # Return the original value if formatting fails
    
@app.route('/api/attendance/export')
@login_required
def export_attendance():
    attendance_ref = db.reference('attendance')
    local_attendance_db = attendance_ref.get() or {}

    # Fetch filters
    date_filter = request.args.get('date', None)
    user_filter = request.args.get('user', None)

    # Filter attendance data locally
    filtered_data = []
    for timestamp, record in local_attendance_db.items():
        # Convert timestamp to date and time
        record_datetime = datetime.fromisoformat(record['timestamp'])
        record_date = record_datetime.strftime('%Y-%m-%d')
        record_time = record_datetime.strftime('%H:%M:%S')

        # Add date and time to the record
        record['date'] = record_date
        record['time'] = record_time

        # Apply filters
        if (not date_filter or record_date == date_filter) and (not user_filter or str(record['userID']) == user_filter):
            filtered_data[timestamp] = record

    # Create CSV
    csv_output = StringIO()
    writer = csv.writer(csv_output)
    writer.writerow(['User ID', 'Date', 'Time', 'Latitude', 'Longitude'])

    for timestamp, record in filtered_data.items():
        writer.writerow([
            record.get('userID', ''),
            record.get('date', ''),
            record.get('time', ''),
            record.get('latitude', ''),
            record.get('longitude', '')
        ])

    response = make_response(csv_output.getvalue())
    response.headers['Content-Disposition'] = f'attachment; filename=attendance_{date_filter or "all"}.csv'
    response.headers['Content-type'] = 'text/csv'
    return response

'''
# Settings Route
@app.route('/settings')
@login_required
def settings_page():
    logs_ref = db.reference('admin_logs')
    logs = logs_ref.get() or {}

    # Convert logs to a list and sort by timestamp
    sorted_logs = sorted(
        logs.values(),
        key=lambda log: datetime.fromisoformat(log['timestamp']),
        reverse=True  # Sort in descending order (most recent first)
    )

    # Limit to the last 50 logs
    limited_logs = sorted_logs[:50]
    admins_ref = db.reference('admins')
    admins = admins_ref.get() or {}


    return render_template('settings.html', logs=limited_logs)

@app.route('/api/change-password', methods=['POST'])
@login_required
def change_password():
    try:
        data = request.json
        user_id = session.get('user_id')
        
        # Verify current password
        admin_ref = db.reference(f'admins/{user_id}')
        admin_data = admin_ref.get()
        
        if admin_data['password'] != data['currentPassword']:
            return jsonify({"success": False, "message": "Incorrect current password"}), 401
            
        # Update password
        admin_ref.update({'password': data['newPassword']})
        
        # Log action
        log_ref = db.reference('admin_logs').push()
        log_ref.set({
            "action": "password_change",
            "user_id": user_id,
            "timestamp": datetime.now().isoformat(),
            "details": "Updated admin password"
        })
        
        return jsonify({"success": True}), 200
    
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500

@app.route('/api/admins', methods=['POST'])
@login_required
def create_admin():
    try:
        data = request.json
        required_fields = ['admin_id', 'email', 'password']
        
        if not all(field in data for field in required_fields):
            return jsonify({"success": False, "message": "Missing required fields"}), 400
        
        admin_id = data['admin_id']
        admins_ref = db.reference('admins')
        
        # Check if admin ID exists
        if admins_ref.child(admin_id).get():
            return jsonify({"success": False, "message": "Admin ID already exists"}), 400
            
        # Check email uniqueness
        existing_emails = [a['email'] for a in admins_ref.get().values()]
        if data['email'] in existing_emails:
            return jsonify({"success": False, "message": "Email already registered"}), 400
        
        # Create admin
        admins_ref.child(admin_id).set({
            "email": data['email'],
            "password": data['password'],
            "created_at": datetime.now().isoformat(),
            "created_by": session.get('user_id'),
            "last_login": None
        })
        
        # Audit log
        log_ref = db.reference('admin_logs').push()
        log_ref.set({
            "action": "admin_created",
            "user_id": session.get('user_id'),
            "timestamp": datetime.now().isoformat(),
            "details": f"Created new admin: {admin_id}"
        })
        
        return jsonify({"success": True}), 201
        
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500

@app.route('/api/admins/<admin_id>', methods=['DELETE'])
@login_required
def delete_admin(admin_id):
    try:
        if admin_id == session.get('user_id'):
            return jsonify({"success": False, "message": "Cannot delete yourself"}), 400
            
        db.reference(f'admins/{admin_id}').delete()
        
        # Audit log
        log_ref = db.reference('admin_logs').push()
        log_ref.set({
            "action": "admin_deleted",
            "user_id": session.get('user_id'),
            "timestamp": datetime.now().isoformat(),
            "details": f"Deleted admin: {admin_id}"
        })
        
        return jsonify({"success": True}), 200
        
    except Exception as e:
        return jsonify({"success": False, "message": str(e)}), 500
'''    
# Analytics Route
'''
@app.route('/analytics')
@login_required
def analytics_page():
    return render_template('analytics.html')

@app.route('/api/analytics/daily-checkins')
@login_required
def daily_checkins():
    try:
        attendance_ref = db.reference('attendance')
        all_attendance = attendance_ref.get() or {}
        
        daily_data = defaultdict(lambda: {'total': 0, 'unique': set()})
        
        for date, entries in all_attendance.items():
            for entry in entries.values():
                daily_data[date]['total'] += 1
                daily_data[date]['unique'].add(entry['userID'])
        
        formatted_data = []
        for date, stats in sorted(daily_data.items()):
            formatted_data.append({
                'date': date,
                'total': stats['total'],
                'unique': len(stats['unique'])
            })
            
        return jsonify(formatted_data)
    
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/api/analytics/geofence-compliance')
@login_required
def geofence_compliance():
    try:
        active_geofence_id = db.reference('active_geofence').get()
        if not active_geofence_id:
            return jsonify({"error": "No active geofence set"}), 400
            
        geofence = db.reference(f'geofences/{active_geofence_id}').get()
        if not geofence:
            return jsonify({"error": "Active geofence not found"}), 404
        
        attendance_ref = db.reference('attendance')
        all_checkins = [entry for date in attendance_ref.get().values() 
                       for entry in date.values()]
        
        inside_count = 0
        poly_points = list(zip(geofence['latitudes'], geofence['longitudes']))
        
        for checkin in all_checkins:
            if point_in_polygon(
                (checkin['latitude'], checkin['longitude']),
                poly_points
            ):
                inside_count += 1
                
        return jsonify({
            'inside': inside_count,
            'outside': len(all_checkins) - inside_count,
            'compliance_rate': round((inside_count/len(all_checkins))*100, 2) if all_checkins else 0
        })
        
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/api/analytics/checkin-times')
@login_required
def checkin_times():
    try:
        attendance_ref = db.reference('attendance')
        all_checkins = [entry for date in attendance_ref.get().values() 
                       for entry in date.values()]
        
        hourly_counts = defaultdict(int)
        for checkin in all_checkins:
            hour = datetime.fromisoformat(checkin['timestamp']).hour
            hourly_counts[f"{hour:02}:00"] += 1
            
        return jsonify(hourly_counts)
    
    except Exception as e:
        return jsonify({"error": str(e)}), 500

# Enhanced point-in-polygon algorithm
def point_in_polygon(point, polygon):
    x, y = point
    n = len(polygon)
    inside = False
    
    p1x, p1y = polygon[0]
    for i in range(n+1):
        p2x, p2y = polygon[i % n]
        if y > min(p1y, p2y):
            if y <= max(p1y, p2y):
                if x <= max(p1x, p2x):
                    if p1y != p2y:
                        xints = (y-p1y)*(p2x-p1x)/(p2y-p1y)+p1x
                    if p1x == p2x or x <= xints:
                        inside = not inside
        p1x, p1y = p2x, p2y
        
    return inside
'''


'''
# API Endpoints
@app.route('/api/recent_activity')
@login_required
def recent_activity():
    ref = db.reference('logs').order_by_child('timestamp').limit_to_last(5)
    logs = ref.get() or {}
    return jsonify(list(logs.values()))
'''
if __name__ == '__main__':
    app.run(debug=True)