package eu.europa.ec.mrzscannerLogic.service

import android.view.MotionEvent
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.log.LogControllerImpl
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

interface CameraFrontService {
    fun start(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analysisUseCase: ImageAnalysis
    )
    fun stop()
    fun isRunning(): Boolean
    fun setupTapToFocus(previewView: PreviewView, enabled: Boolean)
}


class CameraFrontServiceImpl(
    private val resourceProvider: ResourceProvider,
    private val logController: LogController
): CameraFrontService{

    companion object {
        private const val TAG = "FrontCameraManager"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService? = null
    private var camera: Camera? = null
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Idle)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()


    sealed class CameraState {
        object Idle : CameraState()
        object Starting : CameraState()
        object Running : CameraState()
        data class Error(val exception: Throwable) : CameraState()
    }


    override fun start(lifecycleOwner: LifecycleOwner,previewView: PreviewView, analysisUseCase: ImageAnalysis) {
        val cameraFuture = ProcessCameraProvider.getInstance(resourceProvider.provideContext())
        cameraFuture.addListener({
            val cameraProvider = cameraFuture.get()
            bindPreview(cameraProvider, previewView, analysisUseCase,lifecycleOwner)
        }, resourceProvider.provideContext().mainExecutor)
    }
    private fun bindPreview(
        provider: ProcessCameraProvider,
        previewView: PreviewView,
        analysisUseCase: ImageAnalysis,
        lifecycleOwner: LifecycleOwner
    ) {
        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                analysisUseCase
            )
            logController.d(TAG, { "Câmara vinculada com sucesso" })
        } catch (ex: Exception) {
            _cameraState.value = CameraState.Error(ex)
            logController.e(TAG, ex)
        }
    }

    override fun stop() {
        _cameraState.value = CameraState.Idle
        cameraProvider?.unbindAll()
        cameraExecutor?.shutdown()
        cameraExecutor = null
        camera = null
        logController.d(TAG, { "Resource of camera stop" })
    }

    override fun isRunning(): Boolean = _cameraState.value is CameraState.Running

    private fun ensureExecutorActive() {
        if (cameraExecutor == null || cameraExecutor!!.isShutdown) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }
    }

    override fun setupTapToFocus(previewView: PreviewView, enabled: Boolean) {
        if (!enabled) {
            previewView.setOnTouchListener(null)
            return
        }

        previewView.setOnTouchListener { view, event ->
            if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener false

            val currentCamera = camera ?: return@setOnTouchListener false

            val factory = previewView.meteringPointFactory
            val point = factory.createPoint(event.x, event.y)
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()

            currentCamera.cameraControl.startFocusAndMetering(action)
            view.performClick()
            true
        }
    }

}
