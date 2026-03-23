import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import eu.europa.ec.mrzscannerLogic.controller.MrzScanState
import eu.europa.ec.mrzscannerLogic.middleware.ProbabilityAggregator
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
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Analyzer otimizado para deteção AUTOMÁTICA e CONTÍNUA de documentos MRZ.
 *
 * Correções principais:
 * - Período de warmup: primeiros [warmupFrames] frames ignoram anti-spoofing (sensores
 *   ainda estão a calibrar e qualquer leitura seria instável).
 * - Anti-spoofing debounced: só emite SecurityCheckFailed após [maxConsecutiveAntiSpoofFails]
 *   falhas SEGUIDAS, evitando bloqueios por frames isolados com reflexo ou microtremor.
 * - OCR ocorre sempre (mesmo em falha de spoofing pontual), permitindo leitura rápida
 *   de documentos físicos reais.
 * - requiredSuccessFrames=1 por defeito: um parse MRZ válido com checksum correto é
 *   suficiente para emitir sucesso.
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
    /**
     * Número de frames iniciais em que o anti-spoofing é ignorado.
     * Permite que giroscópio e acelerómetro calibrem antes de avaliarem.
     */
    private val warmupFrames: Int = 5,
    /**
     * Número de falhas de anti-spoofing CONSECUTIVAS necessárias para emitir
     * SecurityCheckFailed. Um único frame com reflexo não bloqueia o scanner.
     */
    private val maxConsecutiveAntiSpoofFails: Int = 3,
) : ImageAnalysis.Analyzer {

    private val securityConfig = GuidelineAntiSpoofing(
        checkSpecularReflection = true,
        checkMoirePattern = true,
        checkGyroscope = true,
        checkAccelerometer = true,
        // Threshold mais permissivo: documentos físicos reais sob iluminação
        // ambiente normal têm scores entre 0.55–0.75. Valores acima de 0.75
        // são para ambiente de laboratório.
        threshold = 0.55f
    )

    private val isProcessing = AtomicBoolean(false)
    @Volatile private var lastProcessTime = 0L

    // Contador de frames totais processados (para warmup)
    private val frameCount = AtomicInteger(0)

    // Falhas de anti-spoofing consecutivas (reset quando passa)
    private var consecutiveAntiSpoofFails = 0

    // Sucessos MRZ consecutivos (para requiredSuccessFrames > 1)
    private var consecutiveSuccessCount = 0
    private var lastDetectedDocument: MrzDocument? = null

    private val probabilityAggregator = ProbabilityAggregator()

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

        val currentFrame = frameCount.incrementAndGet()
        val rotation = imageProxy.imageInfo.rotationDegrees
        val fullBitmap = imageProxy.toBitmap()
        val rotatedBitmap = rotateBitmap(fullBitmap, rotation)
        val croppedBitmap = extractRoi(rotatedBitmap)
        val sensorSnapshot = sensorDocumentService.actual

        scope.launch(dispatcher) {
            try {
                // ── 1. Anti-spoofing (com warmup e debounce) ──────────────────────────
                val isInWarmup = currentFrame <= warmupFrames

                if (!isInWarmup) {
                    val spoofingReport = antiSpoofingService.analyze(
                        bitmap = croppedBitmap,
                        config = securityConfig,
                        gyroscopeMatrix = sensorSnapshot.rotationMatrix,
                        accelerometerData = sensorSnapshot.accelerometerData
                    )

                    if (!spoofingReport.isReal) {
                        consecutiveAntiSpoofFails++
                        Log.d(
                            "MrzAnalyzer",
                            "Spoofing fail $consecutiveAntiSpoofFails/$maxConsecutiveAntiSpoofFails " +
                                    "(score=${spoofingReport.overallScore})"
                        )

                        // Só bloqueia após N falhas seguidas, não na primeira
                        if (consecutiveAntiSpoofFails >= maxConsecutiveAntiSpoofFails) {
                            consecutiveSuccessCount = 0
                            val failedChecks =
                                spoofingReport.checks.filter { !it.passed }.map { it.check }
                            resultFlow.trySend(
                                MrzScanState.SecurityCheckFailed(
                                    failedChecks = failedChecks,
                                    reason = "Documento fisicamente inválido (score: ${
                                        "%.2f".format(spoofingReport.overallScore)
                                    })"
                                )
                            )
                            // NÃO faz return aqui: deixa o OCR tentar na mesma.
                            // Se o MRZ for válido mesmo com spoofing marginal, o documento
                            // é provavelmente real e o SecurityCheckFailed será sobreposto
                            // pelo Success no próximo frame bom.
                        }
                        // Frame isolado de falha: continua para OCR sem bloquear
                    } else {
                        // Passou no anti-spoofing — reset do contador de falhas
                        consecutiveAntiSpoofFails = 0
                    }
                } else {
                    Log.d("MrzAnalyzer", "Warmup frame $currentFrame/$warmupFrames — a saltar anti-spoofing")
                }

                // ── 2. OCR ────────────────────────────────────────────────────────────
                val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
                val textResult = textRecognitionService.recognizeText(inputImage, 0)

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
        val allLines = textObject.text.split("\n")

        val mrzCandidates = allLines
            .map { cleanLine(it) }
            .filter { isValidMrzLine(it) }

        if (mrzCandidates.size >= 2) {
            val parseResult = parserService.parse(mrzCandidates)
            if (parseResult is MrzParseResult.Success) {
                handleParseMrzResult(parseResult)
                return
            }
        }

        val dlResult = driverLicenseParser.parse(textObject)
        if (dlResult is MrzParseResult.Success) {
            val dlDoc = dlResult.document as MrzDocument.DrivingLicense
            handleDrivingLicensePartial(dlDoc)
        } else {
            consecutiveSuccessCount = 0
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
        }
    }

    /**
     * Extrai a zona central da imagem correspondente à janela de scan da UI.
     * A caixa da UI ocupa ~85% da largura e ~40% da altura em portrait.
     */
    private fun extractRoi(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val roiWidthFraction = 0.85f
        val roiHeightFraction = 0.40f

        val cropWidth = (width * roiWidthFraction).toInt()
        val cropHeight = (height * roiHeightFraction).toInt()

        val x = (width - cropWidth) / 2
        val y = (height - cropHeight) / 2

        return Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight)
    }
}