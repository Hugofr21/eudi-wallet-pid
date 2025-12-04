package eu.europa.ec.mrzscannerLogic.service

import eu.europa.ec.mrzscannerLogic.model.LineType
import eu.europa.ec.mrzscannerLogic.model.MrzDocument
import eu.europa.ec.mrzscannerLogic.model.MrzFormat


sealed class MrzParseResult {
    data class Success(val document: MrzDocument) : MrzParseResult()
    data class InvalidChecksum(val field: String, val reason: String) : MrzParseResult()
    data class InvalidFormat(val reason: String) : MrzParseResult()
    data class Error(val throwable: Throwable) : MrzParseResult()
}

interface MrzParserService {
    /**
     * Do you parse of lines MRZ from document struct
     */
    fun parse(lines: List<String>): MrzParseResult

    /**
     * Detect the format MRZ of line
     */
    fun detectFormat(lines: List<String>): MrzFormat?
}

class MrzParserServiceImpl(
    private val checksumService: ChecksumValidationService,
    private val correctionService: OcrCorrectionService
):MrzParserService {
    override fun parse(lines: List<String>): MrzParseResult {
        return when (detectFormat(lines)) {
            MrzFormat.TD3 -> parseTD3(lines)
            MrzFormat.TD1 -> parseTD1(lines)
            MrzFormat.TD2 -> parseTD2(lines)
            null -> MrzParseResult.InvalidFormat("Format not found!")
        }
    }

    override fun detectFormat(lines: List<String>): MrzFormat? {
        return when {
            // TD3: 2 line × 44 chars, start with P
            lines.size >= 2 &&
                    lines[0].length in 42..46 &&
                    lines[1].length in 42..46 &&
                    lines[0].startsWith("P") -> MrzFormat.TD3

            // TD1: 3 line × 30 chars, start with I/C/A
            lines.size >= 3 &&
                    lines.all { it.length in 28..32 } &&
                    (lines[0].startsWith("I") ||
                            lines[0].startsWith("C") ||
                            lines[0].startsWith("A")) -> MrzFormat.TD1

            // TD2: 2 line × 36 chars
            lines.size >= 2 &&
                    lines.all { it.length in 34..38 } -> MrzFormat.TD2

            else -> null
        }
    }

    /**
     * Parse TD3 (Passaporte)
     */
    private fun parseTD3(lines: List<String>): MrzParseResult {
        try {
            val line1 = correctionService.correctLine(
                lines[0].padEnd(44, '<'),
                LineType.TD3_LINE1
            )
            val line2 = correctionService.correctLine(
                lines[1].padEnd(44, '<'),
                LineType.TD3_LINE2
            )

            if (line1.length != 44 || line2.length != 44) {
                return MrzParseResult.InvalidFormat("Comprimento inválido")
            }

            // Linha 1: P<XXXSURNAME<<GIVENNAMES
            val issuingCountry = line1.substring(2, 5).replace("<", "")
            val nameSection = line1.substring(5).replace("<<", "|").split("|")
            val surname = nameSection.getOrNull(0)?.replace("<", " ")?.trim() ?: ""
            val givenNames = nameSection.getOrNull(1)?.replace("<", " ")?.trim() ?: ""

            // Linha 2: DOCNUM<CHECKNAT<DOBC<SEXEXPC<PERSONALNC<COMP
            val documentNumber = line2.substring(0, 9).replace("<", "")
            val checkDoc = line2[9]
            val nationality = line2.substring(10, 13).replace("<", "")
            val dob = line2.substring(13, 19)
            val checkDob = line2[19]
            val sex = line2[20].toString()
            val expiry = line2.substring(21, 27)
            val checkExpiry = line2[27]
            val personalNumber = line2.substring(28, 42).replace("<", "")
            val checkPersonal = line2[42]
            val checkComposite = line2[43]

            // Validações
            if (!checksumService.validate(documentNumber, checkDoc)) {
                return MrzParseResult.InvalidChecksum("documentNumber",
                    "Esperado: ${checksumService.calculate(documentNumber)}, Obtido: $checkDoc")
            }
            if (!checksumService.validate(dob, checkDob)) {
                return MrzParseResult.InvalidChecksum("dateOfBirth",
                    "Checksum inválido")
            }
            if (!checksumService.validate(expiry, checkExpiry)) {
                return MrzParseResult.InvalidChecksum("dateOfExpiry",
                    "Checksum inválido")
            }

            // Validação composta
            val composite = documentNumber + checkDoc + dob + checkDob +
                    expiry + checkExpiry + personalNumber + checkPersonal
            if (!checksumService.validate(composite, checkComposite)) {
                return MrzParseResult.InvalidChecksum("composite",
                    "Checksum composto inválido")
            }

            val passport = MrzDocument.Passport(
                documentNumber = documentNumber,
                dateOfBirth = formatDate(dob),
                dateOfExpiry = formatDate(expiry),
                nationality = nationality,
                sex = sex,
                issuingCountry = issuingCountry,
                surname = surname,
                givenNames = givenNames,
                personalNumber = personalNumber,
                documentNumberCheck = checkDoc,
                dobCheck = checkDob,
                expiryCheck = checkExpiry,
                personalNumberCheck = checkPersonal,
                compositeCheck = checkComposite,
                isValid = true,
                rawLines = listOf(line1, line2)
            )

            return MrzParseResult.Success(passport)

        } catch (e: Exception) {
            return MrzParseResult.Error(e)
        }
    }

    /**
     * Parse TD1 (Cartão Cidadão / Carta Condução)
     */
    private fun parseTD1(lines: List<String>): MrzParseResult {
        try {
            val line1 = correctionService.correctLine(
                lines[0].padEnd(30, '<'),
                LineType.TD1_LINE1
            )
            val line2 = correctionService.correctLine(
                lines[1].padEnd(30, '<'),
                LineType.TD1_LINE2
            )
            val line3 = correctionService.correctLine(
                lines[2].padEnd(30, '<'),
                LineType.TD1_LINE3
            )

            if (line1.length != 30 || line2.length != 30 || line3.length != 30) {
                return MrzParseResult.InvalidFormat("Comprimento inválido")
            }

            // Determinar tipo de documento pela primeira linha
            val docTypeCode = line1.substring(0, 2)
            val issuingCountry = line1.substring(2, 5).replace("<", "")
            val documentNumber = line1.substring(5, 14).replace("<", "")
            val checkDoc = line1[14]
            val optionalData1 = line1.substring(15, 30).replace("<", "")

            // Linha 2
            val dob = line2.substring(0, 6)
            val checkDob = line2[6]
            val sex = line2[7].toString()
            val expiry = line2.substring(8, 14)
            val checkExpiry = line2[14]
            val nationality = line2.substring(15, 18).replace("<", "")
            val optionalData2 = line2.substring(18, 29).replace("<", "")
            val checkComposite = line2[29]

            // Linha 3: Nomes
            val nameSection = line3.replace("<<", "|").split("|")
            val surname = nameSection.getOrNull(0)?.replace("<", " ")?.trim() ?: ""
            val givenNames = nameSection.getOrNull(1)?.replace("<", " ")?.trim() ?: ""

            // Validações
            if (!checksumService.validate(documentNumber, checkDoc)) {
                return MrzParseResult.InvalidChecksum("documentNumber", "Checksum inválido")
            }
            if (!checksumService.validate(dob, checkDob)) {
                return MrzParseResult.InvalidChecksum("dateOfBirth", "Checksum inválido")
            }
            if (!checksumService.validate(expiry, checkExpiry)) {
                return MrzParseResult.InvalidChecksum("dateOfExpiry", "Checksum inválido")
            }

            // Validação composta TD1
            val compositeStr = line1.substring(5, 30) + line2.substring(0, 7) +
                    line2.substring(8, 15) + line2.substring(18, 29)
            if (!checksumService.validate(compositeStr, checkComposite)) {
                return MrzParseResult.InvalidChecksum("composite", "Checksum composto inválido")
            }

            // Criar documento baseado no tipo
            val document = when {
                // Cartão de Cidadão (I< ou ID)
                docTypeCode.startsWith("I") -> MrzDocument.IdCard(
                    documentNumber = documentNumber,
                    dateOfBirth = formatDate(dob),
                    dateOfExpiry = formatDate(expiry),
                    nationality = nationality,
                    sex = sex,
                    issuingCountry = issuingCountry,
                    surname = surname,
                    givenNames = givenNames,
                    documentNumberCheck = checkDoc,
                    dobCheck = checkDob,
                    expiryCheck = checkExpiry,
                    optionalData = optionalData1 + optionalData2,
                    compositeCheck = checkComposite,
                    isValid = true,
                    rawLines = listOf(line1, line2, line3)
                )

                // Carta de Condução (D< ou DL)
                docTypeCode.startsWith("D") || docTypeCode.startsWith("A") ->
                    MrzDocument.DrivingLicense(
                        documentNumber = documentNumber,
                        dateOfBirth = formatDate(dob),
                        dateOfExpiry = formatDate(expiry),
                        nationality = nationality,
                        sex = sex,
                        issuingCountry = issuingCountry,
                        surname = surname,
                        givenNames = givenNames,
                        documentNumberCheck = checkDoc,
                        dobCheck = checkDob,
                        expiryCheck = checkExpiry,
                        licenseCategories = optionalData2,
                        isValid = true,
                        rawLines = listOf(line1, line2, line3)
                    )

                else -> return MrzParseResult.InvalidFormat(
                    "Tipo de documento desconhecido: $docTypeCode"
                )
            }

            return MrzParseResult.Success(document)

        } catch (e: Exception) {
            return MrzParseResult.Error(e)
        }
    }

    /**
     * Parse TD2 (algumas Cartas de Condução)
     */
    private fun parseTD2(lines: List<String>): MrzParseResult {
        // Implementação similar ao TD1 mas com 36 caracteres
        return MrzParseResult.InvalidFormat("TD2 não implementado ainda")
    }

    private fun formatDate(yymmdd: String): String {
        if (yymmdd.length != 6) return yymmdd
        val yy = yymmdd.substring(0, 2).toIntOrNull() ?: return yymmdd
        val century = if (yy > 50) "19" else "20"
        val dd = yymmdd.substring(4, 6)
        val mm = yymmdd.substring(2, 4)
        return "$dd/$mm/$century$yy"
    }
}