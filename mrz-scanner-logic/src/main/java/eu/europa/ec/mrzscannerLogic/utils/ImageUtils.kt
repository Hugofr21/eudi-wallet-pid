package eu.europa.ec.mrzscannerLogic.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
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


     fun Bitmap.toPixelArray(): IntArray {
        val safe = if (config == Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888, false)
        return IntArray(safe.width * safe.height).also {
            safe.getPixels(it, 0, safe.width, 0, 0, safe.width, safe.height)
        }
    }

     fun bitmapToGray(bitmap: Bitmap): FloatArray {
        val px = bitmap.toPixelArray()
        return FloatArray(px.size) { i ->
            val p = px[i]
            0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
        }
    }

     fun bitmapToGrayResized(bitmap: Bitmap, tw: Int, th: Int): IntArray {
        val sw = bitmap.width; val sh = bitmap.height; val px = bitmap.toPixelArray()
        return IntArray(tw * th) { idx ->
            val r = (idx / tw * sh / th).coerceIn(0, sh - 1)
            val c = (idx % tw * sw / tw).coerceIn(0, sw - 1)
            val p = px[r * sw + c]
            (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)).toInt()
        }
    }

     fun Bitmap.meanRgb(): Triple<Float, Float, Float> {
        val px = toPixelArray(); var sR = 0L; var sG = 0L; var sB = 0L
        for (p in px) { sR += Color.red(p); sG += Color.green(p); sB += Color.blue(p) }
        val n = px.size.toFloat()
        return Triple(sR / n, sG / n, sB / n)
    }

     fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}