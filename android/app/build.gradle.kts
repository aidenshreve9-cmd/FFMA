import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.focusfriend"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.focusfriend"
        minSdk = 29          // Android 10: AutomaticZenRule + ZenPolicy
        targetSdk = 35
        versionCode = 30
        versionName = "3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
    sourceSets["test"].java.srcDirs("src/test/kotlin")
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

// Deliberately no AndroidX, Compose, analytics, ads or network libraries:
// the app uses only the Android framework plus the shared :core module.
dependencies {
    implementation(project(":core"))

    // Test-only: Robolectric runs the Do Not Disturb layer on the JVM (./gradlew :app:testDebugUnitTest).
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test-junit"))
}
