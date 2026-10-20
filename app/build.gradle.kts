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
        versionCode = 17
        versionName = "3.0.0"
    }
    // Shared, PUBLIC development-only debug certificate. Never use for Play releases.
    // The preview package has its own identity so existing test installations remain untouched.
    signingConfigs {
        getByName("debug") {
            storeFile = file("preview-test-only.jks")
            storePassword = "androidtest"
            keyAlias = "VspWallStudioPreview"
            keyPassword = "androidtest"
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-test"
            signingConfig = signingConfigs.getByName("debug")
        }
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
