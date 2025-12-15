package eu.europa.ec.mrzscannerLogic.utils

object TextSanitize {
    fun String.sanitizeName(): String {
        return this.uppercase()
            .replace("0", "O")
            .replace("1", "I")
            .replace("3", "E")
            .replace("4", "A")
            .replace("5", "S")
            .replace("8", "B")
            .replace("|", "I")
            .replace(Regex("[^A-ZÀ-ÿ'\\s-]"), "") // Remove números do nome
            .trim()
    }

    fun String.sanitizeDate(): String {
        return this.replace(" ", "")
            .replace("-", "/")
            .replace(".", "/")
            .uppercase()
            .replace("O", "0")
            .replace("D", "0")
            .replace("I", "1")
            .replace("L", "1")
            .replace("Z", "2")
            .replace("E", "3")
            .replace("A", "4")
            .replace("S", "5")
            .replace("G", "6")
            .replace("T", "7")
            .replace("B", "8")
            .replace("%", "") // Erro visto no log: 199%
            .replace(Regex("[^0-9/]"), "")
    }

    /**
     * Limpa o campo 4c (Entidade Emissora) de forma genérica.
     * Corrige erros de OCR (1->I, |->I) e normaliza o separador.
     * Funciona para: "1MT-LISBOA", "TKSF-OSLO", "DGT - MADRID".
     */
     fun sanitizeAuthority(raw: String): String {
        if (raw.isBlank()) return ""

        var clean = raw.uppercase().trim()

        // 1. Normalização do Separador (Hífen)
        // O OCR muitas vezes lê " - ", " . " ou apenas " " no lugar do hífen
        clean = clean.replace(" -", "-")
            .replace("- ", "-")
            .replace(" . ", "-")
            // Se tiver um ponto no meio de letras (ex: IMT.PORTO), vira hífen
            .replace(".", "-")

        // 2. Correção Genérica de 1º Caractere
        // Em siglas (seja IMT, INCM, ID, etc), é muito raro começar com número '1' ou '0'.
        // É quase sempre 'I' ou 'O'.
        if (clean.isNotEmpty()) {
            val first = clean.first()
            if (first == '1' || first == '|' || first == '!' || first == 'L') {
                // Assume que é um 'I' (erro muito comum: 1MT -> IMT, 1NCM -> INCM)
                clean = "I" + clean.substring(1)
            } else if (first == '0') {
                // Assume que é um 'O' (ex: 0SLO -> OSLO)
                clean = "O" + clean.substring(1)
            }
        }

        // 3. Limpeza Final
        // Mantém Letras, Números, Hífen e Espaços (para cidades compostas ex: CASTELO BRANCO)
        clean = clean.replace(Regex("[^A-Z0-9- ]"), "").trim()

        // Remove hífens duplicados se o OCR leu "--"
        while (clean.contains("--")) {
            clean = clean.replace("--", "-")
        }

        return clean
    }
}