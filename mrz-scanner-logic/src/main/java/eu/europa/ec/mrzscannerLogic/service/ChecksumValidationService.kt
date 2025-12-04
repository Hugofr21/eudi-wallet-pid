package eu.europa.ec.mrzscannerLogic.service

interface ChecksumValidationService {
    fun validate(field: String, checkDigit: Char): Boolean
    fun calculate(field: String): Int
}

class ChecksumValidationServiceImpl : ChecksumValidationService {

    private val weights = intArrayOf(7, 3, 1)

    override fun validate(field: String, checkDigit: Char): Boolean {
        val expected = when {
            checkDigit == '<' -> 0
            checkDigit.isDigit() -> checkDigit.digitToInt()
            else -> return false
        }
        return calculate(field) == expected
    }

    override fun calculate(field: String): Int {
        var sum = 0
        field.forEachIndexed { index, char ->
            val value = charValue(char)
            val weight = weights[index % 3]
            sum += value * weight
        }
        return sum % 10
    }

    private fun charValue(char: Char): Int {
        return when (char) {
            in '0'..'9' -> char - '0'
            in 'A'..'Z' -> char - 'A' + 10
            '<' -> 0
            else -> 0
        }
    }
}