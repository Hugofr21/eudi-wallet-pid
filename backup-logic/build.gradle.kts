import project.convention.logic.config.LibraryModule
import project.convention.logic.kover.KoverExclusionRules
import project.convention.logic.kover.excludeFromKoverReport

plugins {
    id("project.android.library")
    id("project.android.feature")
}

android {
    namespace = "eu.europa.ec.backuplogic"
}

moduleConfig {
    module = LibraryModule.BackupLogic
}

dependencies {
    implementation(project(LibraryModule.BusinessLogic.path))
    implementation(project(LibraryModule.StorageLogic.path))
    implementation(libs.androidx.material.icons.extended)
}

// Current Module Kover Report
excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.BackupLogic.classes,
    excludedPackages = KoverExclusionRules.BackupLogic.packages,
)