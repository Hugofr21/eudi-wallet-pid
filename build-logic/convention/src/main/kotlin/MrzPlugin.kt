import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import project.convention.logic.libs


class MrzPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {

            configurations.configureEach {
                resolutionStrategy {
                    force(
                        "com.google.android.datatransport:transport-api:3.2.0",
                        "com.google.android.datatransport:transport-backend-cct:3.3.0",
                        "com.google.android.datatransport:transport-runtime:3.3.0"
                    )
                }
            }

            dependencies {
                add("implementation", libs.findLibrary("text-recognition").get())
                add("implementation", libs.findLibrary("androidx-camera-camera2-v130").get())
                add("implementation", libs.findLibrary("androidx-camera-lifecycle-v130").get())
                add("implementation", libs.findLibrary("androidx-camera-view-v130").get())
                add("implementation", libs.findLibrary("androidx-monitor").get())
                add("implementation", libs.findLibrary("face-detection").get())
                add("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
                add("implementation", "com.google.mlkit:segmentation-selfie:16.0.0-beta4")
                add("implementation", libs.findLibrary("androidx-ui-graphics").get())
            }
        }
    }
}