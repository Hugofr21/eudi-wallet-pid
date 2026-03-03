import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import eu.europa.ec.mrzscannerLogic.controller.MrzScanState
import eu.europa.ec.mrzscannerLogic.middleware.DrivingLicenseAggregator
import eu.europa.ec.mrzscannerLogic.middleware.ProbabilityAggregator
import eu.europa.ec.mrzscannerLogic.model.GuidelineAntiSpoofing
import eu.europa.ec.mrzscannerLogic.model.MrzDocument
import eu.europa.ec.mrzscannerLogic.service.AnalyzerGuidelineCardService
import eu.europa.ec.mrzscannerLogic.service.DriverLicenseParseService
import eu.europa.ec.mrzscannerLogic.service.MrzParseResult
import eu.europa.ec.mrzscannerLogic.service.MrzParserService
import eu.europa.ec.mrzscannerLogic.service.SensorDocumentService
import eu.europa.ec.mrzscannerLogic.service.TextRecognitionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Analyzer otimizado para detecção AUTOMÁTICA e CONTÍNUA de documentos MRZ
 *
 * Características:
 * - Processa ~30 frames/segundo (limitado por throttle)
 * - ROI focado nos 30% inferiores da imagem (zona MRZ)
 * - Confirmação em múltiplos frames para evitar falsos positivos
 * - Sem intervenção do usuário necessária
 */
class MrzImageAnalyzer(
    private val resultFlow: ProducerScope<MrzScanState>,
    private val parserService: MrzParserService,
    private val driverLicenseParser: DriverLicenseParseService,
    private val textRecognitionService: TextRecognitionService,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val throttleMs: Long = 600L, // Processa ~6-7 frames/segundo
    private val roiBottomFraction: Float = 0.3f, // Foca nos 30% inferiores
    private val requiredSuccessFrames: Int = 2, // Número mínimo de frames com sucesso para emitir sucesso
    private val antiSpoofingService: AnalyzerGuidelineCardService,
    private val sensorDocumentService: SensorDocumentService
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

    // Controle de confirmação multi-frame
    private var consecutiveSuccessCount = 0
    private var lastDetectedDocument: MrzDocument? = null

    // Estatísticas (para debug)
    private var totalFrames = 0L
    private var processedFrames = 0L
    private var successfulReads = 0L

    private val dlAggregator = DrivingLicenseAggregator()
    private val probabilityAggregator = ProbabilityAggregator()

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        totalFrames++

        val currentTime = System.currentTimeMillis()

        // THROTTLE: Evitar sobrecarga processando todos os frames
        if (isProcessing.get() || (currentTime - lastProcessTime) < throttleMs) {
            imageProxy.close()
            return
        }

        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        lastProcessTime = currentTime
        processedFrames++

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            isProcessing.set(false)
            return
        }

        val bitmap = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees
        val sensorSnapshot = sensorDocumentService.actual

        // Processar de forma assíncrona
        scope.launch(dispatcher) {
            try {
                val spoofingReport = antiSpoofingService.analyze(
                    bitmap = bitmap,
                    config = securityConfig,
                    gyroscopeMatrix = sensorSnapshot.rotationMatrix,
                    accelerometerData = sensorSnapshot.accelerometerData
                )

                if (!spoofingReport.isReal) {
                    consecutiveSuccessCount = 0

                    val failedChecks = spoofingReport.checks
                        .filter { !it.passed }
                        .map { it.check }

                    println("Falhas de spoofing: $failedChecks")

                    resultFlow.trySend(
                        MrzScanState.SecurityCheckFailed(
                            failedChecks = failedChecks,
                            reason = "Documento fisicamente inválido (Score: ${spoofingReport.overallScore})"
                        )
                    )
                    return@launch
                }


                // 2. InputImage via Bitmap
                val inputImage = InputImage.fromBitmap(bitmap, rotation)
                // OCR no frame atual
                val textResult = textRecognitionService.recognizeText(inputImage, rotation)

                textResult.onSuccess { textObject ->
                    processRecognizedText(
                        textObject = textObject,
                        rotation = rotation,
                        bitmap = bitmap,
                    )
                }.onFailure { e ->
                    // Erro de OCR - não interrompe o fluxo, continua tentando
                    consecutiveSuccessCount = 0
                }
            } finally {
                imageProxy.close()
                isProcessing.set(false)
            }
        }
    }

    /**
     * CORREÇÃO TÉCNICA:
     * Removemos a lógica de 'roiBottomFraction' que descartava linhas em close-ups.
     * Implementamos filtragem por conteúdo (heurística).
     */
    private fun processRecognizedText(
        textObject: Text,
        bitmap: Bitmap,
        rotation: Int
    ) {
        val fullText = textObject.text

        // Debug para confirmar entrada
        if (fullText.isNotEmpty()) {
            Log.d("MRZ_RAW", "OCR Bruto:\n$fullText")
        }

        val allLines = fullText.split("\n")




        // 1. Limpeza Prévia: Remove espaços e normaliza caracteres confusos
        // Filtragem Inteligente: Mantém APENAS linhas que parecem MRZ
        // Critério: Tamanho > 20 e contém '<<' ou dígitos suficientes

        val mrzCandidates = allLines
            .map { cleanLine(it) }
            .filter { isValidMrzLine(it) }


        if (mrzCandidates.size >= 2) {
            Log.d("MRZ_PROC", "Padrão MRZ detetado.")
            val parseResult = parserService.parse(mrzCandidates)

            if (parseResult is MrzParseResult.Success) {
                handleParseMrzResult(parseResult)
                return // Se encontrou MRZ, para por aqui
            }
        }


        Log.d("DL_PROC", "Tentando Carta de Condução por Blocos...")

        // AQUI ESTÁ A CORREÇÃO: Passamos o textObject que tem os blocos
        val dlResult = driverLicenseParser.parse(textObject)

        if (dlResult is MrzParseResult.Success) {
            Log.i("DL_SUCCESS", "Carta detetada via Blocos!")
            val dlDoc = dlResult.document as MrzDocument.DrivingLicense

            // Passamos para o agregador
            handleDrivingLicensePartial(dlDoc, bitmap, rotation)
        } else {
            // Se falhar ambos, resetamos
            consecutiveSuccessCount = 0
        }

    }

    private fun handleDrivingLicensePartial(
        partialDoc: MrzDocument.DrivingLicense,
        bitmap: Bitmap,
        rotation: Int
    ) {
        probabilityAggregator.addFrame(partialDoc)

        val progress = probabilityAggregator.getConfidenceProgress()
        resultFlow.trySend(MrzScanState.Processing(progress))

        if (probabilityAggregator.isConfident()) {
            val finalDoc = probabilityAggregator.getResult()
            Log.i("PROBABILITY", "Vencedor Confirmado: ${finalDoc.documentNumber}")

            // ★ FIX: send diretamente aqui, sem scope.launch interior
            // O canal callbackFlow aguenta — trySend é suficiente se o buffer não estiver cheio,
            // mas para Success usamos send (suspending) via o scope pai já existente
            resultFlow.trySend(MrzScanState.Success(document = finalDoc))

            probabilityAggregator.reset()
        }
    }


    private fun handleParseMrzResult(parseResult: MrzParseResult){
        when (parseResult) {
            is MrzParseResult.Success -> {
                Log.i("MRZ_SUCCESS", "Documento Criado: ${parseResult.document.documentNumber}")
                handleSuccessfulParse(parseResult.document)
            }
            is MrzParseResult.InvalidChecksum -> {
                Log.w("MRZ_WARN", "Checksum falhou, mas estrutura detectada.")
                // Envia confiança média para o usuário saber que deve manter a posição
                resultFlow.trySend(MrzScanState.Processing(0.6f))
                consecutiveSuccessCount = 0
            }
            else -> {
                consecutiveSuccessCount = 0
            }
        }
    }

    /**
     * Lida com parse bem-sucedido
     * Requer múltiplas confirmações antes de emitir sucesso
     */
    private fun handleSuccessfulParse(document: MrzDocument) {

        // Verificar se é o mesmo documento do frame anterior
        val isSameDocument = lastDetectedDocument?.let { last ->
            when {
                last is MrzDocument.IdCard && document is MrzDocument.IdCard ->
                    last.documentNumber == document.documentNumber

                last is MrzDocument.Passport && document is MrzDocument.Passport ->
                    last.documentNumber == document.documentNumber

                last is MrzDocument.DrivingLicense && document is MrzDocument.DrivingLicense ->
                    last.documentNumber == document.documentNumber

                else -> false
            }
        } ?: false

        if (isSameDocument) {
            // Mesmo documento detectado - incrementar contador
            consecutiveSuccessCount++

            // Feedback progressivo de confiança
            val confidence = (consecutiveSuccessCount.toFloat() / requiredSuccessFrames)
                .coerceIn(0f, 1f)
            resultFlow.trySend(MrzScanState.Processing(confidence))

            // CONFIRMAÇÃO FINAL
            if (consecutiveSuccessCount >= requiredSuccessFrames) {
                successfulReads++

                // Emitir sucesso APENAS após múltiplas confirmações
                resultFlow.trySend(MrzScanState.Success(document))

                // Resetar para permitir nova detecção
                consecutiveSuccessCount = 0
                lastDetectedDocument = null
            }
        } else {
            // Documento diferente - começar nova contagem
            consecutiveSuccessCount = 1
            lastDetectedDocument = document
        }
    }

    /**
     * Limpa uma linha de texto para processamento MRZ
     */
    private fun cleanLine(line: String): String {
        return line
            .uppercase(Locale.ROOT) // MRZ é sempre maiúsculas
            .replace(" ", "") // Remove espaços
            .replace("«", "<") // Corrigir caracteres mal lidos
            .replace("»", "<")
            .replace("°", "0")
//            .replace("o", "0") // OCR pode confundir O com 0
//            .replace("O", "0")
            .replace("l", "1") // l minúsculo com 1
            .replace("|", "I") // pipe com I
    }

    /**
     * Verifica se uma linha tem características de linha MRZ
     */
    private fun isValidMrzLine(line: String): Boolean {
        // Linha MRZ deve ter:
        // - Tamanho mínimo de 28 caracteres
        // - Apenas caracteres permitidos: A-Z, 0-9, <
        // - Densidade razoável de caracteres '<' (filler)

        if (line.length < 28) return false

        // Verificar se contém apenas caracteres MRZ válidos
        val validChars = line.all { char ->
            char in 'A'..'Z' || char in '0'..'9' || char == '<'
        }

        if (!validChars) return false

        // Verificar se tem número razoável de '<' (entre 10% e 60% da linha)
        val fillerCount = line.count { it == '<' }
        val fillerRatio = fillerCount.toFloat() / line.length

        return fillerRatio in 0.1f..0.6f
    }

}
