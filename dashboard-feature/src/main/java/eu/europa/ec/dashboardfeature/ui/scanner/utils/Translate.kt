package eu.europa.ec.dashboardfeature.ui.scanner.utils

import eu.europa.ec.mrzscannerLogic.controller.MrzScanState
import eu.europa.ec.mrzscannerLogic.model.AntiSpoofingCheck

object Translate {
    fun translateSecurity(state: MrzScanState.SecurityCheckFailed): String {
        val score = Regex("Score: ([0-9.]+)").find(state.reason)?.groups?.get(1)?.value ?: "0.0"
        if (state.failedChecks.isEmpty()) return "Security validation failed. Score: $score"
        val reason = when (state.failedChecks.first()) {
            AntiSpoofingCheck.MOIRE_PATTERN -> "Digital screen detected. Use the physical document."
            AntiSpoofingCheck.SPECULAR_REFLECTION -> "Material does not appear to be genuine plastic."
            AntiSpoofingCheck.GYROSCOPE -> "Tilt the phone slightly to validate the hologram."
            AntiSpoofingCheck.ACCELEROMETER -> "Keep the document steady and avoid flexible surfaces."
            AntiSpoofingCheck.IMAGE_QUALITY -> "Improve ambient lighting."
            AntiSpoofingCheck.ARCORE_DEPTH -> "Move the camera to validate depth."
            AntiSpoofingCheck.EDGE_SHARPNESS -> "Blurred document. Move the camera closer."
            AntiSpoofingCheck.COLOR_CONSISTENCY -> "Inconsistent colors detected."
            AntiSpoofingCheck.TEMPORAL_CONSISTENCY -> "Movement detected. Keep the document stable."
            AntiSpoofingCheck.PRINT_ARTIFACT -> "Print artifact detected."
            AntiSpoofingCheck.HOMOGRAPHY_PLANARITY -> "Printed copy detected. Use the physical document."

        }
        return "$reason\nSecurity score: $score"
    }
}