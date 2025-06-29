import project.convention.logic.config.LibraryModule
import project.convention.logic.kover.KoverExclusionRules
import project.convention.logic.kover.excludeFromKoverReport

plugins {
    id("project.android.library")
    id("project.android.feature")
}

android {
    namespace = "eu.europa.ec.eudi.constent_user"
}



dependencies {

//    implementation(libs.androidx.core.ktx)
//    implementation(libs.androidx.appcompat)
//    implementation(libs.material)
//    testImplementation(libs.junit4)
//    androidTestImplementation(libs.androidx.test.orchestrator)
//    androidTestImplementation(libs.androidx.test.espresso.core)

    implementation(project(LibraryModule.BusinessLogic.path))
    implementation(project(LibraryModule.CoreLogic.path))
    implementation(project(LibraryModule.UiLogic.path))
    implementation(project(LibraryModule.ResourcesLogic.path))

}

excludeFromKoverReport (
    excludedClasses = KoverExclusionRules.UserConsent.classes,
    excludedPackages = KoverExclusionRules.UserConsent.packages,
)