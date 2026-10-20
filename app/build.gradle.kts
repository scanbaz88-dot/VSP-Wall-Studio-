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
        versionCode = 20
        versionName = "3.3.0"
    }
    // Build-time public endpoint ONLY; never bundle service account keys.
    // Set VSP_PREMIUM_VERIFY_URL in private production CI once the backend
    // has been deployed and Play closed-test purchases pass verification.
    val verifierUrl = providers.environmentVariable("VSP_PREMIUM_VERIFY_URL")
        .orElse("").get()
    require(verifierUrl.isEmpty() || verifierUrl.startsWith("https://")) {
        "Premium verifier URL must be HTTPS"
    }
    defaultConfig {
        buildConfigField("String","PREMIUM_VERIFY_URL",
            "\""+verifierUrl.replace("\\","\\\\").replace("\"","\\\"")+"\"")
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
    buildFeatures { buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    // Read-only Billing readiness; checkout remains disabled until trusted
    // Play Developer API verification and backend acknowledgment exist.
    implementation("com.android.billingclient:billing:9.1.0")
    testImplementation("junit:junit:4.13.2")
}
