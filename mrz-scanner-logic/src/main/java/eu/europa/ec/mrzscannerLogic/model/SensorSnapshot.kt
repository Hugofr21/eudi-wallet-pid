package eu.europa.ec.mrzscannerLogic.model

/**
 * Immutable snapshot of sensor data at a given instant.
 * Passed to the analyzer in each frame that requires anti-spoofing.
 */

data class SensorSnapshot(
    val rotationMatrix: FloatArray? = null,
    val accelerometerData: FloatArray? = null,
    val timestampMs: Long = 0L
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SensorSnapshot) return false

        if (timestampMs != other.timestampMs) return false

        if (rotationMatrix != null) {
            if (other.rotationMatrix == null || !rotationMatrix.contentEquals(other.rotationMatrix)) return false
        } else if (other.rotationMatrix != null) {
            return false
        }

        if (accelerometerData != null) {
            if (other.accelerometerData == null || !accelerometerData.contentEquals(other.accelerometerData)) return false
        } else if (other.accelerometerData != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = rotationMatrix?.contentHashCode() ?: 0
        result = 31 * result + (accelerometerData?.contentHashCode() ?: 0)
        result = 31 * result + timestampMs.hashCode()
        return result
    }
}