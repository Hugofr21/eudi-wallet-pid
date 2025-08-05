package eu.europa.ec.issuancefeature.utils.codePlaceholder

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class PinPlaceholderTransformation(
    private val length: Int
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val entered = text.text.length
        val masked = "•".repeat(entered)
        val placeholder = "•".repeat((length - entered).coerceAtLeast(0))
        val outText = masked + placeholder

        val offsetMap = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) =
                (offset).coerceIn(0, outText.length)
            override fun transformedToOriginal(offset: Int) =
                offset.coerceIn(0, entered)
        }

        return TransformedText(AnnotatedString(outText), offsetMap)
    }
}
