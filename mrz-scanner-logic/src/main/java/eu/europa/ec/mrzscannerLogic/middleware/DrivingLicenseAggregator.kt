package eu.europa.ec.mrzscannerLogic.middleware

import eu.europa.ec.mrzscannerLogic.model.MrzDocument

class DrivingLicenseAggregator {

    private var currentData = MrzDocument.DrivingLicense(
        documentNumber = "",
        surname = "",
        givenNames = "",
        dateOfBirth = "",
        dateOfExpiry = "",
        issuingCountry = "",
        nationality = "",
        sex = "",
        licenseCategories = "",
        placeOfBirth = null,
        dateOfIssue = null,
        issuingAuthority = null,
        auditNumber = null,
        address = null,
        isValid = false,
        rawLines = emptyList()
    )
    private var framesCount = 0

    private fun isComplete(): Boolean {
        return !currentData.documentNumber.isNullOrBlank() &&
                !currentData.surname.isNullOrBlank() && // 1
                !currentData.givenNames.isNullOrBlank() && // 2
//                !currentData.placeOfBirth.isNullOrBlank() && // 3
//                !currentData.dateOfIssue.isNullOrBlank() && // 4a
//                !currentData.issuingCountry.isNullOrBlank() && // 4c
//                !currentData.auditNumber.isNullOrBlank() && // 4d
                !currentData.licenseCategories.isNullOrBlank() &&
                (!currentData.address.isNullOrBlank() || framesCount > 10)
    }

    /**
     * Reseta o agregador (ex: quando muda de documento)
     */
    fun reset() {
        currentData = MrzDocument.DrivingLicense(
            documentNumber = "",
            surname = "",
            givenNames = "",
            dateOfBirth = "",
            dateOfExpiry = "",
            issuingCountry = "",
            nationality = "",
            sex = "",
            licenseCategories = "",
            placeOfBirth = null,
            dateOfIssue = null,
            issuingAuthority = null,
            auditNumber = null,
            address = null,
            isValid = false,
            rawLines = emptyList()
        )
        framesCount = 0
    }

    /**
     * Junta os dados do novo frame (partial) com os dados guardados
     * @return O documento completo até agora
     */
    fun aggregate(partial: MrzDocument.DrivingLicense): MrzDocument.DrivingLicense {
        // Se detetarmos um número de carta DIFERENTE, assumimos que é um novo documento
        if (!currentData.documentNumber.isNullOrBlank() &&
            !partial.documentNumber.isNullOrBlank() &&
            partial.documentNumber != currentData.documentNumber) {
            reset()
        }

        framesCount++

        // Lógica de MERGE: Só substituímos se o campo atual estiver vazio ou o novo for "melhor"
        currentData = currentData.copy(
            documentNumber = pickBest(currentData.documentNumber, partial.documentNumber),
            surname = pickBest(currentData.surname, partial.surname),
            givenNames = pickBest(currentData.givenNames, partial.givenNames),
            dateOfBirth = pickBest(currentData.dateOfBirth, partial.dateOfBirth),
            dateOfExpiry = pickBest(currentData.dateOfExpiry, partial.dateOfExpiry),
            issuingCountry = pickBest(currentData.issuingCountry, partial.issuingCountry),
            licenseCategories = pickLongest(currentData.licenseCategories, partial.licenseCategories),
            address = pickAddress(currentData.address, partial.address),
            rawLines = partial.rawLines // Guardamos sempre as últimas linhas raw para debug
        )

        return currentData
    }

    /**
     * Verifica se já temos dados suficientes para terminar o scan
     */
    fun isReady(): Boolean {
        return isComplete() || framesCount > 15
    }


    private fun pickBest(old: String, new: String): String {
        val newHasFormat = new.contains(" ") && new.contains("-")
        val oldHasFormat = old.contains(" ") && old.contains("-")
        if (newHasFormat && !oldHasFormat) return new
        if (oldHasFormat) return old

        return if (new.length > old.length) new else old
    }

    private fun pickLongest(old: String, new: String): String {
        return if (new.length > old.length) new else old
    }

    private fun pickAddress(old: String?, new: String?): String? {
        val o = old ?: ""
        val n = new ?: ""
        return if (n.length > o.length + 5) n else o.ifEmpty { n }
    }
}