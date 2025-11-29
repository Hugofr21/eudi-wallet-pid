import project.convention.logic.config.LibraryModule
import project.convention.logic.kover.KoverExclusionRules
import project.convention.logic.kover.excludeFromKoverReport


plugins {
    id("project.android.library")
    id("project.wallet.core")
}

android {
    namespace = "eu.europa.ec.mrzscannerLogic"
}


moduleConfig {
    module = LibraryModule.MrzScannerLogic
}



dependencies {

}

excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.MrzScannerLogic.classes,
    excludedPackages = KoverExclusionRules.MrzScannerLogic.packages,
)