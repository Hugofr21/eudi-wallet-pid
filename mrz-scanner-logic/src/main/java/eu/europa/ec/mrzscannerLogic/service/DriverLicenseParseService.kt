package eu.europa.ec.mrzscannerLogic.service

import android.util.Log
import com.google.mlkit.vision.text.Text
import eu.europa.ec.mrzscannerLogic.model.MrzDocument
import eu.europa.ec.mrzscannerLogic.model.MrzSex
import eu.europa.ec.mrzscannerLogic.utils.TextSanitize.sanitizeAuthority
import eu.europa.ec.mrzscannerLogic.utils.TextSanitize.sanitizeDate
import eu.europa.ec.mrzscannerLogic.utils.TextSanitize.sanitizeName


interface DriverLicenseParseService {
    /**
     * Do you parse of lines MRZ from document struct
     */
    fun parse(textResult: Text): MrzParseResult

}

class DriverLicenseParseServiceImpl : DriverLicenseParseService {

    /**
     * REGEX "FUZZY": Toleram erros comuns do ML Kit (letras parecidas com números)
     */
    private val fieldIdentifiers = mapOf(
        "1" to Regex("^(1|I|l|\\|)[.,\\s]"),
        "2" to Regex("^(2|Z|z)[.,\\s]"),
        "3" to Regex("^(3|E|e)[.,\\s]"),

        // 4a: Aceita "4a", "Aa", "&a", "qa"
        "4a" to Regex("(?i)^[4A&q]\\s*[aA4@][.,]"),

        // 4b: Aceita "4b", "46", "6b", "Ab" (Erro comum: 6b)
        "4b" to Regex("(?i)^[4A&6]\\s*[bB86][.,]"),

        // 4c: Aceita "4c", "&c"
        "4c" to Regex("(?i)^[4A&]\\s*[cC][.,]"),

        // 4d: Aceita "4d", "&d"
        "4d" to Regex("(?i)^[4A&]\\s*[dD0][.,]"),

        "5" to Regex("^(5|S|s)[.,\\s]"),
        "8" to Regex("^(8|B)[.,\\s]"),
        "9" to Regex("^(9|g)[.,\\s]")
    )

    override fun parse(textResult: Text): MrzParseResult {
        val rawData = mutableMapOf<String, String>()

        // --- ESTRATÉGIA DE BLOCOS (Essencial para Morada) ---
        for (block in textResult.textBlocks) {
            val lines = block.lines
            var i = 0

            // Loop while permite avançar o índice manualmente (para a morada)
            while (i < lines.size) {
                val lineText = lines[i].text.trim()
                val fieldKey = identifyFieldStart(lineText)

                if (fieldKey != null) {
                    if (fieldKey == "8") {
                        // LÓGICA DE MORADA: Captura o bloco restante
                        val addressBuilder = StringBuilder()

                        // 1. Remove o prefixo "8." da linha atual
                        val content = removePrefix(lineText, fieldIdentifiers["8"]!!)
                        if (content.isNotEmpty()) addressBuilder.append(content)

                        // 2. Adiciona as linhas seguintes deste bloco
                        for (j in (i + 1) until lines.size) {
                            val nextLine = lines[j].text.trim()
                            // Se encontrar outro campo (ex: 9), para.
                            if (identifyFieldStart(nextLine) != null) break

                            addressBuilder.append(" ").append(nextLine)
                            i++ // Avança o índice principal
                        }
                        rawData["8"] = addressBuilder.toString().trim()

                    } else {
                        // Lógica Padrão (Uma linha)
                        val content = removePrefix(lineText, fieldIdentifiers[fieldKey]!!)
                        // Filtro de ruído: evita guardar apenas "." ou "-"
                        if (content.length > 1 || (content.isNotEmpty() && !content.matches(Regex("^[.\\- ]+$")))) {
                            rawData[fieldKey] = content
                        }
                    }
                }
                i++
            }
        }

        // --- FALLBACK DE SEGURANÇA ---
        // Se a estratégia de blocos falhou no número da carta, varre o texto bruto
        if (rawData["5"] == null) {
            scanFallback(textResult.text, rawData)
        }

        return buildDocument(rawData, textResult.text)
    }

    private fun buildDocument(rawData: Map<String, String>, fullRawText: String): MrzParseResult {

        // 1. Sanitização de Nomes
        val surname = (rawData["1"] ?: "").sanitizeName()
        val givenNames = (rawData["2"] ?: "").sanitizeName()

        // 2. Campo 3: Separação Inteligente de Data e Local
        val field3 = rawData["3"] ?: ""
        val (dob, pob) = splitBirthDateAndPlace(field3)

        // 3. Datas e Entidades
        val dateIssue = (rawData["4a"] ?: "").sanitizeDate()
        val dateExpiry = (rawData["4b"] ?: "").sanitizeDate()

        // Usa sanitizeAuthority para garantir maiúsculas e corrigir IMT
        val authority = sanitizeAuthority(rawData["4c"] ?: "")
        val auditNumber = (rawData["4d"] ?: "").replace(" ", "").uppercase()

        // 4. Número da Carta (Reconstrução Complexa)
        val licenseNumber = fixLicenseNumber(rawData["5"] ?: "")

        val categories = rawData["9"] ?: findCategoriesFallback(fullRawText)
        val address = rawData["8"]?.replace("\n", " ")

        // 5. Validação Mínima
        // Aceitamos se tiver Nome OU Número válido (o Agregador fará o resto)
        val hasIdentity = surname.isNotEmpty() && givenNames.isNotEmpty()
        val hasNumber = licenseNumber.length >= 7 // Pelo menos L-12345

        if (!hasIdentity && !hasNumber) {
            return MrzParseResult.InvalidFormat("Dados insuficientes")
        }

        val doc = MrzDocument.DrivingLicense(
            documentNumber = licenseNumber,
            surname = surname,
            givenNames = givenNames,
            dateOfBirth = dob ?: "",
            dateOfExpiry = dateExpiry,
            placeOfBirth = pob,
            dateOfIssue = dateIssue,
            issuingAuthority = authority,
            auditNumber = auditNumber,
            address = address,
            licenseCategories = categories ?: "",
            issuingCountry = "PRT",
            nationality = "PRT",
            sex = MrzSex.Unspecified.name,
            isValid = true,
            rawLines = fullRawText.split("\n")
        )

        println("DrivingLicense: $doc")

        return MrzParseResult.Success(doc)
    }

    /**
     * Reconstrói o Número da Carta no formato: XX-NNNNNN N
     * Ex: "BR4830468" -> "L-483046 8"
     */
    private fun fixLicenseNumber(raw: String): String {
        if (raw.isBlank()) return ""

        // 1. Limpeza total (remove espaços, hifens, pontos)
        var clean = raw.uppercase()
            .replace("5.", "")
            .replace("-", "")
            .replace(" ", "")
            .replace(".", "")
            .trim()

        if (clean.length < 8) return raw // Demasiado curto para processar
        if (clean.length > 9) clean = clean.substring(0, 9) // Corta lixo no fim

        // 2. Prefixo (2 Letras): Corrige 1->I, 5->S, 8->B
        val prefix = clean.substring(0, 2).map {
            when (it) {
                '1', '|', '!' -> 'I' // I de IMT/L de Licença
                '5' -> 'S'
                '8' -> 'B'
                '0' -> 'O' // Raro no prefixo PT
                else -> it
            }
        }.joinToString("")

        // Correção específica PT: Se prefixo for BR, IR, PR -> Normalizar para L (Lisboa) ou P (Porto)
        // 90% das cartas modernas são L-
        var finalPrefix = prefix
        if (prefix == "BR" || prefix == "IR" || prefix == "1R") finalPrefix = "L"

        // 3. Corpo (Números): Corrige O->0, I->1, etc
        val body = clean.substring(2).map {
            when (it) {
                'O', 'D', 'Q' -> '0'
                'I', 'L', '|' -> '1'
                'Z' -> '2'
                'E' -> '3'
                'A' -> '4'
                'S' -> '5'
                'G' -> '6'
                'T' -> '7'
                'B' -> '8'
                else -> it
            }
        }.joinToString("")

        // 4. Formatação Final
        return if (body.length >= 7) {
            val mainNum = body.substring(0, 6)
            val checkDigit = body.substring(6, 7)
            "$finalPrefix-$mainNum $checkDigit"
        } else {
            "$finalPrefix-$body"
        }
    }

    /**
     * Separa "Data Local" e corrige anos incompletos (200 -> 2000)
     */
    private fun splitBirthDateAndPlace(text: String): Pair<String?, String?> {
        // Regex captura dia, mes e ano (2 a 4 digitos)
        val dateRegex = Regex("(?i)\\b(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})\\s*[./-]\\s*(\\d{2,4})\\b")
        val match = dateRegex.find(text)

        return if (match != null) {
            var dateStr = match.value

            // Auto-Reparação do Ano (Se tiver 3 digitos, adiciona 0)
            val groups = match.groupValues
            if (groups.size >= 4) {
                val year = groups[3]
                if (year.length == 3) {
                    // Ex: Substitui "200" por "2000" na string da data
                    dateStr = dateStr.replace(year, "${year}0")
                }
            }

            val cleanDate = dateStr.sanitizeDate()

            // Local é o que sobra da string
            val cleanPlace = text.replace(match.value, "")
                .trim { !it.isLetter() } // Remove pontuação
                .sanitizeName()

            Pair(cleanDate, if (cleanPlace.isBlank()) null else cleanPlace)
        } else {
            Pair(null, null)
        }
    }

    // --- Helpers Genéricos ---

    private fun identifyFieldStart(text: String): String? {
        for ((key, regex) in fieldIdentifiers) {
            if (regex.containsMatchIn(text)) return key
        }
        return null
    }

    private fun removePrefix(text: String, regex: Regex): String {
        return text.replace(regex, "").trim()
    }

    private fun scanFallback(fullText: String, rawData: MutableMap<String, String>) {
        val lines = fullText.split("\n")
        for (line in lines) {
            val key = identifyFieldStart(line.trim())
            if (key != null && !rawData.containsKey(key)) {
                rawData[key] = removePrefix(line.trim(), fieldIdentifiers[key]!!)
            }
        }
    }

    private fun findCategoriesFallback(fullText: String): String? {
        val regex = Regex("\\b(AM|A1|A2|A|B1|B|C1|C|D1|D|BE|CE|DE)\\b")
        val matches = regex.findAll(fullText).map { it.value }.distinct().toList()
        return if (matches.isNotEmpty()) matches.joinToString(" ") else null
    }
}