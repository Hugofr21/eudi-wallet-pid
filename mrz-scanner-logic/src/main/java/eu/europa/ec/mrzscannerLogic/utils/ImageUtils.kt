package eu.europa.ec.mrzscannerLogic.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

object ImageUtils {
    fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return stream.toByteArray()
    }

    /**
     * FIX: was `fun bytesToBitmap(bytes: ByteArray) { TODO() }` — Unit return, never implemented.
     * Decodes a JPEG/PNG byte array back to a [Bitmap].
     * Returns null if [bytes] is empty or the data is not a valid image.
     */
    fun bytesToBitmap(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun bitmapToJpeg(bitmap: Bitmap, quality: Int = 90): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            .toByteArray()
}