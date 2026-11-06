plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.opsc6312_poe"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.opsc6312_poe"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseStorePath = System.getenv("SAFEROUTE_KEYSTORE_PATH")
    val releaseKeyAliasValue = System.getenv("SAFEROUTE_KEY_ALIAS")
    val releaseStorePasswordValue = System.getenv("SAFEROUTE_KEYSTORE_PASSWORD")
    val releaseKeyPasswordValue = System.getenv("SAFEROUTE_KEY_PASSWORD")
    val releaseSigningAvailable = listOf(releaseStorePath, releaseKeyAliasValue, releaseStorePasswordValue, releaseKeyPasswordValue).all { !it.isNullOrBlank() }
    if (releaseSigningAvailable) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStorePath!!)
                keyAlias = releaseKeyAliasValue
                storePassword = releaseStorePasswordValue
                keyPassword = releaseKeyPasswordValue
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningAvailable) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.runtime.saveable)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// This keeps the starter project buildable before the user adds their private
// Firebase configuration file. See FIREBASE_SETUP.md.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
