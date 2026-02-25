package eu.europa.ec.mrzscannerLogic.model

data class FaceGeometry(
    /** Euler yaw angle in degrees (positive = face turned right). */
    val yawDeg: Float,
    /** Euler pitch angle in degrees (positive = face tilted up). */
    val pitchDeg: Float,
    /** Euler roll angle in degrees. */
    val rollDeg: Float,
    /** Eye Aspect Ratio for left eye (0.0 closed → ~0.35 open). */
    val earLeft: Float,
    /** Eye Aspect Ratio for right eye. */
    val earRight: Float,
    /** Mouth Aspect Ratio (0.0 closed → >0.5 wide open). */
    val mar: Float,
    /**
     * ML Kit probabilistic backup: probability that left eye is open [0..1].
     * Used as a secondary signal alongside EAR.
     */
    val leftEyeOpenProb: Float,
    /**
     * ML Kit probabilistic backup: probability that right eye is open [0..1].
     */
    val rightEyeOpenProb: Float,
    /** Confidence score of the facial asymmetry index [0..1, 1=perfectly symmetric]. */
    val asymmetryScore: Float
)
