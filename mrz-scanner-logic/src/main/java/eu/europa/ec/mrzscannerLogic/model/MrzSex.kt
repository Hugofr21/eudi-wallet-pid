package eu.europa.ec.mrzscannerLogic.model

enum class MrzSex(val code: String) {
    Male("M"),
    Female("F"),
    Unspecified("X");

    companion object {
        fun parse(charStr: String): MrzSex {
            // Limpeza agressiva de OCR para o campo Sexo
            val clean = charStr.uppercase()
                .replace("K", "M") // Erro comum
                .replace("H", "M")
                .replace("P", "F") // As vezes F é lido como P
                .firstOrNull() ?: return Unspecified

            return when (clean) {
                'M' -> Male
                'F' -> Female
                else -> Unspecified // '<' ou 'X'
            }
        }
    }
}