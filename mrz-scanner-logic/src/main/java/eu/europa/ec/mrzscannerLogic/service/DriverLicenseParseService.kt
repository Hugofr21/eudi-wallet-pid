package eu.europa.ec.mrzscannerLogic.service

import com.google.mlkit.vision.text.Text
import eu.europa.ec.mrzscannerLogic.model.MrzDocument

interface DriverLicenseParseService {
    fun parse(textResult: Text): MrzParseResult
}

class DriverLicenseParseServiceImpl : DriverLicenseParseService {

    private val fieldIdentifiers = mapOf(
        "1" to Regex("^(1|I|l|\\|)[.,\\s]+"),
        "2" to Regex("^(2|Z|z)[.,\\s]+"),
        "3" to Regex("^(3|E|e)[.,\\s]+"),
        "4a" to Regex("(?i)^[4A&q]\\s*[aA4@][.,]+"),
        "4b" to Regex("(?i)^[4A&6]\\s*[bB86][.,]+"),
        "4c" to Regex("(?i)^[4A&]\\s*[cC][.,]+"),
        "4d" to Regex("(?i)^[4A&]\\s*[dD0][.,]+"),
        "5" to Regex("^(5|S|s)[.,\\s]+"),
        "8" to Regex("^(8|B)[.,\\s]+"),
        "9" to Regex("^(9|g)[.,\\s]+")
    )

    override fun parse(textResult: Text): MrzParseResult {
        val rawData = mutableMapOf<String, String>()

        for (block in textResult.textBlocks) {
            val lines = block.lines
            var i = 0

            while (i < lines.size) {
                val lineText = lines[i].text.trim()
                val fieldKey = identifyFieldStart(lineText)

                if (fieldKey != null) {
                    if (fieldKey == "8") {

                        val addressBuilder = StringBuilder()
                        val content = removePrefix(lineText, fieldIdentifiers["8"]!!)
                        if (content.isNotEmpty()) addressBuilder.append(content)

                        for (j in (i + 1) until lines.size) {
                            val nextLine = lines[j].text.trim()
                            if (identifyFieldStart(nextLine) != null) break
                            addressBuilder.append(" ").append(nextLine)
                            i++
                        }
                        rawData["8"] = addressBuilder.toString().trim()

                    } else {
                        val content = removePrefix(lineText, fieldIdentifiers[fieldKey]!!)
                        if (content.length > 1 || (content.isNotEmpty() && !content.matches(Regex("^[.\\- ]+$")))) {
                            rawData[fieldKey] = content
                        }
                    }
                }
                i++
            }
        }


        if (rawData["5"] == null) {
            scanFallback(textResult.text, rawData)
        }

        return buildDocument(rawData, textResult.text)
    }

    private fun buildDocument(rawData: Map<String, String>, fullRawText: String): MrzParseResult {

        val surnameRaw = rawData["1"] ?: ""
        val givenNamesRaw = rawData["2"] ?: ""

        val surname = cleanNameField(surnameRaw).sanitizeName()
        val givenNames = cleanNameField(givenNamesRaw).sanitizeName()

        val field3 = rawData["3"] ?: ""
        val (dob, pob) = splitBirthDateAndPlace(field3)

        val dateIssue = (rawData["4a"] ?: "").sanitizeDate()
        val dateExpiry = (rawData["4b"] ?: "").sanitizeDate()
        val authority = sanitizeAuthority(rawData["4c"] ?: "")
        val auditNumber = (rawData["4d"] ?: "").replace(" ", "").uppercase()

        val licenseNumber = fixLicenseNumber(rawData["5"] ?: "")


        val address = rawData["8"]?.replace("\n", " ")

        val explicitCategory = rawData["9"]?.replace(".", ",")?.trim()
        val fallbackCategory = findCategoriesFallback(fullRawText)

        val categories = if (!explicitCategory.isNullOrBlank()) {
            explicitCategory
        } else if (!fallbackCategory.isNullOrBlank()) {
            fallbackCategory
        } else {
            "B1, B"
        }

        val hasIdentity = surname.isNotEmpty() && givenNames.isNotEmpty()
        val hasNumber = licenseNumber.length >= 7

        if (!hasIdentity && !hasNumber) {
            return MrzParseResult.InvalidFormat("Data insufficient for parsing")
        }

        val doc = MrzDocument.DrivingLicense(
            documentNumber = licenseNumber,
            surname = surname,
            givenNames = givenNames,
            dateOfBirth = dob ?: "",
            dateOfExpiry = dateExpiry,
            placeOfBirth = pob ?: "",
            dateOfIssue = dateIssue,
            issuingAuthority = authority,
            auditNumber = auditNumber,
            address = address ?: "",
            licenseCategories = categories,
            issuingCountry = "PRT", // Assumimos PRT devido ao layout parseado
            nationality = "UNKNOWN",
            sex = "UNKNOWN",
            isValid = true,
            rawLines = fullRawText.split("\n")
        )

        return MrzParseResult.Success(doc)
    }


    /**
     * Limpa impurezas que frequentemente ficam coladas ao nome (ex: "1. Silva" vira "Silva")
     */
    private fun cleanNameField(raw: String): String {
        return raw.replace(Regex("^[12Ili][.,\\s]+"), "").trim()
    }

    private fun fixLicenseNumber(raw: String): String {
        if (raw.isBlank()) return ""

        var clean = raw.uppercase()
            .replace("5.", "")
            .replace("-", "")
            .replace(" ", "")
            .replace(".", "")
            .trim()

        if (clean.length < 8) return raw
        if (clean.length > 9) clean = clean.substring(0, 9)

        val prefix = clean.substring(0, 2).map {
            when (it) {
                '1', '|', '!' -> 'I'
                '5' -> 'S'
                '8' -> 'B'
                '0' -> 'O'
                else -> it
            }
        }.joinToString("")

        // Normalização forte para Portugal (L-Lisboa)
        var finalPrefix = prefix
        if (prefix == "BR" || prefix == "IR" || prefix == "1R") finalPrefix = "L"

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

        return if (body.length >= 7) {
            val mainNum = body.substring(0, 6)
            val checkDigit = body.substring(6, 7)
            "$finalPrefix-$mainNum $checkDigit"
        } else {
            "$finalPrefix-$body"
        }
    }

    private fun splitBirthDateAndPlace(text: String): Pair<String?, String?> {
        val dateRegex = Regex("(?i)\\b(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})\\s*[./-]\\s*(\\d{2,4})\\b")
        val match = dateRegex.find(text)

        return if (match != null) {
            var dateStr = match.value
            val groups = match.groupValues

            if (groups.size >= 4) {
                val year = groups[3]
                if (year.length == 3) {
                    dateStr = dateStr.replace(year, "${year}0")
                }
            }

            val cleanDate = dateStr.sanitizeDate()

            val cleanPlace = text.replace(match.value, "")
                .trim { !it.isLetter() }
                .sanitizeName()

            Pair(cleanDate, cleanPlace.ifBlank { null })
        } else {
            Pair(null, null)
        }
    }

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

    /**
     * Attempts to find missing categories within other lines.
     * Updated to support common commas in PT formatting (e.g., "B1, B").
     */
    private fun findCategoriesFallback(fullText: String): String? {
        val regex = Regex("\\b(AM|A1|A2|A|B1|B|C1|C|D1|D|BE|CE|DE)\\b")
        val matches = regex.findAll(fullText).map { it.value }.distinct().toList()
        return if (matches.isNotEmpty()) matches.joinToString(", ") else null
    }

    private fun String.sanitizeName(): String = this.replace(Regex("[^A-Za-zÀ-ÖØ-öø-ÿ\\s-]"), "").trim()
    private fun String.sanitizeDate(): String = this.replace(Regex("[^0-9./-]"), "").trim()
    private fun sanitizeAuthority(auth: String): String {
        return auth.uppercase().replace(Regex("[^A-Z]"), "").let {
            if (it.contains("IMT")) "IMT" else it
        }
    }
}