package eu.europa.ec.mrzscannerLogic.controller

import FaceCardImageAnalyzer
import MrzImageAnalyzer
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import eu.europa.ec.mrzscannerLogic.model.AntiSpoofingCheck
import eu.europa.ec.mrzscannerLogic.model.MrzDocument
import eu.europa.ec.mrzscannerLogic.model.ScanType
import eu.europa.ec.mrzscannerLogic.service.AnalyzerGuidelineCardService
import eu.europa.ec.mrzscannerLogic.service.CameraService
import eu.europa.ec.mrzscannerLogic.service.DriverLicenseParseService
import eu.europa.ec.mrzscannerLogic.service.FaceService
import eu.europa.ec.mrzscannerLogic.service.MrzParserService
import eu.europa.ec.mrzscannerLogic.service.SensorDocumentService
import eu.europa.ec.mrzscannerLogic.service.TextRecognitionService
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed class MrzScanState {
    object Idle : MrzScanState()
    object Initializing : MrzScanState()
    object Scanning : MrzScanState()
    data class Processing(val confidence: Float = 0f) : MrzScanState()

    data class Success(
        val document: MrzDocument? = null,
        val capturedImage: Bitmap? = null,
        val imagePath: String? = null
    ) : MrzScanState()

    data class SecurityCheckFailed(
        val failedChecks: List<AntiSpoofingCheck>,
        val reason: String
    ) : MrzScanState()

    data class Error(val message: String, val throwable: Throwable? = null) : MrzScanState()
}

interface MrzScanController {
    fun startScanning(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        scanType: ScanType,
    ): Flow<MrzScanState>

    fun stopScanning()
    fun isCameraAvailable(): Boolean
    fun isScanning(): Boolean
    fun enableTapToFocus(enabled: Boolean)
    fun enableTorch(enabled: Boolean)
}

class MrzScanControllerImpl(
    private val cameraService: CameraService,
    private val resourceProvider: ResourceProvider,
    private val analyzerGuidelineCardService: AnalyzerGuidelineCardService,
    private val sensorDocumentService: SensorDocumentService,
    private val parserService: MrzParserService,
    private val faceService: FaceService,
    private val driverLicenseParseService: DriverLicenseParseService,
    private val textRecognitionService: TextRecognitionService
) : MrzScanController {

    private val throttleMs = 120L
    private var tapToFocusEnabled = false
    private var lifecycleOwnerRef: LifecycleOwner? = null
    private var previewViewRef: PreviewView? = null

    // Escopo gerenciado para garantir o encerramento correto dos analisadores.
    private var analyzerScope: CoroutineScope? = null

    override fun startScanning(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        scanType: ScanType,
    ): Flow<MrzScanState> = callbackFlow {

        lifecycleOwnerRef = lifecycleOwner
        previewViewRef = previewView

        trySend(MrzScanState.Initializing)

        if (!isCameraAvailable()) {
            trySend(MrzScanState.Error("Camera not available on this device"))
            close()
            return@callbackFlow
        }

        try {

            sensorDocumentService.reset()
            analyzerGuidelineCardService.reset()

            sensorDocumentService.start(needsRotation = true, needsAccel = true)
            analyzerScope = CoroutineScope(Dispatchers.Default)
            val analyzer = createAnalyzer(scanType, this, analyzerScope!!)

            Log.d("MrzScanControllerImpl", "Analyzer: ${analyzer.javaClass.simpleName}")

            cameraService.start(lifecycleOwner, previewView, analyzer)
            cameraService.setupTapToFocus(previewView, tapToFocusEnabled)

            trySend(MrzScanState.Scanning)

        } catch (e: Exception) {
            trySend(MrzScanState.Error("Error starting scan", e))
            stopScanning()
        }

        awaitClose {
            stopScanning()
            lifecycleOwnerRef = null
            previewViewRef = null
        }
    }

    private fun createAnalyzer(
        scanType: ScanType,
        resultScope: ProducerScope<MrzScanState>,
        managedScope: CoroutineScope
    ): ImageAnalysis.Analyzer {
        return when (scanType) {
            is ScanType.Face -> FaceCardImageAnalyzer(
                resultFlow = resultScope,
                faceService = faceService,
                antiSpoofingService = analyzerGuidelineCardService,
                sensorDocumentService = sensorDocumentService,
                scope = managedScope,
                dispatcher = Dispatchers.Default
            )

            is ScanType.Document -> MrzImageAnalyzer(
                resultFlow = resultScope,
                parserService = parserService,
                driverLicenseParser = driverLicenseParseService,
                textRecognitionService = textRecognitionService,
                antiSpoofingService = analyzerGuidelineCardService,
                sensorDocumentService = sensorDocumentService,
                scope = managedScope,
                dispatcher = Dispatchers.Default,
                throttleMs = throttleMs,
                requiredSuccessFrames = 1,
                warmupFrames = 5,
                maxConsecutiveAntiSpoofFails = 3,
            )

            else -> throw IllegalArgumentException("Unsupported ScanType")
        } as ImageAnalysis.Analyzer
    }

    override fun stopScanning() {
        cameraService.stop()
        sensorDocumentService.stop()
        textRecognitionService.release()
        analyzerScope?.cancel()
        analyzerScope = null
    }

    override fun isCameraAvailable(): Boolean {
        return resourceProvider.provideContext().packageManager.hasSystemFeature(
            PackageManager.FEATURE_CAMERA_ANY
        )
    }

    override fun isScanning(): Boolean = cameraService.isRunning()

    override fun enableTapToFocus(enabled: Boolean) {
        tapToFocusEnabled = enabled
        previewViewRef?.let { view ->
            cameraService.setupTapToFocus(view, enabled)
        }
    }

    override fun enableTorch(enabled: Boolean) {
        cameraService.enableTorch(enabled)
    }
}