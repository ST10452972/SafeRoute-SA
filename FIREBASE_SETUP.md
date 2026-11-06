# Firebase setup (required before sign-in works)

1. In the [Firebase console](https://console.firebase.google.com/), create a project named **SafeRoute SA**.
2. Add an Android app using the exact package name: `com.example.opsc6312_poe`.
3. Download `google-services.json` and put it at `app/google-services.json`. Do not rename, commit, or share it.
4. In **Authentication → Sign-in method**, enable **Email/Password**.
5. Enable **Google** as a sign-in provider. Add the SHA-1 certificate fingerprints for both your debug certificate and the release signing certificate, then download a fresh `google-services.json`.
6. Copy your Firebase project's **Web client ID** into the `saferoute_google_web_client_id` string in `app/src/main/res/values/strings.xml`. This is the public OAuth client ID, not a client secret. The resource is blank by default, so the app gives a setup message until you add it. Sync and rebuild after setting it.
7. In **Firestore Database**, create a Cloud Firestore database. Start in production mode and publish these rules:

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

8. In **Cloud Messaging**, use Firebase Console to send a test notification to a signed-in device token. Android 13+ requires the notification permission prompt.
9. Sync Gradle, then rebuild the app. The project deliberately builds without the JSON file so its source can be inspected safely; authentication, messaging, and Firestore operations require the file and Firebase setup. Incident reports are text-only and do not use Firebase Storage.

## Firestore data created by the prototype

- `users/{Firebase UID}`: profile, language, location-sharing preference and creation timestamp.
- `emergencyContacts`: name, phone, owner UID and timestamp.
- `incidents`: category, description, owner UID, status and timestamp.
- `sos`: active SOS record, owner UID and timestamp.
- `users/{Firebase UID}.fcmToken`: current device token used by a trusted notification sender. Protect this field with the same user-only Firestore rule as the rest of the profile.
- Local SQLite `outbox`: incident, contact and SOS records pending upload. A unique Firestore document ID is reused during retry to avoid duplicate records.

The app never stores a password in Firestore. Firebase Authentication manages password hashing and account credentials. Google sign-in uses Credential Manager and exchanges the Google ID token with Firebase Authentication. The `saferoute_google_web_client_id` string must contain the Firebase project's Web client ID for that flow to work.
