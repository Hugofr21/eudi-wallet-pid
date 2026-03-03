package eu.europa.ec.mrzscannerLogic.service


import android.util.Log
import android.view.MotionEvent
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import java.util.concurrent.Executors

interface CameraService {
    fun start(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: ImageAnalysis.Analyzer
    )
    fun stop()
    fun isRunning(): Boolean
    fun setupTapToFocus(previewView: PreviewView, enabled: Boolean)
    fun enableTorch(enabled: Boolean)
}

class CameraServiceImpl(
    private val resourceProvider: ResourceProvider,
) : CameraService {

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun start(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: ImageAnalysis.Analyzer
    ) {
        val context = resourceProvider.provideContext()
        val future = ProcessCameraProvider.getInstance(context)

        future.addListener({
            provider = future.get()
            provider?.unbindAll()

            val preview = Preview.Builder().build().apply {
                surfaceProvider = previewView.surfaceProvider
            }

            // HIGH PRECISION CONFIGURATION CORRECTED
            val analysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                android.util.Size(2048, 1536),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                )
                .build()

            analysis.setAnalyzer(ContextCompat.getMainExecutor(context), analyzer)

            try {
                camera = provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )

                val cameraControl = camera?.cameraControl

                cameraControl?.setZoomRatio(1.5f)

                val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
                val centerPoint = factory.createPoint(0.5f, 0.5f)
                val action = FocusMeteringAction.Builder(centerPoint, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                cameraControl?.startFocusAndMetering(action)

            } catch (e: Exception) {
                e.message?.let { Log.d("CameraService", it) }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    override fun stop() {
        provider?.unbindAll()
        camera = null
    }

    override fun isRunning(): Boolean = camera != null

    override fun setupTapToFocus(previewView: PreviewView, enabled: Boolean) {
        if (!enabled) {
            previewView.setOnTouchListener(null)
            return
        }

        previewView.setOnTouchListener { view, event ->
            if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener false

            val currentCamera = camera ?: return@setOnTouchListener false

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

    override fun enableTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }
}