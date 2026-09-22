# SafeRoute SA

**OPSC6312 Part 2 – App Prototype Development**
**Student:** Jared Williams
**Student Number:** ST10452972

## About SafeRoute SA

SafeRoute SA is an Android safety app prototype built in Kotlin for South African users. The main idea behind the app is to keep important safety features in one place instead of making the user go through different apps or menus.

The current prototype includes user registration and login, a safety dashboard, emergency contacts, incident reporting, an SOS feature, settings, a Safety Check interface, and a search for nearby police stations.

This version was developed for **Part 2 of the OPSC6312 PoE**, where the focus is on building a working prototype and demonstrating the required features. The final PoE will include some additional features that are not part of this prototype.

## Demonstration Video

Youtube video link
https://youtu.be/CY-z236RZow?si=iRXX8ymZza2XqhgT 

The demonstration video will show the main features of the app and explain what is happening using a voice-over. It will include:

* Registering a new user
* Logging into the app
* Changing settings
* Saving information to Firestore
* Searching for nearby police stations using the REST API
* Adding an emergency contact
* Reporting an incident
* Using the SOS button
* Viewing the stored information in the Firebase Console


## Features Implemented

| Requirement         | How it is implemented                                                                                                                                                                                                |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Register and log in | Users can create an account and log in using Firebase Authentication with email and password. Passwords are handled by Firebase and are not stored directly in Firestore.                                            |
| Settings            | Users can update their name, preferred language and location-sharing preference. This information is stored in their Firestore profile.                                                                              |
| SOS                 | The dashboard has a large SOS button. The user must confirm the action before an SOS record is saved. The current prototype does not contact emergency services, send SMS messages or automatically notify contacts. |
| Emergency contacts  | Users can save the name and phone number of a trusted emergency contact.                                                                                                                                             |
| Incident reporting  | Users can select an incident category and enter a description. The report is then saved to Firestore.                                                                                                                |
| Safety Check        | The app includes a basic 30-minute check-in interface as part of the safety dashboard. Automatic alerts for missed check-ins are planned for the final PoE.                                                          |
| REST API            | Retrofit is used to make HTTPS requests to the OpenStreetMap Nominatim API and search for nearby police stations in South Africa.                                                                                    |
| Input validation    | The app checks required fields, email addresses and passwords before submitting information. This helps prevent invalid information and reduces the chance of the app crashing.                                      |
| Unit testing        | JUnit tests are used to test the main validation rules.                                                                                                                                                              |
| GitHub Actions      | GitHub Actions runs the unit tests and builds a debug APK when changes are pushed to the repository.                                                                                                                 |

The Part 2 brief requires the prototype to include the non-PoE features from the assessment instructions, use a REST API, handle invalid input without crashing, and include automated testing and GitHub Actions.

## Design Considerations

The design of SafeRoute SA was based around making the important safety features easy to find.

The SOS button is one of the most noticeable parts of the dashboard because it is an important feature of the app. A confirmation message is shown before an SOS is saved so that it is not triggered accidentally.

The app mainly uses blue for the normal interface because it fits the safety theme. Red is used for the SOS action so that it stands out from the rest of the interface.

I also tried to keep the screens simple by using short labels, straightforward forms and clear feedback messages. This is especially important for a safety app because the user should not have to spend a long time trying to figure out what to do.

Privacy was also considered when designing the prototype. Location sharing is treated as an optional setting. The current version does not collect the user's actual GPS location, make emergency phone calls or automatically contact emergency contacts.

## Technical Design

The basic structure of the application is:

```text
Android App
Kotlin + Jetpack Compose
        |
        |-- Firebase Authentication
        |     Email/password accounts
        |
        |-- Cloud Firestore
        |     User profiles
        |     Emergency contacts
        |     Incident reports
        |     SOS records
        |
        `-- Retrofit HTTPS Request
              OpenStreetMap Nominatim
              Police station search
```

## Main Libraries and SDKs

* **Kotlin and Jetpack Compose** – used to build the Android application and user interface.
* **Firebase Authentication** – used for registering and logging users in.
* **Cloud Firestore** – used to store the application's online data.
* **Retrofit** – used to make REST API requests.
* **Gson** – used to handle JSON data from the API.
* **JUnit** – used for unit testing.

## Firestore Structure

The main Firestore collections used by the application are:

```text
users/{firebaseUid}

    fullName
    email
    language
    locationSharing
    createdAt


emergencyContacts/{documentId}

    userId
    name
    phone
    createdAt


incidents/{documentId}

    userId
    category
    description
    status
    createdAt


sos/{documentId}

    userId
    status
    message
    createdAt
```

The `userId` field is used to link records back to the signed-in user.

## Firebase Setup

Before running the project, Firebase needs to be connected to the Android application.

1. Create a Firebase project using the [Firebase Console](https://console.firebase.google.com/).

2. Add an Android app using the package name:

   `com.example.opsc6312_poe`

3. Download the `google-services.json` file and place it inside:

   `app/google-services.json`

4. Open **Firebase Authentication** and enable **Email/Password** sign-in.

5. Create a **Cloud Firestore** database.

6. Open the **Rules** section in Firestore and use the rules provided in `FIREBASE_SETUP.md`.

7. Sync the project in Android Studio.

The `google-services.json` file is ignored by Git and should not be uploaded to GitHub.

## Running the Project

To run SafeRoute SA:

1. Open the `OPSC6312POE` folder in Android Studio.
2. Complete the Firebase setup above.
3. Select **File → Sync Project with Gradle Files**.
4. Connect an Android phone with USB debugging enabled, or use an Android emulator such as BlueStacks.
5. Select the device.
6. Click **Run** in Android Studio.

The assessment requires the prototype to compile and run before the features can be assessed.

## Building the Debug APK

A debug APK can be created using:

```powershell
.\gradlew.bat assembleDebug
```

The APK will be created at:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Testing

The unit tests can be run using:

```powershell
.\gradlew.bat testDebugUnitTest
```

The main areas tested include the validation rules used by the application.

Manual testing should also be done on the actual app. This includes checking:

* Invalid registration details
* Successful registration
* Successful login
* Updating settings
* Saving an emergency contact
* Submitting an incident report
* Confirming an SOS
* Cancelling an SOS
* Searching for police stations
* Checking that records are saved correctly in Firestore

## GitHub and GitHub Actions

The project includes a GitHub Actions workflow:

```text
.github/workflows/android.yml
```

The workflow is used to automatically test and build the Android project when changes are pushed to GitHub.

The workflow:

1. Sets up Java 17.
2. Runs the JUnit tests.
3. Builds the debug APK.
4. Uploads the APK as a workflow artifact.

After pushing the project to GitHub, the **Actions** tab can be used to check whether the build completed successfully.

A screenshot of the successful workflow can be added here:

```text
docs/images/github-actions.png
```

The OPSC6312 brief specifically requires the project to use GitHub and GitHub Actions for automated testing and building.

## Part 2 Scope and Future Work

The current version of SafeRoute SA is focused on the requirements for **Part 2**. The aim was to get the main application working first, including authentication, settings, online data storage, REST API integration, testing and GitHub Actions.

Some features are left for the final PoE because they are specifically identified as PoE-only requirements in the assessment brief.

These include:

* Single sign-on (SSO)
* Offline mode with synchronisation
* Real-time push notifications
* Support for at least two South African languages
* GPS/location functionality
* Real location sharing
* Additional Safety Check functionality
* Other improvements based on feedback from Part 2

The final PoE also requires the app to be prepared for publication on the Google Play Store and to include the additional PoE features.

## Conclusion

SafeRoute SA is currently a working safety-focused Android prototype. The main features have been built around the original idea of giving users quick access to useful safety tools from one application.

Part 2 focuses on getting the core application working correctly. The prototype uses Firebase for authentication and data storage, Retrofit for the REST API connection, and GitHub Actions for automated testing and building. Further features can be added during the final PoE as the application is developed and improved.




