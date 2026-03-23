package eu.europa.ec.mrzscannerLogic.model

data class GuidelineAntiSpoofing(
    val threshold: Float = 0.65f,
    val checkSpecularReflection: Boolean = true,
    val checkMoirePattern: Boolean = true,
    val checkImageQuality: Boolean = true,
    val checkGyroscope: Boolean = true,
    val checkAccelerometer: Boolean = true,
    val checkArCoreDepth: Boolean = false,
    val checkEdgeSharpness: Boolean = true,
    val checkColorConsistency: Boolean = true,
    val checkTemporalConsistency: Boolean = true,
    val checkPrintArtifact: Boolean = true,
    val checkHomographyPlanarity: Boolean = true
)