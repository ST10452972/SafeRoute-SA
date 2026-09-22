# SafeRoute SA

SafeRoute SA is a Kotlin/Jetpack Compose Android safety prototype for OPSC6312 Part 2. It provides Firebase email/password registration and login, profile settings, emergency contacts, incident reporting, a confirmed SOS action, and a REST-based nearby police-station search for South Africa.

## Part 2 features

| Requirement | Implementation |
| --- | --- |
| Registration and login | Firebase Authentication Email/Password. Passwords are handled by Firebase, never Firestore. |
| Settings | Name, language and optional location-sharing preference stored in Firestore. |
| Database | Firebase Firestore stores profile, contact, SOS and incident records. |
| REST API | Retrofit calls OpenStreetMap Nominatim over HTTPS to search for South African police stations. |
| Safety Check | A local 30-minute check-in interface for the prototype; missed-check alerts are a final-PoE feature. |
| Validation and feedback | Required-field, email and password validation; loading and error messages. |
| Tests | JUnit tests cover validation. |
| Automation | GitHub Actions runs unit tests and builds a debug APK. |

## Setup

1. Open this folder in Android Studio.
2. Complete [FIREBASE_SETUP.md](FIREBASE_SETUP.md), especially adding `app/google-services.json`.
3. Sync Gradle and run `assembleDebug` or use Android Studio's Run button.
4. Run tests with `./gradlew testDebugUnitTest`.

## Physical-device and BlueStacks testing

### Physical Android phone

1. Enable Developer options by tapping **Build number** seven times.
2. Enable **USB debugging**, connect the phone, and accept the RSA prompt.
3. Select the phone in Android Studio and press Run. This is the recommended route for the required demo video.

### BlueStacks (alternative)

1. In Android Studio, install **Android SDK Platform-Tools** under Settings → Android SDK → SDK Tools.
2. Start BlueStacks and enable ADB in its Advanced settings.
3. In PowerShell, from the SDK's `platform-tools` folder, run:

```powershell
.\adb.exe connect 127.0.0.1:5555
.\adb.exe devices
```

4. Choose `127.0.0.1:5555` in Android Studio and run the app. If BlueStacks uses another port, use the port displayed in its ADB settings.

## Demonstration checklist

Record a voice-over video on a real phone where possible. Show registration, Firebase Authentication's new account, settings saved in Firestore, a contact, an incident, confirmed SOS record, and a nearby-resource REST search. Add the unlisted YouTube video URL here before submission: **VIDEO_URL_TO_ADD**.

## GitHub

Create a new GitHub repository, commit this project, and push to `main`. The workflow is at `.github/workflows/android.yml`; it will run the JUnit tests and produce a debug APK artifact for each push or pull request.

## References

- Firebase. *Add Firebase to your Android project* and *Firebase Authentication for Android*.
- OpenStreetMap Foundation. *Nominatim Search API*.
- Android Developers. *Jetpack Compose* and *ViewModel*.
