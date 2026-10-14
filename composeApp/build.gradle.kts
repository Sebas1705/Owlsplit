plugins {
    alias(libs.plugins.kmp.app)
}

// ── One Firebase project, two Android apps ───────────────────────────────────
// Everything runs on the `owlsplit` project (docs/FIREBASE.md). Release builds are the app
// testers and users get (es.sebas1705.owlsplit); debug builds are a second app in the same
// project (es.sebas1705.owlsplit.debug) so they install next to it with the debug key.
//
// Release builds in CI get, as environment variables:
//  - VERSION_CODE: the commit count of main, so every build installs over the previous one.
//  - VERSION_NAME: the tag's version (vX.Y.Z -> X.Y.Z) or the build name of a main build.
//  - SIGNING_*: the fixed release keystore, decoded from Doppler.
// Locally they are absent: versionCode 1 and a release build signed with the debug key.
val ciVersionCode: Int? = System.getenv("VERSION_CODE")?.toIntOrNull()
val ciVersionName: String? = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
val ciKeystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }

android {
    defaultConfig {
        // App Links: the Hosting site of the project opens `/j/<code>` in the app.
        manifestPlaceholders["inviteHost"] = "owlsplit.web.app"
        ciVersionCode?.let { versionCode = it }
        ciVersionName?.let { versionName = it }
    }
    buildFeatures {
        buildConfig = true
    }
    signingConfigs {
        if (ciKeystorePath != null) {
            create("release") {
                storeFile = file(ciKeystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            // Crash reports only from the builds people use; local crashes stay local.
            manifestPlaceholders["crashlyticsEnabled"] = "false"
        }
        getByName("release") {
            manifestPlaceholders["crashlyticsEnabled"] = "true"
            signingConfig = signingConfigs.getByName(if (ciKeystorePath != null) "release" else "debug")
        }
    }
}

// The Firebase config comes from Doppler (scripts/doppler-sync-local.sh) and is git-ignored.
// Without it the app still builds (CI, fresh clones) but Firebase is not initialised.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    // Injects the build id Crashlytics needs at startup and uploads the R8 mapping of release builds.
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
} else {
    logger.warn("composeApp/google-services.json missing: building without Firebase config")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.compose.core)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.koin.core)
            implementation(libs.bundles.koin.compose.bundle)

            implementation(project(":core:common"))
            implementation(project(":core:ui"))
            implementation(project(":core:security"))
            implementation(project(":core:network"))
            implementation(project(":core:datastore"))
            implementation(project(":core:logging"))
            implementation(project(":data:local"))
            implementation(project(":data:remote"))
            implementation(project(":domain:usecases"))
            implementation(project(":data:repository"))
            implementation(project(":data:fx"))
            implementation(project(":feature:onboarding"))
            implementation(project(":feature:profile"))
            implementation(project(":feature:home"))
            implementation(project(":feature:groups"))
            implementation(project(":feature:expense"))
            implementation(project(":feature:history"))
            implementation(project(":feature:invite"))
            implementation(libs.compose.icons.tabler)
            implementation(libs.gitlive.firebase.crashlytics)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
            implementation(libs.androidx.work.runtime)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play)
            implementation(libs.google.identity.googleid)
            implementation(libs.play.services.code.scanner)
        }
    }
}

dependencies {
    debugImplementation(libs.leakcanary)
    // App Check (docs/FIREBASE.md#app-check): each build type installs its provider from
    // src/androidDebug or src/androidRelease, so the debug provider never ships in a release.
    debugImplementation(libs.firebase.appcheck.debug)
    releaseImplementation(libs.firebase.appcheck.playintegrity)
}

compose.resources {
    packageOfResClass = "es.sebas1705.owlsplit.resources"
    publicResClass = false
}

// The Swift app imports the shared code as `ComposeApp` (iosApp/iosApp/*.swift).
kotlin {
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.withType<org.jetbrains.kotlin.gradle.plugin.mpp.Framework>().configureEach {
            baseName = "ComposeApp"
        }
    }
}
