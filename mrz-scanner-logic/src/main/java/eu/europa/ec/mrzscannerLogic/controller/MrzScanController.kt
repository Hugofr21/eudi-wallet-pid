package eu.europa.ec.mrzscannerLogic.controller

import eu.europa.ec.mrzscannerLogic.config.FaceCardImageAnalyzer
import eu.europa.ec.mrzscannerLogic.config.MrzImageAnalyzer
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

    fun resetScanner()
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
    private var analyzerScope: CoroutineScope? = null
    private var currentAnalyzer: ImageAnalysis.Analyzer? = null

     /*
      * Objective:
      * Implement an Android instance to initialize the camera and analyze a frame-processing pipeline using
      * ImageAnalysis (CameraX) integrated with ML Kit, enabling automatic reading and extraction of data
      * from identification documents.
      *
      * Technical context:
      * The system must operate through continuous frame analysis captured by the camera, processing frames
      * via an ImageAnalyzer integrated with ML Kit (e.g., OCR and/or document detection), in order to detect,
      * classify, and interpret relevant information present on physical ID cards.
      *
      * Supported document types:
      * The pipeline must be capable of recognizing and processing, at minimum, the following documents:
      * - Identity Card (ID Card)
      * - Driving License
      * - Citizen Card
      *
      * Standards compliance:
      * Document processing must follow the formats and requirements defined by international standards,
      * namely ISO/IEC 18013-7 (Mobile Driving Licence / mDL and associated data models) and other applicable
      * ISO standards related to civil identification and credential interoperability.
      *
      * Data requirements and validation:
      * The system must support the extraction of specific identifiers and attributes (e.g., PID, ARF, or
      * other regulated identifiers), allowing the determination of which mandatory attributes must be
      * collected depending on the credential type and the required verification profile.
      *
      * The pipeline must validate the minimum required fields (mandatory attributes) imposed by applicable
      * legislation or by verification entities, ensuring that only strictly necessary data is collected and
      * processed, in compliance with data minimization and credential validation requirements.
      */

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
            currentAnalyzer = analyzer

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
        }
    }

    override fun stopScanning() {
        cameraService.stop()
        sensorDocumentService.stop()
        textRecognitionService.release()
        analyzerScope?.cancel()
        analyzerScope = null
        currentAnalyzer = null
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

    override fun resetScanner() {
        Log.d("MrzScanControllerImpl", "Resetting Scanner Engine...")
        sensorDocumentService.reset()
        analyzerGuidelineCardService.reset()

        if (currentAnalyzer is MrzImageAnalyzer) {
            (currentAnalyzer as MrzImageAnalyzer).resetAnalyzerState()
        }
    }
}