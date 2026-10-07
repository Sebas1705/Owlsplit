
plugins {
    alias(libs.plugins.kmp.app)
}


// ── Environment profiles (ADR-0018) ──────────────────────────────────────────
// KMP has no blessed mechanism for this: Android's buildConfigField does not
// reach iOS, Desktop or Web. So the active profile is resolved here and
// generated into commonMain as one typed object every target reads — no
// per-platform source files, and therefore nothing coupled to which targets
// happen to be switched on.
val templetryProfiles = mapOf(
    //             apiBaseUrl                      verbose  cacheSeconds
    "development" to Triple("http://10.0.2.2:8080", true, 0),
    "staging" to Triple("https://staging.example.com", true, 30),
    "production" to Triple("https://api.example.com", false, 300),
)

val appEnvironment: String =
    (project.findProperty("appEnv") as String?)
        ?: System.getenv("APP_ENV")
        ?: "development"

require(appEnvironment in templetryProfiles) {
    "Unknown appEnv \"$appEnvironment\" — want one of ${templetryProfiles.keys}"
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
            |/**
            | * The active environment profile (ADR-0018).
            | *
            | * Generated at build time from the `appEnv` Gradle property, or the
            | * APP_ENV environment variable, defaulting to development:
            | *
            | *     ./gradlew assembleDebug -PappEnv=staging
            | *
            | * Do not edit by hand — it is rewritten on every build.
            | */
            |object AppConfig {
            |    const val ENVIRONMENT: String = "$environment"
            |    const val API_BASE_URL: String = "${profile.first}"
            |    const val VERBOSE_LOGGING: Boolean = ${profile.second}
            |    const val CACHE_SECONDS: Int = ${profile.third}
            |}
            |
            """.trimMargin()
        )
    }
}
kotlin {
    sourceSets {
        // The generated AppConfig lands here. Passing the task provider (not a
        // path) lets Gradle infer the dependency, so no compile task can run
        // before the file exists.
        commonMain {
            kotlin.srcDir(generateAppConfig)
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.animation)
            implementation(compose.components.resources)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.serialization.json)

            // Koin
            implementation(libs.koin.core)
            implementation(libs.bundles.koin.compose.bundle)

            // Core
            implementation(project(":core:common"))
            implementation(project(":core:ui"))
            implementation(project(":core:security"))
            implementation(project(":core:network"))
            implementation(project(":core:datastore"))
            implementation(project(":core:logging"))

            // Data
            implementation(project(":data:models"))
            implementation(project(":data:local"))
            implementation(project(":data:network"))
            implementation(project(":data:repository"))

            // Domain
            implementation(project(":domain:usecases"))

            // Features
            implementation(project(":feature:auth"))
            implementation(project(":feature:home"))
            implementation(project(":feature:settings"))
            implementation(project(":feature:profile"))
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

