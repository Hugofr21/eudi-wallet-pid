import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import eu.europa.ec.mrzscannerLogic.controller.MrzScanState
import eu.europa.ec.mrzscannerLogic.service.FaceService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class FaceImageAnalyzer(
    private val resultFlow: ProducerScope<MrzScanState>,
    private val faceService: FaceService,
    private val throttleMs: Long = 200L,
    private val requiredSuccessFrames: Int = 3,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ImageAnalysis.Analyzer {

    private val isProcessing = AtomicBoolean(false)
    @Volatile private var lastProcessTime = 0L
    private var consecutiveSuccesses = 0

    // --- NOVO: Inicialização do Segmentador ---
    private val segmenter: Segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            // Pedimos a máscara em tamanho bruto para bater certo com os píxeis do bitmap
            .enableRawSizeMask()
            .build()
        Segmentation.getClient(options)
    }
    // -----------------------------------------

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()

        // Throttle
        if (isProcessing.get() || (currentTime - lastProcessTime) < throttleMs) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        lastProcessTime = currentTime

        try {
            val rotation = imageProxy.imageInfo.rotationDegrees

            // --- CORREÇÃO HÍBRIDA (Universal) ---
            // Verifica o formato para decidir como criar o InputImage
            // Formato 35 = YUV_420_888 (Padrão rápido)
            // Formato 1 = RGBA_8888 (Configuração atual)

            var inputImage: InputImage? = null
            var cachedBitmap: Bitmap? = null

            if (imageProxy.format == android.graphics.ImageFormat.YUV_420_888) {
                // Caminho Rápido (YUV): Passa o buffer direto
                inputImage = InputImage.fromMediaImage(mediaImage, rotation)
            } else {
                // Caminho de Compatibilidade (RGBA): Converte para Bitmap primeiro
                // Nota: toBitmap() já aplica a rotação, por isso passamos 0 no InputImage
                cachedBitmap = imageProxy.toBitmap()
                inputImage = InputImage.fromBitmap(cachedBitmap, 0)
            }

            faceService.detectFaces(inputImage!!)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        try {
                            // Se já convertemos o bitmap antes (caminho RGBA), usamos esse.
                            // Se viemos do caminho YUV, convertemos agora (Lazy Conversion).
                            val bitmapToProcess = cachedBitmap ?: imageProxy.toBitmap()

                            scope.launch(dispatcher) {
                                processDetectedFaces(faces, bitmapToProcess)
                            }
                        } catch (e: Exception) {
                            Log.e("FaceAnalyzer", "Erro ao converter bitmap: ${e.message}")
                        }
                    } else {
                        consecutiveSuccesses = 0
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FaceAnalyzer", "Erro deteção: ${e.message}")
                    consecutiveSuccesses = 0
                }
                .addOnCompleteListener {
                    // O imageProxy é fechado aqui.
                    // Se usámos o toBitmap(), os dados já foram copiados, por isso é seguro fechar.
                    imageProxy.close()
                    isProcessing.set(false)
                }

        } catch (e: Exception) {
            Log.e("FaceAnalyzer", "Erro crítico: ${e.message}")
            imageProxy.close()
            isProcessing.set(false)
        }
    }
    private fun processDetectedFaces(faces: List<Face>, originalBitmap: Bitmap) {
        if (faces.isEmpty()) {
            consecutiveSuccesses = 0
            return
        }

        Log.i("FaceAnalyzer", "Faces detectadas: ${faces.size}")


        // Pega na maior cara (assumindo que focou o cartão)
        val mainFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return

        if (isFaceQualityGood(mainFace, originalBitmap.width, originalBitmap.height)) {
            consecutiveSuccesses++

            // Feedback
            resultFlow.trySend(MrzScanState.Processing(1f))

            if (consecutiveSuccesses >= requiredSuccessFrames) {
                try {

                    // 1. Recorta a imagem expandida (como já fazia)
                    val faceBitmapExpanded = cropFace(originalBitmap, mainFace.boundingBox)
                    Log.i("FaceAnalyzer", "Sucesso! Face recortada, iniciando remoção de fundo...")

                    // --- ALTERADO: Em vez de enviar logo, inicia a segmentação ---
                    processSegmentation(faceBitmapExpanded)

                } catch (e: Exception) {
                    Log.e("FaceAnalyzer", "Erro crop: ${e.message}")
                    consecutiveSuccesses = 0
                }
            }
        } else {
            consecutiveSuccesses = 0
        }
    }

    // --- NOVO: Processamento da Segmentação Mais Seguro ---
    private fun processSegmentation(croppedBitmap: Bitmap) {
        // --- CORREÇÃO DE BITMAP PRETO ---
        // 1. Forçar cópia para memória SOFTWARE (ARGB_8888).
        // Se usar o bitmap direto da câmara, o getPixels() pode falhar silenciosamente.
        val safeBitmap = croppedBitmap.copy(Bitmap.Config.ARGB_8888, true)

        val inputImage = InputImage.fromBitmap(safeBitmap, 0)

        segmenter.process(inputImage)
            .addOnSuccessListener { segmentationMask ->
                scope.launch(dispatcher) {
                    try {
                        // O ML Kit devolve geralmente 256x256. A imagem pode ser 500x700.
                        // NÃO FAZEMOS verificação de igualdade. Passamos tudo para a função aplicar.
                        val bitmapWithWhiteBg = applyBackgroundMask(
                            image = safeBitmap,
                            maskBuffer = segmentationMask.buffer,
                            maskWidth = segmentationMask.width,
                            maskHeight = segmentationMask.height
                        )

                        Log.i("FaceAnalyzer", "Fundo branco aplicado. A recentrar conteúdo...")

                        // --- NOVO PASSO 2: Recentrar o conteúdo na tela branca ---
                        val finalCenteredBitmap = centerContentOnWhiteCanvas(bitmapWithWhiteBg)

                        Log.i("FaceAnalyzer", "Imagem final centrada. Enviando para UI...")
                        resultFlow.trySend(MrzScanState.Success(capturedImage = finalCenteredBitmap))
                        consecutiveSuccesses = 0

                    } catch (e: Exception) {
                        Log.e("FaceAnalyzer", "Erro ao processar fundo", e)
                        consecutiveSuccesses = 0
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("FaceAnalyzer", "Erro ML Kit: ${e.message}")
                consecutiveSuccesses = 0
            }
    }

    // --- NOVO: Aplicação Otimizada (Rápida e Segura) ---
    private fun applyBackgroundMask(
        image: Bitmap,
        maskBuffer: ByteBuffer,
        maskWidth: Int,
        maskHeight: Int
    ): Bitmap {
        val imgWidth = image.width
        val imgHeight = image.height

        // 1. CORREÇÃO DE DADOS (CRÍTICO): Definir a ordem dos bytes
        // Sem isto, os números vêm errados e a máscara falha.
        maskBuffer.order(java.nio.ByteOrder.nativeOrder())
        maskBuffer.rewind()

        // 2. Ler a máscara para um array (Rápido)
        val confidences = FloatArray(maskWidth * maskHeight)
        maskBuffer.asFloatBuffer().get(confidences)

        // 3. Obter os píxeis da imagem original (Alta Resolução)
        val pixels = IntArray(imgWidth * imgHeight)
        image.getPixels(pixels, 0, imgWidth, 0, 0, imgWidth, imgHeight)

        // 4. Calcular a Escala (Para esticar a máscara pequena sobre a imagem grande)
        val scaleX = maskWidth.toFloat() / imgWidth
        val scaleY = maskHeight.toFloat() / imgHeight

        // 5. Loop de Processamento
        for (y in 0 until imgHeight) {
            for (x in 0 until imgWidth) {
                // Encontrar o píxel correspondente na máscara pequena
                val maskX = (x * scaleX).toInt().coerceIn(0, maskWidth - 1)
                val maskY = (y * scaleY).toInt().coerceIn(0, maskHeight - 1)
                val maskIndex = (maskY * maskWidth) + maskX

                // Valor da máscara: 0.0 (Fundo) a 1.0 (Pessoa)
                val confidence = confidences[maskIndex]

                // LÓGICA DE TRANSPARÊNCIA:
                // Se a confiança for baixa (< 0.8), é fundo -> Torna Transparente.
                // Caso contrário, mantém o píxel original da foto.
                if (confidence < 0.85f) { // Ajuste 0.85f para limpar bem as bordas
                    val pixelIndex = (y * imgWidth) + x
//                    pixels[pixelIndex] = Color.TRANSPARENT
                    pixels[pixelIndex] = Color.WHITE
                }
            }
        }

        // 6. Criar Bitmap Final
        // Config.ARGB_8888 é obrigatório para suportar transparência
        val resultBitmap = Bitmap.createBitmap(imgWidth, imgHeight, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(pixels, 0, imgWidth, 0, 0, imgWidth, imgHeight)

        return resultBitmap
    }

    private fun isFaceQualityGood(face: Face, imgWidth: Int, imgHeight: Int): Boolean {
        val bounds = face.boundingBox

        // 1. LOG DE DEBUG: Veja isto no Logcat para entender porque falhava
        Log.d("FaceQuality", "Face Bounds: $bounds | Imagem: ${imgWidth}x${imgHeight}")

        // 2. Validação de Tamanho
        val faceArea = bounds.width() * bounds.height()
        val imageArea = imgWidth * imgHeight
        val coverage = faceArea.toFloat() / imageArea.toFloat()

        Log.d("FaceQuality", "Cobertura: $coverage (Mínimo necessário: 0.01)")

        // Reduzi para 1% (0.01f) porque em câmaras de alta resolução (4K),
        // a foto do cartão pode parecer pequena em píxeis.
        if (coverage < 0.01f) {
            Log.w("FaceQuality", "REJEITADO: Face demasiado pequena.")
            return false
        }

        // 3. REMOVIDA A VALIDAÇÃO DE LIMITES ESTRITA
        // Antes você retornava 'false' se left < 0.
        // O ML Kit faz isso muitas vezes.
        // A função 'cropFace' já lida com coordenadas negativas, por isso ACEITAMOS.

        // A única coisa que devemos verificar é se a cara está TOTALMENTE fora (impossível, mas seguro verificar)
        if (bounds.right < 0 || bounds.left > imgWidth || bounds.bottom < 0 || bounds.top > imgHeight) {
            Log.w("FaceQuality", "REJEITADO: Face totalmente fora da imagem.")
            return false
        }

        return true
    }

    /**
     * Recorta a face forçando a proporção oficial de Foto Tipo Passe (35x45mm).
     * Rácio: 35 / 45 = ~0.777
     */
    private fun cropFace(bitmap: Bitmap, faceBounds: Rect): Bitmap {
        // 1. Definição do Aspect Ratio (35mm x 45mm)
        val targetRatio = 35f / 45f

        // 2. Calcular o tamanho base da cabeça detetada
        val faceWidth = faceBounds.width()
        val faceHeight = faceBounds.height()

        // 3. Definir a altura desejada do recorte final
        // A cara deve ocupar cerca de 70% a 80% da altura da foto final.
        // Multiplicamos a altura da cara por 2.0x para ter espaço para cabelo e pescoço.
        val finalHeight = (faceHeight * 2.0f).toInt()

        // 4. Calcular a largura baseada no Rácio 35x45
        val finalWidth = (finalHeight * targetRatio).toInt()

        // 5. Calcular o centro da cara detetada
        val centerX = faceBounds.centerX()
        val centerY = faceBounds.centerY()

        // 6. Calcular coordenadas de origem (Top/Left)
        // Centramos horizontalmente na cara.
        var x = centerX - (finalWidth / 2)

        // Verticalmente, a cara deve estar ligeiramente acima do centro da foto.
        // Subimos o topo para deixar 1/3 de espaço acima dos olhos (testa) e 2/3 abaixo.
        // O centerY do ML Kit é o meio da cara.
        var y = centerY - (finalHeight * 0.45f).toInt()

        // 7. Proteção de Limites (Clamping)
        // Garante que o quadrado de recorte não sai fora da imagem original

        // Ajuste Esquerda/Direita
        if (x < 0) x = 0
        if (x + finalWidth > bitmap.width) x = bitmap.width - finalWidth

        // Ajuste Topo/Baixo
        if (y < 0) y = 0
        if (y + finalHeight > bitmap.height) y = bitmap.height - finalHeight

        // Validação final de segurança (caso a imagem seja muito pequena)
        val validWidth = finalWidth.coerceAtMost(bitmap.width)
        val validHeight = finalHeight.coerceAtMost(bitmap.height)

        if (validWidth <= 0 || validHeight <= 0) {
            // Fallback se o cálculo falhar: retorna apenas a cara detetada
            return Bitmap.createBitmap(
                bitmap,
                faceBounds.left,
                faceBounds.top,
                faceBounds.width(),
                faceBounds.height()
            )
        }

        return Bitmap.createBitmap(bitmap, x, y, validWidth, validHeight)
    }

    /**
     * NOVO: Pega numa imagem com fundo branco, deteta os limites do conteúdo
     * (a pessoa) e redesenha esse conteúdo perfeitamente centrado numa nova tela branca.
     */
    private fun centerContentOnWhiteCanvas(inputBitmap: Bitmap): Bitmap {
        val width = inputBitmap.width
        val height = inputBitmap.height
        val pixels = IntArray(width * height)
        inputBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Encontrar os limites (bounding box) dos píxeis que NÃO são brancos
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        var foundContent = false

        // Cor branca pura em inteiro
        val whitePixel = Color.WHITE

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                // Se o píxel não for branco puro, faz parte da pessoa
                if (pixel != whitePixel) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    foundContent = true
                }
            }
        }

        // Se por algum motivo a imagem for toda branca (erro de segmentação), devolve a original
        if (!foundContent || minX > maxX || minY > maxY) {
            return inputBitmap
        }

        // Dimensões reais do conteúdo (a pessoa recortada)
        val contentWidth = maxX - minX + 1
        val contentHeight = maxY - minY + 1

        // 2. Criar uma nova tela final, preenchida com Branco
        // Usamos ARGB_8888 para garantir qualidade, embora não precisemos de transparência aqui.
        val finalCenteredBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(finalCenteredBitmap)
        canvas.drawColor(Color.WHITE) // Preenche o fundo de branco

        // 3. Calcular as coordenadas para desenhar o conteúdo centrado
        // Posição X para centrar horizontalmente
        val drawX = (width - contentWidth) / 2f
        // Posição Y para centrar verticalmente
        val drawY = (height - contentHeight) / 2f

        // 4. Recortar apenas o conteúdo da imagem original
        val contentOnlyBitmap = Bitmap.createBitmap(inputBitmap, minX, minY, contentWidth, contentHeight)

        // 5. Desenhar o conteúdo na posição centrada na nova tela branca
        // Usamos um Paint com anti-aliasing para suavizar as bordas
        val paint = android.graphics.Paint().apply { isAntiAlias = true }
        canvas.drawBitmap(contentOnlyBitmap, drawX, drawY, paint)

        // Limpar bitmaps intermédios para poupar memória
        contentOnlyBitmap.recycle()
        if (inputBitmap != finalCenteredBitmap) {
            inputBitmap.recycle()
        }

        return finalCenteredBitmap
    }
    fun close() {
        segmenter.close()
    }
}