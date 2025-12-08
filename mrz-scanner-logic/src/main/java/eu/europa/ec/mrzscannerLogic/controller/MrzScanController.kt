package eu.europa.ec.mrzscannerLogic.controller

import android.content.pm.PackageManager
import android.view.MotionEvent
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import eu.europa.ec.mrzscannerLogic.config.MrzImageAnalyzer
import eu.europa.ec.mrzscannerLogic.model.MrzDocument
import eu.europa.ec.mrzscannerLogic.service.MrzParserService
import eu.europa.ec.mrzscannerLogic.service.TextRecognitionService
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


sealed class MrzScanState {
    object Idle : MrzScanState()
    object Initializing : MrzScanState()
    object Scanning : MrzScanState()
    data class Processing(val confidence: Float = 0f) : MrzScanState()
    data class Success(val document: MrzDocument) : MrzScanState()
    data class Error(val message: String, val throwable: Throwable? = null) : MrzScanState()
}

interface MrzScanController {

    /**
     * Inicia o processo de scanning
     * @return Flow de estados do processo
     */
    fun startScanning(): Flow<MrzScanState>

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
}

class MrzScanControllerImpl(
    private val resourceProvider: ResourceProvider,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val parserService: MrzParserService,
    private val textRecognitionService: TextRecognitionService
) : MrzScanController {

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val throttleMs = 150L  // Parâmetro passado para analyzer
    private val roiBottomFraction = 0.3f  // Novo parâmetro passado para analyzer para ROI

    private var tapToFocusEnabled = false

    override fun startScanning(): Flow<MrzScanState> = callbackFlow {
        trySend(MrzScanState.Initializing)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(resourceProvider.provideContext())
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCamera(this@callbackFlow)
                trySend(MrzScanState.Scanning)
            } catch (e: Exception) {
                trySend(MrzScanState.Error("Erro ao inicializar câmera", e))
            }
        }, ContextCompat.getMainExecutor(resourceProvider.provideContext()))

        awaitClose {
            stopScanning()
        }
    }

    private fun bindCamera(resultFlow: ProducerScope<MrzScanState>) {
        val cameraProvider = cameraProvider ?: return

        cameraProvider.unbindAll()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        // Passar parâmetros do controller para o analyzer
        val analyzer = MrzImageAnalyzer(
            resultFlow = resultFlow,
            parserService = parserService,
            textRecognitionService = textRecognitionService,
            scope = CoroutineScope(Dispatchers.Default),
            dispatcher = Dispatchers.Default,
            throttleMs = throttleMs,  // Passado do controller
            roiBottomFraction = roiBottomFraction  // Novo param passado
        )

        imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

        try {
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            ) as Camera?
        } catch (e: Exception) {
            resultFlow.trySend(MrzScanState.Error("Erro ao bindar câmera", e))
        }

        setupTapToFocus()
    }

    override fun stopScanning() {
        cameraProvider?.unbindAll()
        textRecognitionService.release()
        cameraExecutor.shutdown()
        camera = null
        cameraProvider = null
    }

    override fun isCameraAvailable(): Boolean {
        return resourceProvider.provideContext().packageManager.hasSystemFeature(
            PackageManager.FEATURE_CAMERA_ANY
        )
    }

    override fun isScanning(): Boolean {
        return camera != null && cameraProvider != null
    }

    override fun enableTapToFocus(enabled: Boolean) {
        tapToFocusEnabled = enabled
        setupTapToFocus()
    }

    private fun setupTapToFocus() {
        if (!tapToFocusEnabled) {
            previewView.setOnTouchListener(null)
            return
        }

        previewView.setOnTouchListener { view, event ->
            if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener false

            val meteringPointFactory = SurfaceOrientedMeteringPointFactory(
                previewView.width.toFloat(),
                previewView.height.toFloat()
            )
            val meteringPoint = meteringPointFactory.createPoint(event.x, event.y)

            val focusAction = FocusMeteringAction.Builder(meteringPoint)
                .addPoint(meteringPoint, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB)
                .build()

            camera?.cameraControl?.startFocusAndMetering(focusAction)

            view.performClick()

            true
        }
    }
}