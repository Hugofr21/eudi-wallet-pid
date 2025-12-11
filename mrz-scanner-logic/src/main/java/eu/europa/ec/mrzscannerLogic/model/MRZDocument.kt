package eu.europa.ec.mrzscannerLogic.model

sealed class MrzDocument {
    abstract val documentType: DocumentType
    abstract val mrzFormat: MrzFormat
    abstract val documentNumber: String
    abstract val dateOfBirth: String
    abstract val dateOfExpiry: String
    abstract val nationality: String
    abstract val sex: String
    abstract val isValid: Boolean
    abstract val rawLines: List<String>

    abstract val expiryDate: String?

    /**
     * Passport - TD3 (2 linhas × 44 chars)
     */
    data class Passport(
        override val documentNumber: String,
        override val dateOfBirth: String,
        override val dateOfExpiry: String,
        override val nationality: String,
        override val sex: String,
        override val expiryDate: String? = null,
        val issuingCountry: String,
        public val surname: String,
        public  val givenNames: String,
        val personalNumber: String,
        val documentNumberCheck: Char,
        val dobCheck: Char,
        val expiryCheck: Char,
        val personalNumberCheck: Char,
        val compositeCheck: Char,
        override val isValid: Boolean,
        override val rawLines: List<String>
    ) : MrzDocument() {
        override val documentType = DocumentType.PASSPORT
        override val mrzFormat = MrzFormat.TD3
    }

    /**
     * Citizen Card - TD1 (3 linhas × 30 chars)
     */
    data class IdCard(
        override val documentNumber: String,
        override val dateOfBirth: String,
        override val dateOfExpiry: String,
        override val nationality: String,
        override val sex: String,
        override val expiryDate: String? = null,
        val issuingCountry: String,
        val surname: String,
        val givenNames: String,
        val documentNumberCheck: Char,
        val dobCheck: Char,
        val expiryCheck: Char,
        val optionalData: String,
        val compositeCheck: Char,
        override val isValid: Boolean,
        override val rawLines: List<String>
    ) : MrzDocument() {
        override val documentType = DocumentType.ID_CARD
        override val mrzFormat = MrzFormat.TD1
    }

    /**
     * DRIVER'S LICENSE - TD1 (3 linhas × 30 chars)
     */
    data class DrivingLicense(
        override val documentNumber: String,
        override val dateOfBirth: String,
        override val dateOfExpiry: String,
        override val nationality: String,
        override val sex: String,
        override val expiryDate: String? = null,
        val issuingCountry: String,
        val surname: String,
        val givenNames: String,
        val licenseCategories: String,
        val placeOfBirth: String? = null,   // Parte do Campo 3
        val dateOfIssue: String? = null,     // Campo 4a
        val issuingAuthority: String? = null, // Campo 4c
        val auditNumber: String? = null,      // Campo 4d
        val address: String? = null,          // Campo 8
        override val isValid: Boolean,
        override val rawLines: List<String>
    ) : MrzDocument() {
        override val documentType = DocumentType.DRIVING_LICENSE
        override val mrzFormat = MrzFormat.TD1
    }
}