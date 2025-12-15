package eu.europa.ec.mrzscannerLogic.utils

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

object ImageUtils {
    fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return stream.toByteArray()
    }
}