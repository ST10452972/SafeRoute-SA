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

Youtube video link
https://youtu.be/CY-z236RZow?si=iRXX8ymZza2XqhgT 

