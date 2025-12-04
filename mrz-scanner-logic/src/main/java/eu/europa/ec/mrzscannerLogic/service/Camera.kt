package eu.europa.ec.mrzscannerLogic.service

import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

class Camera(
    private val lifecycleOwner: LifecycleOwner,
    private val surfaceProvider: Preview.SurfaceProvider
) {
    private val executor =  Executors.newSingleThreadExecutor()
    private val textRecognizer: TextRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.Builder().build()
    )
    private var camera: Camera? = null
    private var inProcessing =  false
    private var  lasProcessTime = 0L
    private val throttleMs  = 150L


}