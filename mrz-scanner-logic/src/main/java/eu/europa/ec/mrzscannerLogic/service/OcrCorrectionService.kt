package eu.europa.ec.mrzscannerLogic.service

import eu.europa.ec.mrzscannerLogic.model.LineType


interface OcrCorrectionService {
    /**
     * Correct common OCR errors in one line.
     */
    fun correctLine(line: String, lineType: LineType): String
}

class OcrCorrectionServiceImpl : OcrCorrectionService {

    override fun correctLine(line: String, lineType: LineType): String {
        var corrected = line

        when (lineType) {
            LineType.TD3_LINE2 -> {
                corrected = correctNumeric(corrected, 0, 9)   // Doc number
                corrected = correctNumeric(corrected, 13, 19) // DOB
                corrected = correctNumeric(corrected, 21, 27) // Expiry
                corrected = correctAlpha(corrected, 10, 13)   // Nationality
            }
            LineType.TD1_LINE1 -> {
                corrected = correctNumeric(corrected, 5, 14)  // Doc number
                corrected = correctAlpha(corrected, 2, 5)     // Country
            }
            LineType.TD1_LINE2 -> {
                corrected = correctNumeric(corrected, 0, 6)   // DOB
                corrected = correctNumeric(corrected, 8, 14)  // Expiry
                corrected = correctAlpha(corrected, 15, 18)   // Nationality
            }
            else -> {}
        }

        return corrected
    }

    private fun correctNumeric(text: String, start: Int, end: Int): String {
        val chars = text.toCharArray()
        for (i in start until minOf(end, chars.size)) {
            chars[i] = when (chars[i]) {
                'O', 'o', 'Q', 'D', 'U' -> '0'
                'I', 'i', 'l', 'L' -> '1'
                'Z', 'z' -> '2'
                'S', 's' -> '5'
                'B' -> '8'
                else -> chars[i]
            }
        }
        return String(chars)
    }

    private fun correctAlpha(text: String, start: Int, end: Int): String {
        val chars = text.toCharArray()
        for (i in start until minOf(end, chars.size)) {
            if (chars[i].isDigit()) {
                chars[i] = when (chars[i]) {
                    '0' -> 'O'
                    '1' -> 'I'
                    '5' -> 'S'
                    '8' -> 'B'
                    else -> chars[i]
                }
            }
        }
        return String(chars)
    }
}