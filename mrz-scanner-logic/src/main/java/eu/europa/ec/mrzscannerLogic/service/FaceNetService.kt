package eu.europa.ec.mrzscannerLogic.service

import android.graphics.Bitmap
import androidx.core.graphics.scale
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Transfer object that encapsulates the result of the biometric inference.

 * @param isVerified True if the similarity exceeds the security threshold.

 * @param similarityScore Exact value of the cosine distance (0.0 to 1.0).

 * @param label Descriptive message ready to be consumed by the presentation layer (Frontend).

 */

data class FaceVerificationResult(
    val isVerified: Boolean,
    val similarityScore: Float,
    val label: String
)

interface FaceNetService {
    suspend fun loadModel()
    suspend fun setReferenceIdentity(referenceFaceCrop: Bitmap)
    suspend fun verifyIdentity(liveFaceCrop: Bitmap): FaceVerificationResult
    fun close()
}

class FaceNetServiceImpl(
    private val context: ResourceProvider,
    private val modelFilename: String = "mobilefacenet.tflite"
) : FaceNetService {

    private var interpreter: Interpreter? = null
    private var referenceEmbedding: FloatArray? = null

    private val inputImageSize = 112
    private val outputVectorSize = 192
    private val bytesPerChannel = 4
    private val channels = 3

    private val acceptanceThreshold = 0.65f

    override suspend fun loadModel() = withContext(Dispatchers.IO) {
        if (interpreter != null) return@withContext

        val fileDescriptor = context.provideContext().assets.openFd(modelFilename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

        val options = Interpreter.Options().apply {
            numThreads = 4
        }
        interpreter = Interpreter(modelBuffer, options)
    }

    override suspend fun setReferenceIdentity(referenceFaceCrop: Bitmap) = withContext(Dispatchers.Default) {
        if (interpreter == null) {
            throw IllegalStateException("The biometric model interpreter has not been initialized in the system.")
        }
        referenceEmbedding = extractEmbedding(referenceFaceCrop)
    }

    override suspend fun verifyIdentity(liveFaceCrop: Bitmap): FaceVerificationResult = withContext(Dispatchers.Default) {
        val currentInterpreter = interpreter ?: throw IllegalStateException("The inference mechanism is inactive.")
        val anchorEmbedding = referenceEmbedding ?: throw IllegalStateException("The reference identity has not been previously established in the service.")

        val liveEmbedding = extractEmbedding(liveFaceCrop, currentInterpreter)

        val cosineDistance = similarityCosen(anchorEmbedding, liveEmbedding)

        val isVerified = cosineDistance >= acceptanceThreshold

        val similarityPercentage = (cosineDistance * 100).toInt()
        val labelMessage = if (isVerified) {
            "Identity confirmed ($similarityPercentage% match)"
        } else {
            "Identity not recognized (Only $similarityPercentage% similarity)"
        }

        return@withContext FaceVerificationResult(
            isVerified = isVerified,
            similarityScore = cosineDistance,
            label = labelMessage
        )
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        referenceEmbedding = null
    }

    private fun extractEmbedding(bitmap: Bitmap, activeInterpreter: Interpreter? = interpreter): FloatArray {
        val engine = activeInterpreter ?: throw IllegalStateException("Missing matrix engine for inference.")
        val scaledBitmap = bitmap.scale(inputImageSize, inputImageSize)
        val tensorBuffer = normalizePixelDataToBuffer(scaledBitmap)
        val outputFeatureVector = Array(1) { FloatArray(outputVectorSize) }

        engine.run(tensorBuffer, outputFeatureVector)

        val rawVector = outputFeatureVector[0]
        var sumSquares = 0.0f
        for (value in rawVector) {
            sumSquares += value * value
        }
        val norm = sqrt(sumSquares.toDouble()).toFloat()

        val normalizedVector = FloatArray(outputVectorSize)
        if (norm > 1e-6f) {
            for (i in rawVector.indices) {
                normalizedVector[i] = rawVector[i] / norm
            }
        }
        return normalizedVector
    }

    private fun normalizePixelDataToBuffer(bitmap: Bitmap): ByteBuffer {
        val requiredCapacity = inputImageSize * inputImageSize * channels * bytesPerChannel
        val byteBuffer = ByteBuffer.allocateDirect(requiredCapacity)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixelArray = IntArray(inputImageSize * inputImageSize)
        bitmap.getPixels(pixelArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixelValue in pixelArray) {
            byteBuffer.putFloat(((pixelValue shr 16 and 0xFF) - 127.5f) / 128f)
            byteBuffer.putFloat(((pixelValue shr 8 and 0xFF) - 127.5f) / 128f)
            byteBuffer.putFloat(((pixelValue and 0xFF) - 127.5f) / 128f)
        }
        return byteBuffer
    }

    private fun similarityCosen(v1: FloatArray, v2: FloatArray): Float {
        var productDot = 0.0f
        var norm1 = 0.0f
        var norm2 = 0.0f

        for (i in v1.indices) {
            productDot += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }
        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator < 1e-6) 0.0f else productDot / denominator
    }
}