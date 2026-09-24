// Focus Friend — native Android.
//   :core  pure Kotlin (search, session clock, durations, contacts, policy) — JVM-tested
//   :app   Android app using only the Android framework (no AndroidX, no Compose)
pluginManagement {
    repositories {
        google {
            content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx.*"); includeGroupByRegex("com\\.google.*") }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
    }
}
dependencyResolutionManagement {
    repositories {
        google {
            content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("androidx.*"); includeGroupByRegex("com\\.google.*") }
        }
        mavenCentral()
    }
}
rootProject.name = "FocusFriend"
include(":core")
// `-Pff.coreOnly=true` builds and tests :core without the Android SDK (used in CI-less sandboxes).
if (providers.gradleProperty("ff.coreOnly").orNull != "true") include(":app")
// `-Pff.verifyAndroid=true` type-checks the app's Kotlin against Robolectric's Android framework jar (no SDK needed).
if (providers.gradleProperty("ff.verifyAndroid").orNull == "true") include(":verify")
