package eu.europa.ec.mrzscannerLogic.utils

import java.util.Locale


object MRZCleaner {
    public val WHITESPACE_REGEX = Regex("[ \\t\\r]+")
    public val INVALID_MRZ_CHARS_REGEX = Regex("[^A-Z0-9<]")
    public val COMMON_HEADER_FIX_REGEX = Regex("^[PIACV]1<")

    fun cleanBlockText(text: String): String {
        return text.uppercase(Locale.ROOT)
            .replace(WHITESPACE_REGEX, "")
            .replace("«", "<")
            .replace(Regex("<[ceEKSÇ({[]<"), "<<<")
            .replace(COMMON_HEADER_FIX_REGEX, "P<")
            .replace(INVALID_MRZ_CHARS_REGEX, "")
    }

     fun cleanLine(line: String): String {
        return line.uppercase(Locale.ROOT)
            .replace(" ", "")
            .replace("«", "<")
            .replace("»", "<")
            .replace("°", "0")
            .replace("l", "1")
            .replace("|", "I")
    }

     fun isValidMrzLine(line: String): Boolean {
        if (line.length < 28) return false
        val validChars = line.all { it in 'A'..'Z' || it in '0'..'9' || it == '<' }
        if (!validChars) return false

        val fillerRatio = line.count { it == '<' }.toFloat() / line.length
        return fillerRatio in 0.1f..0.6f
    }
}