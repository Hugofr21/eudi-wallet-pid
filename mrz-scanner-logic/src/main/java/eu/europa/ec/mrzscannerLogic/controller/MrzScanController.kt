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

    /**
     * Inicia o processo de scanning
     * @return Flow de estados do processo
     */
    fun startScanning(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        scanType: ScanType,
    ): Flow<MrzScanState>

    /**
     * Para o processo de scanning e libera recursos
     */
    fun stopScanning()

    /**
     * Verifica se a câmera está disponível no dispositivo
     */
    fun isCameraAvailable(): Boolean

    /**
     * Verifica se o scanning está ativo
     */
    fun isScanning(): Boolean

    /**
     * Ativa/desativa tap-to-focus
     */
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
    private val throttleMs = 150L
    private val roiBottomFraction = 0.3f
    private var tapToFocusEnabled = false
    private var lifecycleOwnerRef: LifecycleOwner? = null
    private var previewViewRef: PreviewView? = null

    /**
      * 1. Check availability (Simple Delegation)
      * 2. Create the appropriate Analyzer (Factory Logic)
      * 3. Starting the Camera Service (SRP: The service knows how to start the hardware)
      * 4. Configure extra features
    **/
    override fun startScanning(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        scanType: ScanType,
    ): Flow<MrzScanState> = callbackFlow {

        lifecycleOwnerRef = lifecycleOwner
        previewViewRef = previewView

        trySend(MrzScanState.Initializing)

        if (!isCameraAvailable()) {
            trySend(MrzScanState.Error("Câmara não disponível neste dispositivo"))
            close()
            return@callbackFlow
        }

        try {
            sensorDocumentService.start(needsRotation = true, needsAccel = true)

            val analyzer = createAnalyzer(scanType, this)

            Log.d("CameraService", "Starting camera service")
            Log.d("MrzScanControllerImpl", "Controller analyzer ${analyzer.javaClass.simpleName}")


            cameraService.start(lifecycleOwner, previewView, analyzer)
            cameraService.setupTapToFocus(previewView, tapToFocusEnabled)

            trySend(MrzScanState.Scanning)

        } catch (e: Exception) {
            trySend(MrzScanState.Error("Erro ao iniciar scanning", e))
            stopScanning()
        }

        awaitClose {
            stopScanning()
            lifecycleOwnerRef = null
            previewViewRef = null
        }
    }

    /**
     * Factory Method para criar o analisador correto.
     * Isto mantém o código de startScanning limpo e coeso.
     */
    private fun createAnalyzer(
        scanType: ScanType,
        scope: ProducerScope<MrzScanState>
    ): ImageAnalysis.Analyzer {
        return when (scanType) {
            is ScanType.Face -> FaceCardImageAnalyzer(
                resultFlow = scope,
                faceService = faceService,
                antiSpoofingService = analyzerGuidelineCardService,
                sensorDocumentService = sensorDocumentService,
                scope = CoroutineScope(Dispatchers.Default),
                dispatcher = Dispatchers.Default
            )

            is ScanType.Document -> MrzImageAnalyzer(
                resultFlow = scope,
                parserService = parserService,
                driverLicenseParser = driverLicenseParseService,
                textRecognitionService = textRecognitionService,
                antiSpoofingService = analyzerGuidelineCardService,
                sensorDocumentService = sensorDocumentService,
                scope = CoroutineScope(Dispatchers.Default),
                dispatcher = Dispatchers.Default,
                throttleMs = throttleMs,
                roiBottomFraction = roiBottomFraction
            )

            else -> {
                throw IllegalArgumentException("Invalid scan type: $scanType")
            }
        }
    }

    override fun stopScanning() {
        cameraService.stop()
        sensorDocumentService.stop()
        textRecognitionService.release()
    }

    override fun isCameraAvailable(): Boolean {
        return resourceProvider.provideContext().packageManager.hasSystemFeature(
            PackageManager.FEATURE_CAMERA_ANY
        )
    }

    override fun isScanning(): Boolean {
        return cameraService.isRunning()
    }

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