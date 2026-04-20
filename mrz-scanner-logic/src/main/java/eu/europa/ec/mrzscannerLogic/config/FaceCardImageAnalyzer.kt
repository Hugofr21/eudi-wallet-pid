package eu.europa.ec.mrzscannerLogic.config

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Paint
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
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class FaceCardImageAnalyzer(
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

    private val segmenter: Segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
        Segmentation.getClient(options)
    }


    /**
     * Pipeline principal de análise de frames provenientes da câmera em tempo real.
     *
     * Esta função implementa controlo de concorrência, limitação de taxa (throttling) e gestão
     * de ciclo de vida do ImageProxy, garantindo que cada frame é processado de forma segura
     * sem bloqueio da pipeline da CameraX.
     *
     * O processamento segue duas vias condicionais dependendo do formato da imagem:
     * - YUV_420_888: utilização direta via InputImage.fromMediaImage (menor custo computacional).
     * - Outros formatos (ex.: RGBA_8888): conversão intermédia para Bitmap.
     *
     * O sistema executa deteção facial através de um serviço ML Kit abstraído (FaceService),
     * e apenas continua o pipeline caso existam faces detetadas.
     *
     * A função garante:
     * - Exclusão de concorrência via AtomicBoolean.
     * - Throttling temporal para evitar sobrecarga do pipeline.
     * - Liberação garantida do ImageProxy.
     *
     * Limitações estruturais:
     * - Conversão para Bitmap introduz overhead significativo em dispositivos de gama baixa.
     * - O pipeline assume que pelo menos uma face relevante existe antes de avançar.
     * - Dependência direta de ML Kit pode introduzir variabilidade de latência.
     *
     * @param imageProxy Frame de entrada fornecido pelo CameraX ImageAnalysis.
     */

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()

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

            var inputImage: InputImage? = null
            var cachedBitmap: Bitmap? = null

            if (imageProxy.format == ImageFormat.YUV_420_888) {
                inputImage = InputImage.fromMediaImage(mediaImage, rotation)
            } else {
                cachedBitmap = imageProxy.toBitmap()
                inputImage = InputImage.fromBitmap(cachedBitmap, 0)
            }

            faceService.detectFaces(inputImage!!)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        try {
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
                    imageProxy.close()
                    isProcessing.set(false)
                }

        } catch (e: Exception) {
            Log.e("FaceAnalyzer", "Erro crítico: ${e.message}")
            imageProxy.close()
            isProcessing.set(false)
        }
    }

    /**
     * Processa o resultado da deteção facial e valida a qualidade geométrica da face principal.
     *
     * A função seleciona a face dominante com base na área do bounding box, assumindo
     * que esta corresponde ao sujeito principal da imagem.
     *
     * A validação de qualidade é baseada em heurísticas geométricas (cobertura relativa da face
     * na imagem e coerência espacial), o que implica ausência de robustez semântica.
     *
     * Quando a qualidade é considerada estável por múltiplos frames consecutivos,
     * inicia-se o pipeline de segmentação de fundo com expansão contextual do bounding box
     * para melhorar a estabilidade do modelo de segmentação.
     *
     * Riscos técnicos:
     * - Seleção por área pode falhar em cenários com múltiplos rostos de tamanho semelhante.
     * - Expansão do bounding box pode introduzir clipping em bordas da imagem.
     * - Estado interno (consecutiveSuccesses) introduz dependência temporal (stateful pipeline).
     *
     * @param faces Lista de faces detetadas pelo ML Kit.
     * @param originalBitmap Frame original convertido.
     */
    private fun processDetectedFaces(faces: List<Face>, originalBitmap: Bitmap) {
        if (faces.isEmpty()) {
            consecutiveSuccesses = 0
            return
        }

        Log.i("FaceAnalyzer", "Faces detectadas: ${faces.size}")

        val mainFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return

        if (isFaceQualityGood(mainFace, originalBitmap.width, originalBitmap.height)) {
            consecutiveSuccesses++
            resultFlow.trySend(MrzScanState.Processing(1f))

            if (consecutiveSuccesses >= requiredSuccessFrames) {
                try {
                    val expandedBounds = Rect(
                        maxOf(0, mainFace.boundingBox.left - mainFace.boundingBox.width()),
                        maxOf(0, mainFace.boundingBox.top - mainFace.boundingBox.height()),
                        minOf(originalBitmap.width, mainFace.boundingBox.right + mainFace.boundingBox.width()),
                        minOf(originalBitmap.height, mainFace.boundingBox.bottom + mainFace.boundingBox.height())
                    )

                    val contextBitmap = Bitmap.createBitmap(
                        originalBitmap,
                        expandedBounds.left,
                        expandedBounds.top,
                        expandedBounds.width(),
                        expandedBounds.height()
                    )

                    Log.i("FaceAnalyzer", "Iniciando segmentação com contexto...")
                    processSegmentation(contextBitmap, mainFace)

                } catch (e: Exception) {
                    Log.e("FaceAnalyzer", "Erro na preparação: ${e.message}")
                    consecutiveSuccesses = 0
                }
            }
        } else {
            consecutiveSuccesses = 0
        }
    }

    /**
     * Executa segmentação de foreground (selfie segmentation) e constrói a imagem final
     * centrada e normalizada.
     *
     * Esta etapa aplica segmentação semântica através do ML Kit Selfie Segmenter,
     * gerando uma máscara de probabilidade por pixel que distingue sujeito e fundo.
     *
     * O pipeline executa:
     * 1. Segmentação do conteúdo expandido (contexto adicional ao rosto).
     * 2. Aplicação da máscara para remoção de fundo.
     * 3. Correção de coordenadas relativas devido ao crop expandido.
     * 4. Recorte da face com proporção fixa (documentação tipo passe).
     * 5. Recentração final em canvas branco.
     *
     * Limitações críticas:
     * - A qualidade da segmentação depende fortemente da iluminação e contraste.
     * - A compensação de coordenadas é sensível a erros acumulados de cropping.
     * - O modelo ML Kit não garante consistência temporal entre frames.
     *
     * @param contextBitmap Imagem expandida contendo contexto adicional ao rosto.
     * @param mainFace Face principal detetada no frame original.
     */

    private fun processSegmentation(contextBitmap: Bitmap, mainFace: Face) {
        val safeBitmap = contextBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val inputImage = InputImage.fromBitmap(safeBitmap, 0)

        segmenter.process(inputImage)
            .addOnSuccessListener { segmentationMask ->
                scope.launch(dispatcher) {
                    try {

                        val bitmapWithWhiteBg = applyBackgroundMask(
                            image = safeBitmap,
                            maskBuffer = segmentationMask.buffer,
                            maskWidth = segmentationMask.width,
                            maskHeight = segmentationMask.height
                        )


                        val offsetX = maxOf(0, mainFace.boundingBox.left - mainFace.boundingBox.width())
                        val offsetY = maxOf(0, mainFace.boundingBox.top - mainFace.boundingBox.height())
                        val localFaceBounds = Rect(
                            mainFace.boundingBox.left - offsetX,
                            mainFace.boundingBox.top - offsetY,
                            mainFace.boundingBox.right - offsetX,
                            mainFace.boundingBox.bottom - offsetY
                        )

                        val faceBitmapExpanded = cropFace(bitmapWithWhiteBg, localFaceBounds)

                        val finalCenteredBitmap = centerContentOnWhiteCanvas(faceBitmapExpanded)

                        Log.i("FaceAnalyzer", "Imagem final segmentada e centrada com sucesso.")
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

    /**
     * Aplica uma máscara de segmentação semântica sobre uma imagem de alta resolução,
     * removendo o fundo com base em probabilidades por pixel.
     *
     * A máscara fornecida pelo ML Kit é redimensionada implicitamente para o espaço da imagem
     * original através de interpolação linear por escala (scaleX, scaleY).
     *
     * Cada pixel da imagem é classificado com base na confiança da máscara:
     * - confidence < threshold → considerado fundo e substituído por branco.
     * - confidence ≥ threshold → preserva pixel original.
     *
     * Este método implementa uma forma simplificada de matting binário,
     * sem refinamento de borda ou suavização alfa, o que pode introduzir aliasing
     * em regiões de transição.
     *
     * Problemas técnicos conhecidos:
     * - Possível desalinhamento entre máscara e imagem devido a downsampling do ML Kit.
     * - Operação O(n²) altamente custosa em imagens de alta resolução.
     * - Ausência de blur de borda (edge feathering), resultando em recortes rígidos.
     *
     * @param image Bitmap original de entrada.
     * @param maskBuffer Buffer de saída do ML Kit segmentation.
     * @param maskWidth Largura da máscara.
     * @param maskHeight Altura da máscara.
     * @return Bitmap com fundo removido (substituído por branco).
     */

    private fun applyBackgroundMask(
        image: Bitmap,
        maskBuffer: ByteBuffer,
        maskWidth: Int,
        maskHeight: Int
    ): Bitmap {
        val imgWidth = image.width
        val imgHeight = image.height
        maskBuffer.order(ByteOrder.nativeOrder())
        maskBuffer.rewind()

        val confidences = FloatArray(maskWidth * maskHeight)
        maskBuffer.asFloatBuffer().get(confidences)

        val pixels = IntArray(imgWidth * imgHeight)
        image.getPixels(pixels, 0, imgWidth, 0, 0, imgWidth, imgHeight)

        val scaleX = maskWidth.toFloat() / imgWidth
        val scaleY = maskHeight.toFloat() / imgHeight


        for (y in 0 until imgHeight) {
            for (x in 0 until imgWidth) {
                val maskX = (x * scaleX).toInt().coerceIn(0, maskWidth - 1)
                val maskY = (y * scaleY).toInt().coerceIn(0, maskHeight - 1)
                val maskIndex = (maskY * maskWidth) + maskX
                val confidence = confidences[maskIndex]


                if (confidence < 0.85f) {
                    val pixelIndex = (y * imgWidth) + x
//                    pixels[pixelIndex] = Color.TRANSPARENT
                    pixels[pixelIndex] = Color.WHITE
                }
            }
        }

        val resultBitmap = Bitmap.createBitmap(imgWidth, imgHeight, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(pixels, 0, imgWidth, 0, 0, imgWidth, imgHeight)

        return resultBitmap
    }


    /**
     * Avalia a qualidade geométrica da face detetada com base em critérios heurísticos
     * de cobertura e consistência espacial.
     *
     * A métrica principal utilizada é a proporção da área da face em relação à área total
     * da imagem, assumindo que faces demasiado pequenas indicam deteção não confiável.
     *
     * Critérios aplicados:
     * - Cobertura mínima da face na imagem (threshold empírico de 1%).
     * - Rejeição de bounding boxes completamente fora dos limites da imagem.
     *
     * Esta abordagem não avalia qualidade visual (nitidez, blur ou oclusão),
     * sendo estritamente geométrica.
     *
     * Limitações:
     * - Não deteta faces desfocadas ou parcialmente ocluídas.
     * - Sensível a resolução da imagem (heurística não normalizada por DPI real).
     * - Pode rejeitar casos válidos em imagens de alta resolução com faces pequenas.
     *
     * @param face Objeto Face detetado pelo ML Kit.
     * @param imgWidth Largura da imagem.
     * @param imgHeight Altura da imagem.
     * @return true se a face satisfaz critérios mínimos de qualidade.
     */
    private fun isFaceQualityGood(face: Face, imgWidth: Int, imgHeight: Int): Boolean {
        val bounds = face.boundingBox

        Log.d("FaceQuality", "Face Bounds: $bounds | Image: ${imgWidth}x${imgHeight}")
        val faceArea = bounds.width() * bounds.height()
        val imageArea = imgWidth * imgHeight
        val coverage = faceArea.toFloat() / imageArea.toFloat()

        Log.d("FaceQuality", "Coverage: $coverage (Minimum required: 0.01)")

        if (coverage < 0.01f) {
            Log.w("FaceQuality", "REJECTED: Face too small.")
            return false
        }

        if (bounds.right < 0 || bounds.left > imgWidth || bounds.bottom < 0 || bounds.top > imgHeight) {
            Log.w("FaceQuality", "REJECTED: Face completely outside the image.")
            return false
        }

        return true
    }

    /**
     * Recorta a região da face e força conformidade com proporção fotográfica oficial
     * (tipo passe 35x45mm).
     *
     * O algoritmo ajusta dinamicamente a região de recorte com base no centro da face,
     * aplicando expansão vertical para incluir contexto facial adicional (testa e queixo).
     *
     * O bounding box é normalizado com:
     * - Clamping de coordenadas para evitar out-of-bounds.
     * - Ajuste de largura/altura para manter proporção alvo.
     *
     * Limitações técnicas:
     * - Não preserva exatamente o bounding box original, introduzindo distorção controlada.
     * - Pode cortar partes relevantes da face em caso de deteções próximas das bordas.
     * - Dependência de heurística fixa de proporção pode não generalizar para todos os standards biométricos.
     *
     * @param bitmap Imagem de entrada já segmentada.
     * @param faceBounds Bounding box da face detetada.
     * @return Bitmap recortado com proporção fixa biométrica.
     */
    private fun cropFace(bitmap: Bitmap, faceBounds: Rect): Bitmap {
        val targetRatio = 35f / 45f

        val faceWidth = faceBounds.width()
        val faceHeight = faceBounds.height()

        val finalHeight = (faceHeight * 2.0f).toInt()
        val finalWidth = (finalHeight * targetRatio).toInt()

        val centerX = faceBounds.centerX()
        val centerY = faceBounds.centerY()

        var x = centerX - (finalWidth / 2)
        var y = centerY - (finalHeight * 0.45f).toInt()

        x = x.coerceAtLeast(0)
        y = y.coerceAtLeast(0)

        var w = finalWidth
        var h = finalHeight

        if (x + w > bitmap.width) {
            w = bitmap.width - x
        }

        if (y + h > bitmap.height) {
            h = bitmap.height - y
        }

        if (w <= 0 || h <= 0) {
            Log.w("FaceAnalyzer", "Warning in cropFace: Invalid crop dimensions (w=$w, h=$h). Returning the original image.")
            return bitmap
        }

        return Bitmap.createBitmap(bitmap, x, y, w, h)
    }

    /**
     * Centraliza o conteúdo visível de uma imagem num canvas branco através de análise
     * de intensidade RGB por limiar fixo.
     *
     * O algoritmo identifica o bounding box do conteúdo com base na detecção de pixels
     * não-brancos (RGB < 240 em qualquer canal), assumindo fundo uniforme branco.
     *
     * Pipeline:
     * 1. Extração de matriz de pixels.
     * 2. Identificação de bounding box do foreground.
     * 3. Recorte da região relevante.
     * 4. Redesenho centrado em canvas branco.
     *
     * Esta abordagem é uma heurística de segmentação baseada em intensidade,
     * não sendo robusta a:
     * - Sombras suaves.
     * - Compressão JPEG.
     * - Fundos não uniformes.
     *
     * Implicações técnicas:
     * - Pode gerar bounding boxes incorretas em condições reais de iluminação.
     * - Não substitui segmentação semântica.
     * - Opera em O(n) sobre todos os pixels da imagem.
     *
     * @param inputBitmap Imagem de entrada já processada.
     * @return Bitmap recortado e centrado sobre fundo branco.
     */
    private fun centerContentOnWhiteCanvas(inputBitmap: Bitmap): Bitmap {
        val width = inputBitmap.width
        val height = inputBitmap.height
        val pixels = IntArray(width * height)
        inputBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        var foundContent = false

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]

                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                if (r < 240 || g < 240 || b < 240) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    foundContent = true
                }
            }
        }

        if (!foundContent || minX > maxX || minY > maxY) {
            Log.w("FaceAnalyzer", "Warning: No content detected outside the white background. Returning to original.")
            return inputBitmap
        }

        val contentWidth = maxX - minX + 1
        val contentHeight = maxY - minY + 1

        val finalCenteredBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalCenteredBitmap)
        canvas.drawColor(Color.WHITE)

        val drawX = (width - contentWidth) / 2f
        val drawY = (height - contentHeight) / 2f

        val contentOnlyBitmap = Bitmap.createBitmap(inputBitmap, minX, minY, contentWidth, contentHeight)

        val paint = Paint().apply { isAntiAlias = true }
        canvas.drawBitmap(contentOnlyBitmap, drawX, drawY, paint)

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