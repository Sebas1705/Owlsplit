plugins {
    alias(libs.plugins.kmp.app)
}

// ── Environment profiles ─────────────────────────────────────────────────────
// The active profile is resolved here and generated into commonMain as one typed
// object every target reads.
val templetryProfiles = mapOf(
    //             fxApiBaseUrl                      verbose  cacheSeconds
    "development" to Triple("https://api.frankfurter.dev/v1", true, 0),
    "staging" to Triple("https://api.frankfurter.dev/v1", true, 43_200),
    "production" to Triple("https://api.frankfurter.dev/v1", false, 43_200),
)

val appEnvironment: String =
    (project.findProperty("appEnv") as String?)
        ?: System.getenv("APP_ENV")
        ?: "development"

require(appEnvironment in templetryProfiles) {
    "Unknown appEnv \"$appEnvironment\" — want one of ${templetryProfiles.keys}"
}

// One Firebase app per environment: development and staging install side by side
// with production thanks to the applicationId suffix.
val applicationIdSuffixByEnv = mapOf("development" to ".dev", "staging" to ".stg", "production" to "")

android {
    defaultConfig {
        applicationIdSuffix = applicationIdSuffixByEnv.getValue(appEnvironment).ifEmpty { null }
    }
}

// The Firebase config comes from Doppler (scripts/doppler-sync-local.sh) and is git-ignored.
// Without it the app still builds (CI, fresh clones) but Firebase is not initialised.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
} else {
    logger.warn("composeApp/google-services.json missing: building without Firebase config")
}

val generateAppConfig = tasks.register("generateAppConfig") {
    val environment = appEnvironment
    val profile = templetryProfiles.getValue(environment)
    val outputDir = layout.buildDirectory.dir("generated/templetry/commonMain/kotlin")

    inputs.property("environment", environment)
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
            |    const val FX_API_BASE_URL: String = "${profile.first}"
            |    const val VERBOSE_LOGGING: Boolean = ${profile.second}
            |    const val FX_CACHE_SECONDS: Int = ${profile.third}
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
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }
    }
}

dependencies {
    debugImplementation(libs.leakcanary)
}
