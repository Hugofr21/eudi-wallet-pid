import project.convention.logic.config.LibraryModule
import project.convention.logic.kover.KoverExclusionRules
import project.convention.logic.kover.excludeFromKoverReport


plugins {
    id("project.android.feature")
}

android {
    namespace = "eu.europa.ec.mrzscannerLogic"
}


moduleConfig {
    module = LibraryModule.MrzScannerLogic
}



dependencies {
    implementation(project(LibraryModule.ResourcesLogic.path))
    implementation(libs.text.recognition)
    implementation(libs.androidx.camera.camera2.v130)
    implementation(libs.androidx.camera.lifecycle.v130)
    implementation(libs.androidx.camera.view.v130)
    implementation(libs.androidx.monitor)
    implementation(libs.face.detection)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta4")

}

excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.MrzScannerLogic.classes,
    excludedPackages = KoverExclusionRules.MrzScannerLogic.packages,
)