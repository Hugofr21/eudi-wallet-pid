import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import eu.europa.ec.mrzscannerLogic.controller.MrzScanState
import eu.europa.ec.mrzscannerLogic.model.MrzDocument
import eu.europa.ec.mrzscannerLogic.service.DriverLicenseParseService
import eu.europa.ec.mrzscannerLogic.service.MrzParseResult
import eu.europa.ec.mrzscannerLogic.service.MrzParserService
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
    private val throttleMs: Long = 150L, // Processa ~6-7 frames/segundo
    private val roiBottomFraction: Float = 0.3f, // Foca nos 30% inferiores
    private val requiredSuccessFrames: Int = 2 // Requer 2 leituras consecutivas
) : ImageAnalysis.Analyzer {

    private val isProcessing = AtomicBoolean(false)
    @Volatile private var lastProcessTime = 0L

    // Controle de confirmação multi-frame
    private var consecutiveSuccessCount = 0
    private var lastDetectedDocument: MrzDocument? = null

    // Estatísticas (para debug)
    private var totalFrames = 0L
    private var processedFrames = 0L
    private var successfulReads = 0L

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

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        // Processar de forma assíncrona
        scope.launch(dispatcher) {
            try {
                // OCR no frame atual
                val textResult = textRecognitionService.recognizeText(inputImage, rotation)

                textResult.onSuccess { recognizedText ->
                    processRecognizedText(
                        recognizedText = recognizedText,
                        imageHeight = imageProxy.height,
                        imageWidth = imageProxy.width,
                        rotation = rotation
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
        recognizedText: String,
        imageHeight: Int,
        imageWidth: Int,
        rotation: Int
    ) {
        // Debug para confirmar entrada
        if (recognizedText.isNotEmpty()) {
            Log.d("MRZ_RAW", "OCR Bruto:\n$recognizedText")
        }

        val allLines = recognizedText.split("\n")




        // 1. Limpeza Prévia: Remove espaços e normaliza caracteres confusos
        // Filtragem Inteligente: Mantém APENAS linhas que parecem MRZ
        // Critério: Tamanho > 20 e contém '<<' ou dígitos suficientes

        val mrzCandidates = allLines
            .map { cleanLine(it) }
            .filter { isValidMrzLine(it) }


        // Se não sobrarem linhas candidatas suficientes, aborta sem resetar bruscamente
        if (mrzCandidates.size >= 2) {
            Log.d("MRZ_PROC", "Tentando Parse com linhas limpas: $mrzCandidates")
            // 3. Tentativa de Parse
            val parseResult = parserService.parse(mrzCandidates)
            handleParseMrzResult(parseResult)
        }else{
            Log.d("VISUAL_PROC", "Tentando Parse Visual (Carta Condução)...")

            val dlResult = driverLicenseParser.parse(allLines)

            if (dlResult is MrzParseResult.Success) {
                Log.i("VISUAL_SUCCESS", "Carta detetada: ${dlResult.document.documentNumber}")
                handleSuccessfulParse(dlResult.document)

            } else {
                consecutiveSuccessCount = 0
            }
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
            .replace("o", "0") // OCR pode confundir O com 0
            .replace("O", "0")
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

    /**
     * Calcula confiança baseado na qualidade do texto reconhecido
     */
    private fun calculateConfidence(text: String): Float {
        val lines = text.split("\n").filter { it.isNotBlank() }

        if (lines.isEmpty()) return 0f

        // Fatores de confiança:
        // 1. Número de linhas (mais linhas = melhor)
        val linesFactor = (lines.size.toFloat() / 10f).coerceIn(0f, 1f)

        // 2. Presença de caracteres '<' (indicativo de MRZ)
        val fillerFactor = if (text.contains('<')) 0.3f else 0f

        // 3. Densidade de caracteres maiúsculos
        val uppercaseRatio = text.count { it.isUpperCase() }.toFloat() / text.length
        val uppercaseFactor = uppercaseRatio.coerceIn(0f, 0.3f)

        return (linesFactor + fillerFactor + uppercaseFactor).coerceIn(0f, 1f)
    }

    /**
     * Obtém estatísticas de processamento (para debug)
     */
    fun getStatistics(): AnalyzerStatistics {
        return AnalyzerStatistics(
            totalFrames = totalFrames,
            processedFrames = processedFrames,
            successfulReads = successfulReads,
            processingRate = if (totalFrames > 0) {
                (processedFrames.toFloat() / totalFrames * 100)
            } else 0f
        )
    }
}

/**
 * Estatísticas do analyzer (útil para debug e otimização)
 */
data class AnalyzerStatistics(
    val totalFrames: Long,
    val processedFrames: Long,
    val successfulReads: Long,
    val processingRate: Float // Percentagem de frames processados
)