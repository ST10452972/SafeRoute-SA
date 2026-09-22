# Firebase setup (required before sign-in works)

1. In the [Firebase console](https://console.firebase.google.com/), create a project named **SafeRoute SA**.
2. Add an Android app using the exact package name: `com.example.opsc6312_poe`.
3. Download `google-services.json` and put it at `app/google-services.json`. Do not rename, commit, or share it.
4. In **Authentication → Sign-in method**, enable **Email/Password**.
5. In **Firestore Database**, create a Cloud Firestore database. Start in production mode and publish these rules:

```text
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read, create, update: if request.auth != null && request.auth.uid == userId;
      allow delete: if false;
    }
    match /{collection}/{document} {
      allow create: if request.auth != null && request.resource.data.userId == request.auth.uid;
      allow read, update, delete: if request.auth != null && resource.data.userId == request.auth.uid;
    }
  }
}
```

6. Sync Gradle, then rebuild the app. The project deliberately builds without the JSON file so its source can be inspected safely; authentication and Firestore operations require the file.

## Firestore data created by the prototype

- `users/{Firebase UID}`: profile, language, location-sharing preference and creation timestamp.
- `emergencyContacts`: name, phone, owner UID and timestamp.
- `incidents`: category, description, owner UID, status and timestamp.
- `sos`: active SOS record, owner UID and timestamp.

The app never stores a password in Firestore. Firebase Authentication manages password hashing and account credentials.
