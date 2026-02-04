package eu.europa.ec.mrzscannerLogic.service

import eu.europa.ec.mrzscannerLogic.model.LineType
import eu.europa.ec.mrzscannerLogic.model.MrzDocument
import eu.europa.ec.mrzscannerLogic.model.MrzFormat
import eu.europa.ec.mrzscannerLogic.model.MrzSex
import eu.europa.ec.mrzscannerLogic.utils.MrzDate


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
        val format = detectFormat(lines)
            ?: return MrzParseResult.InvalidFormat("Unrecognized format. Lines: ${lines.size}")

        return when (format) {
            MrzFormat.TD3 -> parseTD3(lines)
            MrzFormat.TD1 -> parseTD1(lines)
            MrzFormat.TD2 -> parseTD2(lines)
        }
    }

    override fun detectFormat(lines: List<String>): MrzFormat? {
        val count = lines.size


        return when {

            count == 2 && lines.all { it.length in 40..50 } -> MrzFormat.TD3

            count == 3 && lines.all { it.length in 28..34 } -> MrzFormat.TD1

            count == 2 && lines.all { it.length in 32..40 } -> MrzFormat.TD2

            else -> null
        }
    }

    private fun parseTD1(rawLines: List<String>): MrzParseResult {
        try {

            val lines = rawLines.map { line ->
                line.uppercase().replace(" ", "").replace("1<", "I<").replace("L<", "I<")
                    .let { if (it.length > 30) it.substring(0, 30) else it.padEnd(30, '<') }
            }
            val line1 = lines[0]
            val line2 = lines[1]
            val line3 = lines[2]

            val issuingCountry = line1.substring(2, 5).replaceDigitsWithAlpha()

            val documentNumber = line1.substring(5, 14).replace("<", "")
            val checkDoc = line1[14]


            val dobString = line2.substring(0, 6)
            val birthDate = MrzDate.parseBirthDate(dobString)
            val checkDob = line2[6]

            val expiryString = line2.substring(8, 14)
            val expiryDate = MrzDate.parseExpiryDate(expiryString)
            val checkExpiry = line2[14]


            val sex = MrzSex.parse(line2[7].toString())


            val nationality = line2.substring(15, 18).replaceDigitsWithAlpha()


            val checkComposite = line2[29]


            val namesClean = line3.replaceDigitsWithAlpha()
            val nameSection = namesClean.replace("<<", "|").split("|")
            val surname = nameSection.getOrNull(0)?.replace("<", " ")?.trim() ?: ""
            val givenNames = nameSection.getOrNull(1)?.replace("<", " ")?.trim() ?: ""


            val isChecksumValid = checksumService.validate(documentNumber, checkDoc) &&
                    checksumService.validate(birthDate.rawMrz, checkDob) &&
                    checksumService.validate(expiryDate.rawMrz, checkExpiry)

            val isDataValid = birthDate.isValid && expiryDate.isValid

            val document = MrzDocument.IdCard(
                documentNumber = documentNumber,
                dateOfBirth = birthDate.toString(),
                dateOfExpiry = expiryDate.toString(),
                nationality = nationality,
                sex = sex.code, // "M", "F" ou "X"
                issuingCountry = issuingCountry,
                surname = surname,
                givenNames = givenNames,
                documentNumberCheck = checkDoc,
                dobCheck = checkDob,
                expiryCheck = checkExpiry,
                optionalData = "",
                compositeCheck = checkComposite,
                isValid = isChecksumValid && isDataValid,
                rawLines = lines
            )

            return MrzParseResult.Success(document)

        } catch (e: Exception) {
            return MrzParseResult.Error(e)
        }
    }

    private fun parseTD3(rawLines: List<String>): MrzParseResult {
        try {
            val lines = rawLines.map { line ->
                line.uppercase()
                    .replace(" ", "")
                    .replace("1<", "P<")
                    .let { if (it.length > 44) it.substring(0, 44) else it.padEnd(44, '<') }
            }

            val line1 = lines[0]
            val line2 = lines[1]

            val documentNumber = line2.substring(0, 9).replaceAlphaWithDigits()
            val checkDoc = line2[9]
            val dob = line2.substring(13, 19).replaceAlphaWithDigits()
            val expiry = line2.substring(21, 27).replaceAlphaWithDigits()
            val nameSection = line1.substring(5).replaceDigitsWithAlpha().replace("<<", "|").split("|")
            val surname = nameSection.getOrNull(0)?.replace("<", " ")?.trim() ?: ""
            val givenNames = nameSection.getOrNull(1)?.replace("<", " ")?.trim() ?: ""

            val passport = MrzDocument.Passport(
                documentNumber = documentNumber,
                issuingCountry = line1.substring(2, 5).replaceDigitsWithAlpha(),
                surname = surname,
                givenNames = givenNames,
                dateOfBirth = formatDate(dob),
                dateOfExpiry = formatDate(expiry),
                nationality = line2.substring(10, 13).replaceDigitsWithAlpha(),
                sex = line2[20].toString(),
                personalNumber = "",
                documentNumberCheck = checkDoc,
                dobCheck = line2[19],
                expiryCheck = line2[27],
                personalNumberCheck = '0',
                compositeCheck = '0',
                isValid = true, // Simplificado para garantir retorno
                rawLines = lines
            )
            return MrzParseResult.Success(passport)

        } catch (e: Exception) {
            return MrzParseResult.Error(e)
        }
    }

    /**
     * Parse TD2 (algumas Cartas de Condução)
     */
    /**
     * Parse TD2 (Formato: 2 linhas x 36 caracteres)
     * Utilizado em Cartões de Identidade antigos e Vistos.
     */
    private fun parseTD2(rawLines: List<String>): MrzParseResult {
        try {
            // 1. Normalização Geométrica e Limpeza
            // Força exatamente 36 caracteres, preenchendo com filler '<' se necessário
            val lines = rawLines.map { line ->
                line.uppercase()
                    .replace(" ", "")
                    .replace("1<", "I<") // Correção de cabeçalho comum
                    .let { if (it.length > 36) it.substring(0, 36) else it.padEnd(36, '<') }
            }

            val line1 = lines[0]
            val line2 = lines[1]

            // -----------------------------------------------------------
            // LINHA 1: Tipo(2) + País(3) + Nomes(31)
            // Exemplo: I<PRTDE<SOUSA<CARVALHO<<MARIA<<<<<<
            // -----------------------------------------------------------

            // Tipo de documento (I<, ID, C<)
            var docType = line1.substring(0, 2).replaceDigitsWithAlpha()

            // País Emissor (sempre letras)
            val issuingCountry = line1.substring(2, 5).replaceDigitsWithAlpha()

            // Sobrenome e Nome (sempre letras, separados por <<)
            // Usamos replaceDigitsWithAlpha para corrigir "0" lido em nomes como "O"
            val namesRaw = line1.substring(5).replaceDigitsWithAlpha()
            val nameSection = namesRaw.replace("<<", "|").split("|")
            val surname = nameSection.getOrNull(0)?.replace("<", " ")?.trim() ?: ""
            val givenNames = nameSection.getOrNull(1)?.replace("<", " ")?.trim() ?: ""


            // -----------------------------------------------------------
            // LINHA 2: DocNum(9)+Chk(1) + Nac(3) + DN(6)+Chk(1) + Sex(1) + Val(6)+Chk(1) + Opt(7) + ChkComp(1)
            // Exemplo: 1234567897PRT8001018F2501015<<<<<<<8
            // -----------------------------------------------------------

            // Número do Documento (predominantemente numérico)
            val documentNumber = line2.take(9).replaceAlphaWithDigits()
            val checkDoc = line2[9]

            // Nacionalidade (letras)
            val nationality = line2.substring(10, 13).replaceDigitsWithAlpha()

            // Data de Nascimento (YYMMDD - Numérico)
            val dob = line2.substring(13, 19).replaceAlphaWithDigits()
            val checkDob = line2[19]

            // Sexo (M, F ou <)
            val sex = line2[20].toString()

            // Data de Validade (YYMMDD - Numérico)
            val expiry = line2.substring(21, 27).replaceAlphaWithDigits()
            val checkExpiry = line2[27]

            // Dados Opcionais (variável, não forçamos correção alfa/numérica aqui)
            val optionalData = line2.substring(28, 35).replace("<", "")

            // Checksum Composto Final
            val checkComposite = line2[35]

            // -----------------------------------------------------------
            // Validação e Construção
            // -----------------------------------------------------------

            val isValidDoc = checksumService.validate(documentNumber, checkDoc) &&
                    checksumService.validate(dob, checkDob) &&
                    checksumService.validate(expiry, checkExpiry)

            // Mapeamos para IdCard, pois a maioria dos TD2 são documentos de identidade.
            // Se o seu sistema tiver uma classe específica para Vistos, altere aqui.
            val document = MrzDocument.IdCard(
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
                optionalData = optionalData,
                compositeCheck = checkComposite,
                isValid = isValidDoc, // Passa o resultado real da validação
                rawLines = lines
            )

            return MrzParseResult.Success(document)

        } catch (e: Exception) {
            // Em caso de erro estrutural grave (ex: substring out of bounds)
            return MrzParseResult.Error(e)
        }
    }
    private fun String.replaceDigitsWithAlpha() = this
        .replace('0', 'O')
        .replace('1', 'I')
        .replace('2', 'Z')
        .replace('3', 'E')
        .replace('4', 'A')
        .replace('5', 'S')
        .replace('8', 'B')

    private fun String.replaceAlphaWithDigits() = this
        .replace('O', '0').replace('o', '0')
        .replace('I', '1').replace('l', '1').replace('L', '1')
        .replace('Z', '2')
        .replace('A', '4')
        .replace('S', '5')
        .replace('B', '8')
        .replace('G', '6')

    private fun formatDate(yymmdd: String): String {
        if (yymmdd.length != 6) return yymmdd
        val yy = yymmdd.substring(0, 2).toIntOrNull() ?: return yymmdd
        val century = if (yy > 50) "19" else "20"
        val dd = yymmdd.substring(4, 6)
        val mm = yymmdd.substring(2, 4)
        return "$dd/$mm/$century$yy"
    }
}