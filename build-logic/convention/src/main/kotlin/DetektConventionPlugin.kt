import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "io.gitlab.arturbosch.detekt")
        configure<DetektExtension> {
            // KMP keeps sources under src/<sourceSet>/kotlin; Detekt's default (src/main/kotlin) finds nothing.
            source.setFrom(
                file("src").listFiles()
                    ?.map { it.resolve("kotlin") }
                    ?.filter { it.isDirectory }
                    .orEmpty(),
            )
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            buildUponDefaultConfig = true
            autoCorrect = true
            parallel    = true
        }
        // Detekt 1.23 takes the JVM it runs on as the target by default and only knows up to 22;
        // the Gradle daemon runs on JDK 25 (gradle-daemon-jvm.properties). The code targets 17.
        tasks.withType<Detekt>().configureEach { jvmTarget = "17" }
        dependencies {
            "detektPlugins"("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
        }
    }
}
