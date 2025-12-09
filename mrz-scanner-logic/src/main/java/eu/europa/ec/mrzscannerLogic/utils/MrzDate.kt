package eu.europa.ec.mrzscannerLogic.utils

import java.util.Calendar

data class MrzDate(
    val day: Int,
    val month: Int,
    val year: Int,
    val rawMrz: String,
    val isValid: Boolean
) {
    override fun toString(): String = "%02d/%02d/%04d".format(day, month, year)

    companion object {
        private val currentYear by lazy { Calendar.getInstance().get(Calendar.YEAR) }
        private val currentYear2Digits by lazy { currentYear % 100 }

        /**
         * Faz parse de Data de Nascimento.
         * Lógica: Se o ano MRZ for maior que o ano atual + 1, assume-se século passado.
         * Ex (em 2025): '26' -> 1926. '24' -> 2024.
         */
        fun parseBirthDate(mrz: String): MrzDate {
            return parse(mrz, isExpiry = false)
        }

        /**
         * Faz parse de Data de Validade.
         * Lógica: Assume quase sempre 2000+, a menos que seja muito alto (anos 60-90) e estejamos longe.
         * Geralmente validades são no futuro ou passado recente.
         */
        fun parseExpiryDate(mrz: String): MrzDate {
            return parse(mrz, isExpiry = true)
        }

        private fun parse(raw: String, isExpiry: Boolean): MrzDate {
            // 1. Limpeza de OCR (Numérica)
            val clean = raw.replace('O', '0')
                .replace('o', '0')
                .replace('I', '1')
                .replace('l', '1')
                .replace('S', '5')
                .replace('B', '8')
                .replace('Z', '2')
                // Garante apenas dígitos
                .filter { it.isDigit() }

            if (clean.length != 6) {
                return MrzDate(0, 0, 0, raw, false)
            }

            val yy = clean.take(2).toInt()
            val mm = clean.substring(2, 4).toInt()
            val dd = clean.substring(4, 6).toInt()

            // 2. Cálculo do Século
            val fullYear = if (isExpiry) {
                // Validade: Se YY < 60 assumimos 20YY. Se > 60 assumimos 19YY (documento antigo expirado)
                // Ajuste este threshold conforme necessário (atualmente 2060)
                if (yy < 60) 2000 + yy else 1900 + yy
            } else {
                // Nascimento:
                // Se o ano YY é maior que o ano atual (ex: estamos em 25, data é 90), é 1990.
                // Se o ano YY é menor/igual (ex: data é 10), é 2010.
                if (yy > currentYear2Digits) 1900 + yy else 2000 + yy
            }

            // 3. Validação Lógica
            val valid = mm in 1..12 && dd in 1..31

            return MrzDate(dd, mm, fullYear, raw, valid)
        }
    }
}