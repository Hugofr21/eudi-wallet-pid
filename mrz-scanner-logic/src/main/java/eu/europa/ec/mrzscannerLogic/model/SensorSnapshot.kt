package eu.europa.ec.mrzscannerLogic.model

/**
 *
 * Immutable snapshot of sensor data at a given instant.
 * Passed to the analyzer in each frame that requires anti-spoofing.
 *
 **/

data class SensorSnapshot(
    /** 3x3 rotation matrix (longest row, 9 elements) derived from ROTATION_VECTOR. */
    val rotationMatrix: FloatArray? = null,
    /** Linear acceleration in the 3 axes [x, y, z] in m/s² (TYPE_ACCELEROMETER). */
    val accelerometerData: FloatArray? = null,
    /** Timestamp of the last update in milliseconds. */
    val timestampMs: Long = 0L
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SensorSnapshot) return false
        return timestampMs == other.timestampMs &&
                rotationMatrix.contentEquals(other.rotationMatrix) &&
                accelerometerData.contentEquals(other.accelerometerData)
    }

    override fun hashCode(): Int {
        var result = rotationMatrix?.contentHashCode() ?: 0
        result = 31 * result + (accelerometerData?.contentHashCode() ?: 0)
        result = 31 * result + timestampMs.hashCode()
        return result
    }
}

private fun FloatArray?.contentEquals(other: FloatArray?): Boolean {
    if (this == null && other == null) return true
    if (this == null || other == null) return false
    return this.contentEquals(other)
}

private fun FloatArray?.contentHashCode(): Int = this?.contentHashCode() ?: 0