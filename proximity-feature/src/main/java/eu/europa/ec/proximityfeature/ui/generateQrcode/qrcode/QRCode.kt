package eu.europa.ec.proximityfeature.ui.generateQrcode.qrcode

import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import eu.europa.ec.uilogic.component.wrap.WrapImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import eu.europa.ec.resourceslogic.R

@Composable
fun QRCode(
    modifier: Modifier = Modifier,
    qrCode: String,
    qrSize: Dp
) {
    if (qrCode.isNotEmpty()) {
        WrapImage(
            modifier = modifier,
            painter = rememberQrBitmapPainter(
                content = qrCode,
                size = qrSize
            ),
            contentDescription = stringResource(id = R.string.content_description_qr_code_icon)
        )
    }
}


@Composable
fun rememberQrBitmapPainter(
    content: String,
    @ColorInt primaryPixelsColor: Int = Color.BLACK,
    @ColorInt secondaryPixelsColor: Int = Color.WHITE,
    size: Dp = 150.dp,
    padding: Dp = 0.dp,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): BitmapPainter {

    val density = LocalDensity.current

    val sizePx = with(density) { size.roundToPx() }
    val paddingPx = with(density) { padding.roundToPx() }

    var bitmap by remember(content) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(bitmap) {
        if (bitmap != null) return@LaunchedEffect

        launch(dispatcher) {
            val qrCodeWriter = QRCodeWriter()

            val encodeHints = mutableMapOf<EncodeHintType, Any?>()
                .apply {
                    this[EncodeHintType.MARGIN] = paddingPx
                }

            val bitmapMatrix = try {
                qrCodeWriter.encode(
                    content, BarcodeFormat.QR_CODE,
                    sizePx, sizePx, encodeHints
                )
            } catch (ex: WriterException) {
                null
            }

            val matrixWidth = bitmapMatrix?.width ?: sizePx
            val matrixHeight = bitmapMatrix?.height ?: sizePx

            val newBitmap = Bitmap.createBitmap(
                bitmapMatrix?.width ?: sizePx,
                bitmapMatrix?.height ?: sizePx,
                Bitmap.Config.ARGB_8888,
            )

            for (x in 0 until matrixWidth) {
                for (y in 0 until matrixHeight) {
                    val shouldColorPixel = bitmapMatrix?.get(x, y) ?: false
                    val pixelColor =
                        if (shouldColorPixel) primaryPixelsColor else secondaryPixelsColor

                    newBitmap.setPixel(x, y, pixelColor)
                }
            }

            bitmap = newBitmap
        }
    }

    return remember(bitmap) {
        val currentBitmap = bitmap ?: Bitmap.createBitmap(
            sizePx, sizePx,
            Bitmap.Config.RGBA_F16,
        ).apply { eraseColor(Color.TRANSPARENT) }

        BitmapPainter(currentBitmap.asImageBitmap())
    }
}
