import project.convention.logic.config.LibraryModule
import project.convention.logic.kover.KoverExclusionRules
import project.convention.logic.kover.excludeFromKoverReport


plugins {
    id("project.android.feature")
}

android {
    namespace = "eu.europa.ec.verifierfeature"
}


moduleConfig {
    module = LibraryModule.VerifierFeature
}

dependencies {
    implementation(libs.eudi.lib.jvm.siop.openid4vp.kt)

}

excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.VerifierFeature.classes,
    excludedPackages = KoverExclusionRules.VerifierFeature.packages,
)