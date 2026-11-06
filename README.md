# SafeRoute SA

> **OPSC6312 Part 3 - Final app development and evaluation**  
> Student: Jared Williams | Student number: ST10452972  
> Android package: `com.example.opsc6312_poe` | Version: 2.0.0  
> GitHub repository: (https://github.com/ST10452972/SafeRoute-SA)

## Project purpose

SafeRoute SA is an Android personal-safety prototype for South African users. It brings together account access, emergency contacts, incident reporting, an SOS record, a timed Safety Check, a search for police-station map listings, and English, Afrikaans and isiXhosa interface text.

The project was designed around a practical constraint: a person may need to record information when their internet connection is unavailable. The contact, incident and SOS actions therefore enter an app-private SQLite outbox and are sent to Firestore when network access returns. The app is a prototype. It does not dispatch emergency services, send SMS messages, or automatically contact a user's trusted contacts.

## Design considerations

| Consideration | Design decision |
| --- | --- |
| Emergency-first interaction | The dashboard gives the SOS action visual prominence and asks for confirmation before recording it. |
| Connectivity loss | Contacts, incident reports and SOS records are kept in a local SQLite outbox. WorkManager attempts synchronization when the device has a network connection. |
| Privacy | Optional location sharing is stored as a user preference. The app does not currently collect or share GPS location. Firestore records include the signed-in Firebase user ID. |
| Account security | Firebase Authentication handles email/password authentication and Google SSO. Passwords are not written to Firestore. |
| South African users | Phone-number validation accepts common South African formats. The interface includes English, Afrikaans and isiXhosa resources. |
| Clear status | The Contacts screen distinguishes contacts waiting in the local queue from contacts read from Firestore. |
| Reliability | Form validation and logging are used to help handle invalid input and diagnose API or Firebase failures. The final release still requires device testing. |

## Features and current status

This table describes the current source code. “Implemented” means code exists; it does not mean the feature has been verified on the final phone build.

| Feature | Current implementation and limitation |
| --- | --- |
| Email registration and login | Firebase Authentication provides account registration and login. The app does not store passwords in Firestore. Configure Firebase before testing. |
| Google SSO | Android Credential Manager obtains a Google ID token, which the app exchanges with Firebase Authentication. Requires Google enabled in Firebase, the correct Web client ID, SHA-1 fingerprints and a current `google-services.json`. |
| Settings | The app can save a user's name, language and location-sharing preference. The preference does not activate GPS tracking or sharing, and profile values are not all loaded into the fields when Settings opens. |
| SOS | A confirmation dialog precedes a local queue record that can synchronize to Firestore. The action does not call emergency services, send SMS, or notify contacts. |
| Emergency contacts | The app queues a contact locally, writes it to Firestore, and listens for the signed-in user's contacts to display them. Locally queued contacts are marked as waiting to sync. Edit, delete and priority management are not implemented. |
| Incident reports | Text category and description are queued locally and synchronized to Firestore. File/photo attachments and incident GPS coordinates are not currently implemented. |
| REST API | Retrofit calls OpenStreetMap Nominatim over HTTPS to search for police-station map listings by South African town or suburb. It requires internet access and can return incomplete best matches; it is not an emergency dispatch service. |
| Offline mode and sync | SQLite stores selected contact, incident and SOS actions. WorkManager retries Firestore writes when connectivity is available. Stable document IDs prevent duplicate records on retry. Verify this behavior on a physical phone. |
| Push notifications | The app registers an FCM token, handles incoming messages and displays a notification. The timed Safety Check schedules a local missed-check notification. The app does not automatically send an alert to emergency contacts when SOS or a missed check occurs. |
| Safety Check | A 30-minute local reminder can be started or ended. Missing it displays an on-device notification; it does not notify another person. WorkManager timing may be later than exactly 30 minutes. |
| Languages | English, Afrikaans and isiXhosa string resources are included. Some system and error messages remain English. |
| App icon | A custom SafeRoute launcher icon is included in the Android resources. |
| Blob/object storage | **Not implemented yet.** The current app does not upload files to Firebase Storage or another object-storage service. Part 3 requires blob storage; complete and test a private file upload before claiming this requirement is met, or get lecturer approval for an alternative. |
| Location and map features from Part 1 | Live GPS, location sharing, route display and a safety map are not implemented. |



## Architecture and data flow

```text
Android app (Kotlin, Jetpack Compose)
  |-- Firebase Authentication (email/password and Google SSO)
  |-- Retrofit HTTPS -> OpenStreetMap Nominatim (police-station search)
  |-- SQLite outbox -> WorkManager -> Cloud Firestore
  |-- Firestore listener -> display signed-in user's emergency contacts
  |-- Firebase Cloud Messaging -> receive and display notifications
  `-- WorkManager -> local 30-minute Safety Check notification
```

### REST API

The app uses the public Nominatim search endpoint through Retrofit. It sends a query similar to `police station, Johannesburg, South Africa`, requests JSON results, limits the response to five records and restricts results to South Africa (`countrycodes=za`). Results include a display name and coordinates. Nominatim is a map search service, so results can be incomplete and must not be described as a complete or authoritative police-station directory.

### Firestore records

| Collection/document | Fields used by the prototype |
| --- | --- |
| `users/{firebaseUid}` | `fullName`, `email`, `language`, `locationSharing`, `fcmToken`, `createdAt` |
| `emergencyContacts/{eventId}` | `userId`, `clientEventId`, `name`, `phone`, `createdAt` |
| `incidents/{eventId}` | `userId`, `clientEventId`, `category`, `description`, `status`, `createdAt` |
| `sos/{eventId}` | `userId`, `clientEventId`, `status`, `message`, `createdAt` |

The Contacts screen reads Firestore using a listener filtered to the signed-in user's UID. It combines cloud records with contacts still pending in the local outbox. The app does not currently read/display incident or SOS history.

Firestore is configured separately in `app/google-services.json`. Publish owner-scoped rules before testing; see [FIREBASE_SETUP.md](FIREBASE_SETUP.md). Never use open test rules for the final demonstration.

### Offline synchronization

1. The user submits a contact, text incident report or SOS action.
2. The app saves the record to its private SQLite outbox and displays a queued status.
3. WorkManager waits for network connectivity and uploads queued records to Firestore.
4. The record is removed from the outbox only after the Firestore write succeeds.

Demonstrate this by recording a test action while offline, reconnecting, and showing the corresponding Firestore record after sync.

## Setup and build

1. Open this project in Android Studio.
2. Install the Android SDK Platform and Build Tools version 36 and use a compatible JDK (the project is configured for Java 11 source/target compatibility; use the Gradle/JDK combination required by Android Studio).
3. Add `app/google-services.json` for the Firebase Android app with package `com.example.opsc6312_poe`. Keep this file out of GitHub.
4. Enable Email/Password and Google providers in Firebase Authentication. Add the debug and release SHA-1 fingerprints. Put the Firebase Web client ID in `saferoute_google_web_client_id` in `app/src/main/res/values/strings.xml`.
5. Create Firestore and publish the rules in [FIREBASE_SETUP.md](FIREBASE_SETUP.md). Enable Firebase Cloud Messaging and allow notifications on the phone.
6. Sync Gradle, build, install on a physical Android phone and test the flows described below. The police-station REST API also requires internet access.

Build commands from the project root:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. A debug APK is not the signed release APK required for publication evidence. The release signing configuration uses `SAFEROUTE_KEYSTORE_PATH`, `SAFEROUTE_KEY_ALIAS`, `SAFEROUTE_KEYSTORE_PASSWORD` and `SAFEROUTE_KEY_PASSWORD`. Keep the keystore and passwords outside this repository. Without those values, the release build is unsigned.

## Testing and verification

The existing local unit tests cover email/password validation, required values and South African phone-number validation. They do not verify live Firebase, FCM delivery, Nominatim availability or synchronization on a physical phone.

| Test | Result/evidence |
| --- | --- |
| Gradle sync and debug build | **[ADD RESULT AND BUILD DATE]** |
| Unit tests (`testDebugUnitTest`) | **[ADD PASS/FAIL RESULT AND TEST COUNT]** |
| Install and launch on physical Android phone | **[ADD DEVICE/ANDROID VERSION AND RESULT]** |
| Email registration/login and Google SSO | **[ADD RESULT; REDACT ACCOUNT DETAILS]** |
| Create contact and verify it appears in the app and Firestore | **[ADD RESULT/SCREENSHOT LINK]** |
| Submit incident and SOS; verify Firestore records | **[ADD RESULT/SCREENSHOT LINK]** |
| Offline action followed by successful sync | **[ADD RESULT/SCREENSHOT LINK]** |
| Nominatim police-station search | **[ADD PLACE TESTED AND RESULT]** |
| FCM test notification and local Safety Check notification | **[ADD PHONE TEST RESULT/SCREENSHOT LINK]** |
| Afrikaans and isiXhosa screen check | **[ADD RESULT/SCREENSHOT LINK]** |
| Blob-storage upload and access-control test | **[IMPLEMENT STORAGE, THEN ADD TEST RESULT; CURRENTLY NOT IMPLEMENTED]** |

Do not replace these fields with “passed” until you have run and observed the test. Do not include real contacts, passwords, private incident details, signing credentials or unredacted FCM tokens in screenshots.

## GitHub and automated build

The Android source, Gradle build files, tests, README and GitHub Actions workflow belong in the repository. Upload the project files directly; do not submit a ZIP of the source as a substitute for the GitHub repository. Exclude `app/google-services.json`, `local.properties`, generated build/cache folders, keystores and credentials.

The workflow at `.github/workflows/android.yml` is configured to run unit tests and build a debug APK on pushes/pull requests to `main` and on the `final-poe` tag. Confirm that the workflow run is green after the final source is uploaded.

- GitHub repository: https://github.com/ST10452972/SafeRoute-SA


## Release notes

### Version 2.0.0 - Part 3 revision

- Added Google SSO using Android Credential Manager and Firebase Authentication.
- Added SQLite outbox storage and WorkManager synchronization for contacts, incident reports and SOS records.
- Added a Firestore contact listener so saved contacts display in the app, including queued contacts waiting for synchronization.
- Restored the Nominatim REST API police-station search. Results are best-match map listings and may be incomplete.
- Added Firebase Cloud Messaging token registration and client-side notification handling, plus a local 30-minute Safety Check reminder.
- Added English, Afrikaans and isiXhosa resources and language selection.
- Improved sign-in screen contrast and readability.
- Removed incident photo uploads from this build; **blob storage remains outstanding for Part 3**.
- Added Firebase setup and build guidance. Final device testing and evidence still need to be added below.

## Demonstration and publication evidence

The final demonstration must be recorded on a physical Android phone, include a clear voice-over, show the app features working, and show redacted hosted Authentication/Firestore data. 

| Evidence required | Add link or screenshot |
| --- | --- |
| Final narrated Part 3 phone demonstration | **[ADD UNLISTED VIDEO URL]** |
| Login and Google SSO | **[ADD SCREENSHOT: `docs/images/login-sso.png`]** |
| Dashboard and SOS confirmation | **[ADD SCREENSHOT: `docs/images/dashboard-sos.png`]** |
| Contacts displayed from Firestore | **[ADD SCREENSHOT: `docs/images/contacts-firestore.png`]** |
| Incident report and Firestore record | **[ADD SCREENSHOT: `docs/images/incident-firestore.png`]** |
| Offline action and completed synchronization | **[ADD SCREENSHOT: `docs/images/offline-sync.png`]** |
| FCM notification received on phone | **[ADD SCREENSHOT: `docs/images/notification.png`]** |
| Afrikaans and isiXhosa screens | **[ADD SCREENSHOT: `docs/images/languages.png`]** |
| Blob-storage upload and private access test | **[ADD AFTER IMPLEMENTING STORAGE]** |
| Signed release APK | **[ADD SIGNED APK LINK OR SUBMISSION ITEM]** |
| Play Console upload/listing evidence (if applicable) | **[ADD SCREENSHOT OR CONSOLE LINK]** |

Do not claim Play Store publication unless the app has actually been uploaded or published. Keep the release keystore and its passwords private.

## AI-use disclosure

OpenAI Codex was used to assist with reviewing the assignment and project, suggesting and editing Kotlin/Gradle code, debugging Firebase and Android build issues, restoring the REST API screen, adding Firestore contact display, improving sign-in contrast, and drafting project documentation. AI assistance is cited here as OpenAI Codex (OpenAI, 2026). I remain responsible for checking the implementation, Firebase configuration, security rules, build results, physical-device behavior and final evidence. Results must be filled in above only after I personally perform and observe those checks.

## References

1. Android Developers. (2026). *Credential Manager*. Available at: https://developer.android.com/identity/sign-in/credential-manager.
2. Android Developers. (2026). *Notification runtime permission*. Available at: https://developer.android.com/develop/ui/compose/notifications/notification-permission.
3. Firebase. (2026). *Authenticate with Google on Android*. Available at: https://firebase.google.com/docs/auth/android/google-signin.
4. Firebase. (2026). *Get realtime updates with Cloud Firestore*. Available at: https://firebase.google.com/docs/firestore/query-data/listen.
5. Firebase. (2026). *Cloud Firestore Security Rules*. Available at: https://firebase.google.com/docs/firestore/security/get-started.
6. Firebase. (2026). *Receive messages in Android apps*. Available at: https://firebase.google.com/docs/cloud-messaging/android/receive.
7. OpenStreetMap Foundation. (2026). *Nominatim Search API*. Available at: https://nominatim.org/release-docs/latest/api/Search/.
8. OpenAI. (2026). *Codex*, AI coding assistant used to assist with this project.
9. GitHub. (2026). *GitHub Actions documentation*. Available at: https://docs.github.com/actions.
