package eu.europa.ec.mrzscannerLogic.model

object LivenessThresholds {
    /** Minimum yaw delta (°) to accept a left/right head-turn challenge. */
    const val YAW_THRESHOLD_DEG = 20f

    /** Minimum pitch delta (°) to accept a nod challenge. */
    const val PITCH_THRESHOLD_DEG = 15f

    /** EAR value below which the eye is considered closed. */
    const val EAR_BLINK_THRESHOLD = 0.22f

    /** Number of consecutive frames eye must be closed to count as a blink. */
    const val BLINK_CONSEC_FRAMES = 2

    /** ML Kit backup: eye-open probability below this → eye is closed. */
    const val EYE_PROB_CLOSED = 0.15f

    /** MAR value above which the mouth is considered open (smile/open mouth). */
    const val MAR_OPEN_THRESHOLD = 0.25f

    /** Timeout per challenge in milliseconds. */
    const val CHALLENGE_TIMEOUT_MS = 6_000L

    /** Minimum asymmetry score accepted as a real face (rejects flat prints). */
    const val MIN_ASYMMETRY_SCORE = 0.60f

    const val SMILE_PROB_THRESHOLD = 0.75f
}