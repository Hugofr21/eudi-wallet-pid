import project.convention.logic.config.LibraryModule
import project.convention.logic.kover.KoverExclusionRules
import project.convention.logic.kover.excludeFromKoverReport

plugins {
    id("project.android.library")
    id("project.android.feature")
}

android {
    namespace = "eu.europa.ec.consentuser"
}

moduleConfig {
    module = LibraryModule.UserConsentFeature
}


dependencies {
    implementation(project(LibraryModule.BusinessLogic.path))
    implementation(project(LibraryModule.CoreLogic.path))
    implementation(project(LibraryModule.UiLogic.path))
    implementation(project(LibraryModule.ResourcesLogic.path))
    implementation(project(LibraryModule.BackupLogic.path))
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.documentfile)

}

excludeFromKoverReport (
    excludedClasses = KoverExclusionRules.UserConsent.classes,
    excludedPackages = KoverExclusionRules.UserConsent.packages,
)