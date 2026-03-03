package eu.europa.ec.mrzscannerLogic.model

enum class AntiSpoofingCheck {

    SPECULAR_REFLECTION, // Specular reflection → distinguishes plastic from paper

    MOIRE_PATTERN, // 2D FFT → detects digital screens

    IMAGE_QUALITY, // Blur + Exposure

    GYROSCOPE, // OVI Hologram + angular variation

    ACCELEROMETER, // Material stiffness

    ARCORE_DEPTH, // 3D depth (ARCore)

    EDGE_SHARPNESS, // NEW: sharpness of card edges

    COLOR_CONSISTENCY, // NEW: color uniformity (paper vs polycarbonate)

    TEMPORAL_CONSISTENCY, // NEW: frame consistency (detects still images)

    PRINT_ARTIFACT, // NEW: CMYK printer artifacts

}

data class CheckResult(
    val check: AntiSpoofingCheck,
    val passed: Boolean,
    val score: Float,
    val reason: String? = null

)

data class AntiSpoofingReport(
    val isReal: Boolean,
    val overallScore: Float,
    val checks: List<CheckResult>,
    val verdict: AttackType = AttackType.UNKNOWN
) {

    /** Diagnosis of the most likely type of attack */
    enum class AttackType {
        REAL_CARD, // Genuine physical card
        PRINTED_COPY, // Photocopy / laser or inkjet print
        SCREEN_DISPLAY, // Screen (tablet, cell phone, monitor)
        UNKNOWN // Inconclusive
    }
}