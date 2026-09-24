// Type-checks the Android app's Kotlin WITHOUT the Android SDK (for sandboxes where Google's
// repositories are unreachable): compiles app/src/main/kotlin against Robolectric's full
// Android framework jar from Maven Central. It does not build an APK and ships nothing.
//   gradle -Pff.coreOnly=true -Pff.verifyAndroid=true :verify:compileKotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { id("org.jetbrains.kotlin.jvm") }

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17); allWarningsAsErrors.set(false) }
    sourceSets["main"].kotlin.srcDir("../app/src/main/kotlin")
}

dependencies {
    implementation(project(":core"))
    // Android 15 (API 35) framework classes. `-Pff.androidJar=/path/android-all.jar` uses a local copy instead.
    val localJar = providers.gradleProperty("ff.androidJar").orNull
    if (localJar != null) compileOnly(files(localJar)) else compileOnly("org.robolectric:android-all:15-robolectric-12650502")

    // Compile-check (only) the app's Robolectric tests; they need androidx.test from Google's
    // repository to *run*, which the Android build provides (:app:testDebugUnitTest).
    testImplementation("org.robolectric:robolectric:4.14.1") { exclude(group = "androidx.test"); exclude(group = "androidx.test.espresso") }
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test-junit"))
    if (localJar != null) testCompileOnly(files(localJar)) else testCompileOnly("org.robolectric:android-all:15-robolectric-12650502")
}

kotlin { sourceSets["test"].kotlin.srcDir("../app/src/test/kotlin") }

// Running them here would need a stand-in for androidx.test; we don't fake test infrastructure.
tasks.test { enabled = false }
