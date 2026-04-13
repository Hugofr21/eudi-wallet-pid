package eu.europa.ec.mrzscannerLogic.config

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import eu.europa.ec.mrzscannerLogic.controller.MrzScanState
import eu.europa.ec.mrzscannerLogic.middleware.ProbabilityAggregator
import eu.europa.ec.mrzscannerLogic.model.AntiSpoofingCheck
import eu.europa.ec.mrzscannerLogic.model.GuidelineAntiSpoofing
import eu.europa.ec.mrzscannerLogic.model.MrzDocument
import eu.europa.ec.mrzscannerLogic.service.AnalyzerGuidelineCardService
import eu.europa.ec.mrzscannerLogic.service.DriverLicenseParseService
import eu.europa.ec.mrzscannerLogic.service.MrzParseResult
import eu.europa.ec.mrzscannerLogic.service.MrzParserService
import eu.europa.ec.mrzscannerLogic.service.SensorDocumentService
import eu.europa.ec.mrzscannerLogic.service.TextRecognitionService
import eu.europa.ec.mrzscannerLogic.utils.ImageUtils.rotateBitmap
import eu.europa.ec.mrzscannerLogic.utils.MRZCleaner.cleanLine
import eu.europa.ec.mrzscannerLogic.utils.MRZCleaner.isValidMrzLine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Define a necessidade de aplicação de um limitador temporal de frames (frame throttling)
 * durante o pipeline de análise de vídeo, com o objetivo de evitar concorrência e sobreposição
 * de processamento (overlay) entre módulos críticos como detecção e anti-spoofing.
 *
 * <p>Sem um mecanismo de limitação, o sistema pode sofrer acúmulo de frames na fila de processamento,
 * causando aumento progressivo de latência e potencial inconsistência entre os resultados de módulos
 * que dependem de sincronização temporal. Esse cenário compromete diretamente a integridade do fluxo
 * de validação biométrica e a confiabilidade da detecção de eventos.</p>
 *
 * <p>Considerando que o algoritmo de OCR possui tempo médio de processamento de aproximadamente
 * 150 ms por frame, a capacidade prática de avaliação do sistema é limitada a cerca de 6 frames por segundo.
 * Entretanto, uma câmera operando em 30 FPS produz frames em uma taxa significativamente superior à capacidade
 * de consumo do pipeline. Caso a captura seja realizada de forma síncrona e contínua, a fila de frames
 * tende a crescer rapidamente, resultando em backlog e degradação operacional.</p>
 *
 * <p>Como estratégia de mitigação, recomenda-se o descarte controlado de frames após a captura de um frame
 * selecionado para leitura. Uma abordagem objetiva consiste em descartar os três frames subsequentes ao frame
 * processado, reduzindo a pressão sobre a fila e mantendo o processamento próximo do tempo real.</p>
 *
 * <p>Essa política de descarte deve ser ajustada conforme o desempenho real do dispositivo e a latência
 * observada em produção, podendo ser substituída por mecanismos adaptativos baseados em taxa de processamento,
 * tamanho de fila ou deadline temporal.</p>
 *
 * @implNote A taxa de captura (30 FPS) excede significativamente a taxa máxima de processamento (≈ 6 FPS),
 *           tornando inevitável o descarte de frames ou o uso de buffering com limite rígido.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Producer%E2%80%93consumer_problem">Producer–Consumer Problem</a>
 * @see <a href="https://developer.android.com/reference/java/util/concurrent/BlockingQueue">BlockingQueue</a>
 */
class MrzImageAnalyzer(
    private val resultFlow: ProducerScope<MrzScanState>,
    private val parserService: MrzParserService,
    private val driverLicenseParser: DriverLicenseParseService,
    private val textRecognitionService: TextRecognitionService,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val throttleMs: Long = 120L,
    private val requiredSuccessFrames: Int = 1,
    private val antiSpoofingService: AnalyzerGuidelineCardService,
    private val sensorDocumentService: SensorDocumentService,
    private val warmupFrames: Int = 5,
    private val maxConsecutiveAntiSpoofFails: Int = 3,
) : ImageAnalysis.Analyzer {

    private val securityConfig = GuidelineAntiSpoofing(
        checkSpecularReflection = true,
        checkMoirePattern = true,
        checkGyroscope = true,
        checkAccelerometer = true,
        threshold = 0.75f
    )

    private val isProcessing = AtomicBoolean(false)
    @Volatile private var lastProcessTime = 0L

    private val frameCount = AtomicInteger(0)
    private var consecutiveAntiSpoofFails = 0
    private var consecutiveSuccessCount = 0
    private var lastDetectedDocument: MrzDocument? = null
    private var wasInSpoofingError = false
    private val probabilityAggregator = ProbabilityAggregator()

    @OptIn(ExperimentalGetImage::class)
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

        val currentFrame = frameCount.incrementAndGet()
        val rotation = imageProxy.imageInfo.rotationDegrees
        val fullBitmap = imageProxy.toBitmap()
        val rotatedBitmap = rotateBitmap(fullBitmap, rotation)
        val croppedBitmap = extractRoi(rotatedBitmap)
        val sensorSnapshot = sensorDocumentService.actual

        scope.launch(dispatcher) {
            try {
                val isInWarmup = currentFrame <= warmupFrames

                val deferredTextResult = async {
                    val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
                    textRecognitionService.recognizeText(inputImage, 0)
                }

                if (!isInWarmup) {
                    val spoofingReport = antiSpoofingService.analyze(
                        bitmap = rotatedBitmap,
                        config = securityConfig,
                        gyroscopeMatrix = sensorSnapshot.rotationMatrix,
                        accelerometerData = sensorSnapshot.accelerometerData
                    )

                    if (!spoofingReport.isReal) {
                        consecutiveAntiSpoofFails++
                        Log.d("MrzAnalyzer", "Spoofing fail $consecutiveAntiSpoofFails/$maxConsecutiveAntiSpoofFails (score=${spoofingReport.overallScore})")

                        if (consecutiveAntiSpoofFails >= maxConsecutiveAntiSpoofFails) {
                            consecutiveSuccessCount = 0
                            lastDetectedDocument = null
                            probabilityAggregator.reset()

                            wasInSpoofingError = true
                            val failedChecks = spoofingReport.checks.filter { !it.passed }.map { it.check }

                            val isTooDark = spoofingReport.checks.any { it.check == AntiSpoofingCheck.IMAGE_QUALITY && it.score < 0.40f }
                            val alertReason = if (isTooDark) {
                                "Dark environment. Turn on the light or move the document closer."
                            } else {
                                "Physically invalid document (score: ${"%.2f".format(spoofingReport.overallScore)})"
                            }
                            resultFlow.trySend(
                                MrzScanState.SecurityCheckFailed(failedChecks = failedChecks, reason = alertReason)
                            )
                        }

                        deferredTextResult.cancel()
                        return@launch
                    } else {
                        consecutiveAntiSpoofFails = 0

                        if (wasInSpoofingError) {
                            wasInSpoofingError = false
                            resultFlow.trySend(MrzScanState.Scanning)
                        }
                    }
                }

                val textResult = deferredTextResult.await()

                textResult.onSuccess { textObject ->
                    processRecognizedText(textObject, croppedBitmap)
                }.onFailure {
                    consecutiveSuccessCount = 0
                }

            } finally {
                imageProxy.close()
                isProcessing.set(false)
                if (fullBitmap !== rotatedBitmap) rotatedBitmap.recycle()
            }
        }
    }

    private fun processRecognizedText(textObject: Text, bitmap: Bitmap) {
        val allLines = textObject.textBlocks.flatMap { it.lines }.map { it.text }

        val mrzCandidates = allLines
            .map { cleanLine(it) }
            .filter { isValidMrzLine(it) }

        if (mrzCandidates.size >= 2) {
            val parseResult = parserService.parse(mrzCandidates)
            handleParseMrzResult(parseResult)
            return
        }

        when (val dlResult = driverLicenseParser.parse(textObject)) {
            is MrzParseResult.Success -> {
                val dlDoc = dlResult.document as MrzDocument.DrivingLicense
                handleDrivingLicensePartial(dlDoc)
            }
            is MrzParseResult.Partial -> {
                val dlDoc = dlResult.document as MrzDocument.DrivingLicense
                handleDrivingLicensePartial(dlDoc)
            }
            else -> {
                // empty
            }
        }
    }

    private fun handleDrivingLicensePartial(partialDoc: MrzDocument.DrivingLicense) {
        probabilityAggregator.addFrame(partialDoc)
        resultFlow.trySend(MrzScanState.Processing(probabilityAggregator.getConfidenceProgress()))

        if (probabilityAggregator.isConfident()) {
            val finalDoc = probabilityAggregator.getResult()
            resultFlow.trySend(MrzScanState.Success(document = finalDoc))
            probabilityAggregator.reset()
        }
    }

    private fun handleParseMrzResult(parseResult: MrzParseResult) {
        when (parseResult) {
            is MrzParseResult.Success -> handleSuccessfulParse(parseResult.document)
            is MrzParseResult.InvalidChecksum -> {
                resultFlow.trySend(MrzScanState.Processing(0.6f))
                consecutiveSuccessCount = 0
            }
            else -> consecutiveSuccessCount = 0
        }
    }

    private fun handleSuccessfulParse(document: MrzDocument) {
        val isSameDocument = lastDetectedDocument?.documentNumber == document.documentNumber

        if (isSameDocument) {
            consecutiveSuccessCount++
        } else {
            consecutiveSuccessCount = 1
            lastDetectedDocument = document
        }

        val confidence = (consecutiveSuccessCount.toFloat() / requiredSuccessFrames).coerceIn(0f, 1f)
        resultFlow.trySend(MrzScanState.Processing(confidence))

        if (consecutiveSuccessCount >= requiredSuccessFrames) {
            consecutiveAntiSpoofFails = 0
            resultFlow.trySend(MrzScanState.Success(document))
            consecutiveSuccessCount = 0
            lastDetectedDocument = null
            probabilityAggregator.reset()
        }
    }

    private fun extractRoi(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val roiWidthFraction = 0.90f
        val roiHeightFraction = 0.80f

        val cropWidth = (width * roiWidthFraction).toInt()
        val cropHeight = (height * roiHeightFraction).toInt()

        val x = (width - cropWidth) / 2
        val y = (height - cropHeight) / 2

        return Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight)
    }

    fun resetAnalyzerState() {
        consecutiveAntiSpoofFails = 0
        consecutiveSuccessCount = 0
        lastDetectedDocument = null
        wasInSpoofingError = false
        probabilityAggregator.reset()
        frameCount.set(0)

        resultFlow.trySend(MrzScanState.Scanning)
        resultFlow.trySend(MrzScanState.Processing(0f))

        Log.d("MrzAnalyzer", "Analyzer state reset complete. Ready for new capture.")
    }
}