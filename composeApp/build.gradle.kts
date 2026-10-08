plugins {
    alias(libs.plugins.kmp.app)
}

// ── Environment profiles ─────────────────────────────────────────────────────
// The active profile is resolved here and generated into commonMain as one typed
// object every target reads. Each environment has its own Firebase project.
data class EnvProfile(
    val suffix: String,
    val fxApiBaseUrl: String,
    val verbose: Boolean,
    val fxCacheSeconds: Int,
    /** Firebase Hosting site that serves the invite links (`/j/<code>`). */
    val inviteBaseUrl: String,
    /** Crash reports to Crashlytics; off in development so local crashes stay local. */
    val crashReporting: Boolean,
)

val templetryProfiles = mapOf(
    "development" to EnvProfile(".dev", "https://api.frankfurter.dev/v1", true, 0, "https://owlsplit-dev.web.app", false),
    "staging" to EnvProfile(".stg", "https://api.frankfurter.dev/v1", true, 43_200, "https://owlsplit-stg.web.app", true),
    "production" to EnvProfile("", "https://api.frankfurter.dev/v1", false, 43_200, "https://owlsplit.web.app", true),
)

val appEnvironment: String =
    (project.findProperty("appEnv") as String?)
        ?: System.getenv("APP_ENV")
        ?: "development"

require(appEnvironment in templetryProfiles) {
    "Unknown appEnv \"$appEnvironment\" — want one of ${templetryProfiles.keys}"
}
val profile = templetryProfiles.getValue(appEnvironment)

// ── Release builds (CI) ──────────────────────────────────────────────────────
// The release workflow passes these as environment variables; locally they are absent and a
// release build is signed with the debug key, versionCode 1.
//  - VERSION_CODE: the GitHub run number, so every distributed build installs over the last one.
//  - VERSION_NAME: the tag's version (vX.Y.Z -> X.Y.Z).
//  - SIGNING_*: the fixed release keystore, decoded from Doppler (prd).
val ciVersionCode: Int? = System.getenv("VERSION_CODE")?.toIntOrNull()
val ciVersionName: String? = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
val ciKeystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }

android {
    defaultConfig {
        // Development and staging install side by side with production.
        applicationIdSuffix = profile.suffix.ifEmpty { null }
        // App Links: the invite host of this environment opens `/j/<code>` in the app.
        manifestPlaceholders["inviteHost"] = profile.inviteBaseUrl.substringAfter("://")
        manifestPlaceholders["crashlyticsEnabled"] = profile.crashReporting.toString()
        ciVersionCode?.let { versionCode = it }
        ciVersionName?.let { versionName = it }
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
        getByName("release") {
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
    // Mappings matter only where crashes are reported (staging, production).
    tasks.matching { it.name.startsWith("uploadCrashlyticsMappingFile") }.configureEach {
        enabled = profile.crashReporting
    }
} else {
    logger.warn("composeApp/google-services.json missing: building without Firebase config")
}

val generateAppConfig = tasks.register("generateAppConfig") {
    val environment = appEnvironment
    val fxApiBaseUrl = profile.fxApiBaseUrl
    val verbose = profile.verbose
    val fxCacheSeconds = profile.fxCacheSeconds
    val inviteBaseUrl = profile.inviteBaseUrl
    val crashReporting = profile.crashReporting
    val outputDir = layout.buildDirectory.dir("generated/templetry/commonMain/kotlin")

    inputs.property("environment", environment)
    inputs.property("profile", profile.toString())
    outputs.dir(outputDir)

    doLast {
        val packageDir = outputDir.get().asFile.resolve("es/sebas1705/owlsplit/config")
        packageDir.mkdirs()
        packageDir.resolve("AppConfig.kt").writeText(
            """
            |package es.sebas1705.owlsplit.config
            |
            |/** The active environment profile. Generated at build time — do not edit. */
            |object AppConfig {
            |    const val ENVIRONMENT: String = "$environment"
            |    const val FX_API_BASE_URL: String = "$fxApiBaseUrl"
            |    const val VERBOSE_LOGGING: Boolean = $verbose
            |    const val FX_CACHE_SECONDS: Int = $fxCacheSeconds
            |    const val INVITE_BASE_URL: String = "$inviteBaseUrl"
            |    const val CRASH_REPORTING: Boolean = $crashReporting
            |}
            |
            """.trimMargin()
        )
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateAppConfig)
        }
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
}

compose.resources {
    packageOfResClass = "es.sebas1705.owlsplit.resources"
    publicResClass = false
}
