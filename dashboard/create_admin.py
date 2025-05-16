import firebase_admin
from firebase_admin import auth, credentials

cred = credentials.Certificate("firebase_service.json")
firebase_admin.initialize_app(cred)

# Create user (if not exists)
try:
    user = auth.create_user(
        email="admin@college.edu",
        password="SecurePassword123"
    )
    print(f"Created user: {user.uid}")
except Exception as e:
    print(f"Error: {str(e)}")
#9mZAft7adWWcyydwyt1nq4QDqcw1
# Set admin claim
auth.set_custom_user_claims(user.uid, {'admin': True})
print("Admin privileges granted")
