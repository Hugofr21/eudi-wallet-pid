package eu.europa.ec.mrzscannerLogic.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.mrzscannerLogic.model.SensorSnapshot
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SensorDocumentService {
    val sensorFlow: StateFlow<SensorSnapshot>
    val actual: SensorSnapshot
    fun start(needsRotation: Boolean, needsAccel: Boolean)
    fun stop()
}

class SensorDocumentServiceImpl(
    private val log: LogController,
    private val resourceProvider: ResourceProvider,

    ) : SensorDocumentService, SensorEventListener {
    private val sensorManager: SensorManager by lazy {
        resourceProvider.provideContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val _sensorFlow = MutableStateFlow(SensorSnapshot())
    override val sensorFlow: StateFlow<SensorSnapshot> = _sensorFlow.asStateFlow()

    private val bufferRotationMatrix = FloatArray(9) { if (it % 4 == 0) 1f else 0f }
    private val bufferAccelerometer = FloatArray(3)
    @Volatile private var lastTimestampMs: Long = 0L
    private var isRunning = false

    override val actual: SensorSnapshot
        get() = SensorSnapshot(
            rotationMatrix = bufferRotationMatrix.clone(),
            accelerometerData = bufferAccelerometer.clone(),
            timestampMs = lastTimestampMs
        )

    override fun start(needsRotation: Boolean, needsAccel: Boolean) {
        if (isRunning) return
        isRunning = true

        if (needsRotation) {
            sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        if (needsAccel) {
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    override fun stop() {
        if (!isRunning) return
        sensorManager.unregisterListener(this)
        isRunning = false
        lastTimestampMs = 0L
    }


    override fun onSensorChanged(event: SensorEvent) {
        lastTimestampMs = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(bufferRotationMatrix, event.values)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                bufferAccelerometer[0] = event.values[0]
                bufferAccelerometer[1] = event.values[1]
                bufferAccelerometer[2] = event.values[2]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}