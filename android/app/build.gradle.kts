plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.focusfriend.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.focusfriend.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // The app shows the same page as the web prototype, so there's one copy of the UI to maintain.
    sourceSets {
        getByName("main") {
            assets.srcDir("../../prototype")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.12.1")
}
