plugins {
    alias(libs.plugins.kotlin.multiplatform)  apply false
    alias(libs.plugins.kotlin.serialization)  apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler)      apply false
    alias(libs.plugins.android.application)   apply false
    alias(libs.plugins.android.library)       apply false
    alias(libs.plugins.sqldelight)            apply false
    alias(libs.plugins.google.services)       apply false
    alias(libs.plugins.firebase.crashlytics)  apply false
    alias(libs.plugins.roborazzi)             apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

// Code coverage (Kotlinx Kover). Each library module applies Kover through the KMP convention plugin;
// the root project merges them into one report. Modules with no production code to measure are left out:
// :core:testing (test helpers) and :composeApp (app wiring, DI modules and platform UI, no unit tests).
// The modules only have an Android target, so unit tests run on the `debug` build variant; each module exposes
// it as the `unitTests` report variant (KmpLibraryConventionPlugin) and the root merges them:
//   ./gradlew koverXmlReportUnitTests koverHtmlReportUnitTests koverVerifyUnitTests
// The un-suffixed koverXmlReport / koverVerify tasks also work but compile the release variant too.
dependencies {
    kover(projects.core.common)
    kover(projects.core.ui)
    kover(projects.core.security)
    kover(projects.core.network)
    kover(projects.core.datastore)
    kover(projects.core.logging)
    kover(projects.core.money)
    kover(projects.data.models)
    kover(projects.data.local)
    kover(projects.data.remote)
    kover(projects.data.repository)
    kover(projects.data.fx)
    kover(projects.domain.usecases)
    kover(projects.feature.onboarding)
    kover(projects.feature.profile)
    kover(projects.feature.home)
    kover(projects.feature.groups)
    kover(projects.feature.expense)
    kover(projects.feature.history)
    kover(projects.feature.invite)
}

kover {
    currentProject {
        // The root has no code of its own; this empty variant merges the modules' `unitTests` variants
        // so the koverXmlReportUnitTests / koverHtmlReportUnitTests / koverVerifyUnitTests tasks exist here.
        createVariant("unitTests") {}
    }
    reports {
        filters {
            excludes {
                // SQLDelight generated database, queries and adapters.
                packages("es.sebas1705.owlsplit.data.local.db")
                // Compose Multiplatform generated resource accessors (Res, Drawable0_commonMainKt, ...).
                classes("es.sebas1705.owlsplit.*.resources.*")
                // Android generated classes and Compose compiler synthetics.
                classes("*.BuildConfig", "*ComposableSingletons*", "*\$\$serializer")
                // Koin DI modules and Compose previews.
                classes("*.di.*", "*Module", "*ModuleKt")
                annotatedBy(
                    "androidx.compose.ui.tooling.preview.Preview",
                    "org.jetbrains.compose.ui.tooling.preview.Preview",
                )
            }
        }
        verify {
            rule("Minimum line coverage") {
                // Current coverage rounded down to the nearest 5 %; raise it when coverage grows, never lower it.
                minBound(75)
            }
        }
    }
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = true
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

// Detekt 1.23.x is compiled against Kotlin 2.0.21; Gradle would otherwise align its
// Kotlin dependencies with the project's Kotlin and Detekt fails at startup.
val detektKotlinVersion: String = libs.versions.detekt.kotlin.get()
allprojects {
    configurations.matching { it.name == "detekt" || it.name == "detektPlugins" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion(detektKotlinVersion)
        }
    }
}
