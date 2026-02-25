package eu.europa.ec.mrzscannerLogic.service

import android.view.MotionEvent
import androidx.camera.core.CameraSelector
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
    private var imageCapture: ImageCapture? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()



    override fun start(lifecycleOwner: LifecycleOwner,previewView: PreviewView, analysisUseCase: ImageAnalysis) {
        val cameraFuture = ProcessCameraProvider.getInstance(resourceProvider.provideContext())
        cameraFuture.addListener({
            val cameraProvider = cameraFuture.get()
            bindPreview(cameraProvider, previewView, analysisUseCase,lifecycleOwner)
        }, resourceProvider.provideContext().mainExecutor)
    }
    private fun bindPreview(
        cameraProvider: ProcessCameraProvider,
        previewView: PreviewView,
        analysisUseCase: ImageAnalysis,
        lifecycleOwner: LifecycleOwner
    ) {
        val provider = cameraProvider ?: return;

        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        analysisUseCase = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                frameAnalyser?.let { analysis.setAnalyzer(cameraExecutor, it) }
            }
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                analysisUseCase
            )
            logController.d(TAG, "Camera bound successfully")
        } catch (ex: Exception) {
            logController.e(TAG, "Use case binding failed", ex)
        }

    }

    override fun stop() {
        cameraExecutor.shutdown();
        cameraProvider?.unbindAll()
        logController.d(TAG, "Camera stopped successfully")
    }

    override fun isRunning(): Boolean {
        if (cameraProvider == null) {
            return false
        }
        return cameraProvider!!.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
    }

    override fun setupTapToFocus(
        previewView: PreviewView,
        enabled: Boolean
    ) {
        if (!enabled) {
            previewView.setOnTouchListener(null)
            return
        }

        previewView.setOnTouchListener { view, event ->
            if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener false

            val currentCamera = cameraProvider ?: return@setOnTouchListener false

            val factory = SurfaceOrientedMeteringPointFactory(
                previewView.width.toFloat(),
                previewView.height.toFloat()
            )
            val point = factory.createPoint(event.x, event.y)
            val action = FocusMeteringAction.Builder(point).build()

            currentCamera.cameraControl.startFocusAndMetering(action)
            view.performClick()
            return@setOnTouchListener true
        }
    }

}
