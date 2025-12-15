package eu.europa.ec.mrzscannerLogic.service

import  com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

interface TextRecognitionService {
    suspend fun recognizeText(image: InputImage, rotation: Int): Result<Text>
    fun release()
}

class TextRecognitionServiceImpl : TextRecognitionService {

    private val recognizer = TextRecognition.getClient(
        TextRecognizerOptions.Builder().build()
    )

    override suspend fun recognizeText(image: InputImage, rotation: Int): Result<Text>{
        return try {
            // O método process() retorna um Task<Text>
            val result: Text = recognizer.process(image).await()

            // CORREÇÃO: Retornar o objeto 'result' (Text), NÃO 'result.text' (String)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun release() {
        recognizer.close()
    }
}

