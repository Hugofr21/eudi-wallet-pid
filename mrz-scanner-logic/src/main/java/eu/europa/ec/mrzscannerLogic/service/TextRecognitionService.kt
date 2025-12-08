package eu.europa.ec.mrzscannerLogic.service

import  com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.latin.TextRecognizerOptions


interface TextRecognitionService {
    suspend fun recognizeText(image: Any, rotation: Int): Result<String>
    fun release()
}

class TextRecognitionServiceImpl : TextRecognitionService {

    private val recognizer = TextRecognition.getClient(
        TextRecognizerOptions.Builder().build()
    )

    override suspend fun recognizeText(image: Any, rotation: Int): Result<String> {
        return try {
            val inputImage = image as InputImage

            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        continuation.resume(Result.success(visionText.text)) {
                            DEFAULT_BUFFER_SIZE
                        }
                    }
                    .addOnFailureListener { e ->
                        continuation.resume(Result.failure(e)) {
                            also { recognizer.close() }
                        }
                    }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun release() {
        recognizer.close()
    }
}