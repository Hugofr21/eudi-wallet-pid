package eu.europa.ec.mrzscannerLogic.utils

object TextReconizer {
    /**
     * Limpeza para NOMES (Campos 1, 2, Localidade)
     * Regra: Se aparecer um número, converte para a letra parecida.
     */
    private fun String.sanitizeName(): String {
        return this.uppercase()
            // Problema "2 não reconhece": No nome, um 2 é provavelmente um Z
            .replace("2", "Z")
            .replace("7", "Z") // Às vezes 7 é lido em vez de Z

            // Problema "e por o":
            .replace("0", "O")
            .replace("3", "E") // 3 no nome vira E
            .replace("6", "G")
            .replace("9", "P") // Raro, mas acontece

            // Outros comuns
            .replace("1", "I")
            .replace("4", "A")
            .replace("5", "S")
            .replace("8", "B")
            .replace("|", "I")
            .replace("$", "S")
            .replace("@", "A")

            // Limpa lixo final
            .replace(Regex("[^\\p{L}'\\s-]"), "")
            .trim()
    }

    /**
     * Limpeza para DATAS e NÚMEROS (Campos 3, 4a, 4b, 5)
     * Regra: Se aparecer uma letra, converte para o número parecido.
     */
    private fun String.sanitizeDate(): String {
        // Normaliza separadores primeiro
        var clean = this.replace(" ", "").replace("-", "/").replace(".", "/")

        return clean.uppercase()
            // Problema "2 não reconhece": Na data, um Z é um 2
            .replace("Z", "2")
            .replace("?", "2") // ? às vezes aparece em vez de 2

            // Problema "e por o": Na data, O/D/Q é 0.
            .replace("O", "0")
            .replace("D", "0")
            .replace("Q", "0")
            .replace("U", "0")

            // Outros comuns
            .replace("I", "1")
            .replace("L", "1")
            .replace("|", "1")
            .replace("E", "3") // E na data é 3
            .replace("A", "4")
            .replace("S", "5")
            .replace("G", "6")
            .replace("B", "8")
            .replace("T", "7") // T é 7

            // Remove tudo o que não for número ou barra
            .replace(Regex("[^0-9/]"), "")
    }
}