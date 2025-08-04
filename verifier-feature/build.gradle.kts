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
    implementation(project(LibraryModule.CoreLogic.path))
    implementation(project(LibraryModule.DashboardFeature.path))
    implementation(libs.eudi.lib.jvm.siop.openid4vp.kt)
    implementation(libs.eudi.lib.jvm.sdjwt.kt)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json.v151)
    implementation(libs.retrofit.v290)
    implementation(libs.retrofit2.kotlinx.serialization.converter)

}

excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.VerifierFeature.classes,
    excludedPackages = KoverExclusionRules.VerifierFeature.packages,
)