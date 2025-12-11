package eu.europa.ec.mrzscannerLogic.service

import android.util.Log
import eu.europa.ec.mrzscannerLogic.model.MrzDocument
import eu.europa.ec.mrzscannerLogic.model.MrzSex


interface DriverLicenseParseService {
    /**
     * Do you parse of lines MRZ from document struct
     */
    fun parse(lines: List<String>): MrzParseResult

}


class DriverLicenseParseServiceImpl : DriverLicenseParseService {

    private val datePattern = Regex("\\b(\\d{2})\\s?[./-]\\s?(\\d{2})\\s?[./-]\\s?(\\d{4})\\b")

    private val patterns = mapOf(
        // 1. Apelido: Aceita 1, I, l, |, i
        "1" to Regex("^(1|I|l|\\||i)[.,\\s]\\s*"),

        // 2. Nome: Aceita 2, Z, z, 7 (comum), ?
        "2" to Regex("^(2|Z|z|7|\\?)[.,\\s]\\s*"),

        // 3. Data/Local: Aceita 3, E, e, B
        "3" to Regex("^(3|E|e|B)[.,\\s]\\s*"),

        // 4a, 4b...
        "4a" to Regex("^(4\\s?[aA4][.,]?)\\s*"), // As vezes o 'a' é lido como '4'
        "4b" to Regex("^(4\\s?[bB8][.,]?)\\s*"),
        "4c" to Regex("^(4\\s?[cC][.,]?)\\s*"),
        "4d" to Regex("^(4\\s?[dD0][.,]?)\\s*"),

        // 5. Número: Aceita 5, S, s, $
        "5" to Regex("^(5|S|s|\\$)[.,\\s]\\s*"),

        // 8. Morada: Aceita 8, B, &
        "8" to Regex("^(8|B|\\&)[.,\\s]\\s*"),

        // 9. Categorias: Aceita 9, g, q
        "9" to Regex("^(9|g|q)[.,\\s]\\s*")
    )

    override fun parse(lines: List<String>): MrzParseResult {
        if (lines.isEmpty()) return MrzParseResult.InvalidFormat("Nenhum texto detetado")

        val rawData = mutableMapOf<String, String>()
        var capturingAddress = false
        var addressBuffer = StringBuilder()

        for (line in lines) {
            val trimLine = line.trim()
            var matchedLabel = false

            for ((key, regex) in patterns) {
                if (regex.containsMatchIn(trimLine)) {
                    matchedLabel = true
                    capturingAddress = false // Parar captura de morada se encontrar novo numero
                    val value = trimLine.replace(regex, "").trim()
//                    if (value.isNotEmpty()) rawData[key] = value
                    if (key == "8") {
                        // Começou a morada
                        capturingAddress = true
                        addressBuffer.append(value)
                    } else if (value.isNotEmpty()) {
                        rawData[key] = value
                    }
                    break
                }
            }

            if (!matchedLabel && capturingAddress) {
                // Verifica se parece um código postal ou continuação de texto
                if (line.isNotEmpty() && !line.contains("CARTA DE CONDUÇAO")) {
                    addressBuffer.append(" ").append(line)
                }
            }

        }

        // --- LÓGICA DE LIMPEZA CONTEXTUAL (A MELHORIA ESTÁ AQUI) ---

        if (addressBuffer.isNotEmpty()) {
            rawData["8"] = addressBuffer.toString()
        }

        // 1. Limpeza de Nomes (Campos 1, 2 e Local)
        // Converte números errados de volta para letras (0->O, 1->I, 5->S)
        val surname = (rawData["1"] ?: "").sanitizeName() // Corrige 2->Z, 3->E
        val givenNames = (rawData["2"] ?: "").sanitizeName()

        // 2. Limpeza de Datas (Campos 3-data, 4a, 4b)
        // Converte letras erradas de volta para números (O->0, S->5, Z->2)
        val field3 = rawData["3"] ?: ""
        val (dobRaw, pobRaw) = splitBirthDateAndPlace(field3)
        val dob = dobRaw?.sanitizeDate() ?: ""
        val placeOfBirth = pobRaw?.sanitizeName() // Local é nome, usa sanitizeName

        val dateIssue = (rawData["4a"] ?: "").sanitizeDate()
        val dateExpiry = (rawData["4b"] ?: "").sanitizeDate()

        // 3. Limpeza do Número da Carta (Campo 5)
        // O número da carta pode ter letras e números, mas a estrutura PT é "L-1234567"
        // Removemos espaços e tentamos corrigir caracteres ambíguos se parecerem errados
        var licenseNumber = (rawData["5"] ?: "").replace(" ", "").uppercase()
        // Pequena correção para prefixos comuns em Portugal lidos errados (ex: 1-123 -> L-123)
        if (licenseNumber.startsWith("1-")) licenseNumber = "L-" + licenseNumber.substring(2)
        if (licenseNumber.startsWith("I-")) licenseNumber = "L-" + licenseNumber.substring(2)

        // 4. Categorias e País
        val categories = rawData["9"] ?: findCategoriesFallback(lines.joinToString(" "))
        val detectedCountry = detectIssuingCountry(lines)
        val nationality = if (detectedCountry == "PRT") "PRT" else detectedCountry

        // Validação Mínima
        if (licenseNumber.length < 6 ) {
            return MrzParseResult.InvalidFormat("Dados insuficientes")
        }

        val document = MrzDocument.DrivingLicense(
            documentNumber = licenseNumber,
            surname = surname,
            givenNames = givenNames,
            dateOfBirth = dob,
            dateOfExpiry = dateExpiry,
            issuingCountry = detectedCountry,
            nationality = nationality,
            sex = MrzSex.Unspecified.name,
            licenseCategories = categories ?: "",
            placeOfBirth = placeOfBirth,
            // dateOfIssue foi adicionado se o modelo suportar, senão remove-se daqui
            dateOfIssue = dateIssue,
            issuingAuthority = rawData["4c"],
            auditNumber = rawData["4d"]?.replace(" ", ""),
            address = rawData["8"],
            isValid = true,
            rawLines = lines
        )

        Log.d("DRIVER LICENSE", "Document parsed: $document")

        return MrzParseResult.Success(document)
    }

    // --- FUNÇÕES DE SANITIZAÇÃO ---
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

    private fun splitBirthDateAndPlace(text: String): Pair<String?, String?> {
        val match = datePattern.find(text)
        return if (match != null) {
            val dateStr = match.value
            val place = text.replace(match.value, "").trim { !it.isLetter() }
            Pair(dateStr, if (place.isBlank()) null else place)
        } else {
            Pair(null, null)
        }
    }

    private fun findCategoriesFallback(fullText: String): String? {
        val regex = Regex("\\b(AM|A1|A2|A|B1|B|C1|C|D1|D|BE|CE|DE)\\b")
        val matches = regex.findAll(fullText).map { it.value }.distinct().toList()
        return if (matches.isNotEmpty()) matches.joinToString(" ") else null
    }

    private fun detectIssuingCountry(lines: List<String>): String {
        val header = lines.take(5).joinToString(" ").uppercase()
            .replace("Ç", "C").replace("Ã", "A")

        return when {
            header.contains("PORTUGAL") || header.contains("PORTUGUESA") || header.contains("IMT") -> "PRT"
            header.contains("ESPANA") || header.contains("DGT") -> "ESP"
            header.contains("FRANCE") || header.contains("FRANCAISE") -> "FRA"
            else -> "UNK"
        }
    }
}