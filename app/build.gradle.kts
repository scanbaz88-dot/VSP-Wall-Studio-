plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vintagesoulprint.wallmuse"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vintagesoulprint.wallmuse"
        minSdk = 29
        targetSdk = 36
        versionCode = 6
        versionName = "2.2.1"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // Release signing is configured locally in Android Studio; never commit a signing key.
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = false }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
