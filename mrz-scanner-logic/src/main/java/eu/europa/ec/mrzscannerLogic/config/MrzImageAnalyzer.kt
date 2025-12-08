package eu.europa.ec.mrzscannerLogic.config

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import eu.europa.ec.mrzscannerLogic.controller.MrzScanState
import eu.europa.ec.mrzscannerLogic.service.MrzParseResult
import eu.europa.ec.mrzscannerLogic.service.MrzParserService
import eu.europa.ec.mrzscannerLogic.service.TextRecognitionService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MrzImageAnalyzer(
    private val resultFlow: ProducerScope<MrzScanState>,
    private val parserService: MrzParserService,
    private val textRecognitionService: TextRecognitionService,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val throttleMs: Long = 150L,
    private val roiBottomFraction: Float = 0.3f  // New param: fraction of image height for MRZ ROI (bottom part)
) : ImageAnalysis.Analyzer {
    private val isProcessing = AtomicBoolean(false)
    @Volatile private var lastProcessTime = 0L

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (isProcessing.get() || (currentTime - lastProcessTime) < throttleMs) {
            imageProxy.close()
            return
        }

        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        lastProcessTime = currentTime

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            isProcessing.set(false)
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        scope.launch(dispatcher) {
            val textResult = textRecognitionService.recognizeText(inputImage, rotation)

            textResult.onSuccess { recognizedText ->
                processRecognizedText(recognizedText, imageProxy.height, imageProxy.width, rotation)
            }.onFailure { e ->
                resultFlow.trySend(MrzScanState.Error("Erro OCR", e))
            }

            imageProxy.close()
            isProcessing.set(false)
        }
    }

    private fun processRecognizedText(
        recognizedText: String,
        imageHeight: Int,
        imageWidth: Int,
        rotation: Int
    ) {
        resultFlow.trySend(MrzScanState.Processing())  // Send processing state

        val lines = recognizedText.split("\n")
        val totalLines = lines.size
        if (totalLines < 2) {
            return  // Not enough lines, continue scanning
        }

        // Approximate ROI using line indices: bottom fraction of lines
        val roiStartIndex = (totalLines * (1 - roiBottomFraction)).toInt().coerceIn(0, totalLines - 1)
        val bottomLines = lines.subList(roiStartIndex, totalLines)


        val candidateLines = bottomLines
            .map { it.uppercase(Locale.ROOT).replace(" ", "") }
            .filter { it.length >= 28 }  // Assuming min MRZ line length

        if (candidateLines.size < 2) {
            return  // Not enough candidate lines
        }

        val parseResult = parserService.parse(candidateLines)

        when (parseResult) {
            is MrzParseResult.Success -> {
                resultFlow.trySend(MrzScanState.Success(parseResult.document))
            }
            else -> {
                // Continua tentando (keep trying)
            }
        }
    }
}