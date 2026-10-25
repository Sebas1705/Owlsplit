import ext.configureAndroidLibrary
import ext.configureKmp
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

/**
 * Base KMP library convention.
 * Applies: kotlin.multiplatform, com.android.library, kotlin.serialization, detekt, kover.
 * Configures: Android + Desktop + iOS targets, jvmTarget = 17, minSdk = 24.
 *
 * Use this on every non-app module. Specialised conventions (feature, data, etc.)
 * build on top by applying additional plugins or adding dependencies.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    companion object {
        /** Name of the Kover report variant (debug unit tests) that the root project merges. */
        const val KOVER_VARIANT = "unitTests"
    }

    override fun apply(target: Project) = with(target) {
        apply(plugin = "org.jetbrains.kotlin.multiplatform")
        apply(plugin = "com.android.library")
        apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
        apply(plugin = "owlsplit.detekt")
        // Every library module collects coverage; the root project merges the reports (root build.gradle.kts).
        apply(plugin = "org.jetbrains.kotlinx.kover")
        configure<KoverProjectExtension> {
            currentProject {
                // Unit tests run once, on the debug variant (CI runs testDebugUnitTest); the release
                // variant adds nothing to coverage and must not be triggered by the Kover report tasks.
                instrumentation.disabledForTestTasks.add("testReleaseUnitTest")
                // Report variant merged by the root project: koverXmlReportUnitTests etc.
                createVariant(KOVER_VARIANT) { add("debug") }
            }
        }

        val namespace = "es.sebas1705.owlsplit" +
            project.path.replace(":", ".").let { if (it.startsWith(".")) it else ".$it" }
        configureAndroidLibrary(namespace)
        configureKmp()
    }
}
