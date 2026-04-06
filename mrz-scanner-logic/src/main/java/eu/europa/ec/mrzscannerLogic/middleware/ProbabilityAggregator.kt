package eu.europa.ec.mrzscannerLogic.middleware

import eu.europa.ec.mrzscannerLogic.model.MrzDocument
import kotlin.math.min

class ProbabilityAggregator {

    private val votes = mutableMapOf<String, MutableMap<String, Int>>()
    private var lastDocumentNumber: String = ""
    private val MIN_VOTES_TO_WIN = 2      // ← era 4; com fuzzy matching, 2 leituras similares chegam
    private val MAX_FRAMES_LIMIT = 20     // ← era 30; não prende o utilizador tanto tempo


    /**
     * Distância máxima de Levenshtein para dois valores serem considerados
     * "o mesmo" pelo OCR.
     * - 1 para campos curtos/críticos (número de carta, datas)
     * - 2 para campos longos (nome, morada)
     */
    private val FUZZY_THRESHOLD_SHORT = 1
    private val FUZZY_THRESHOLD_LONG  = 2

    /** Campos onde a tolerância fuzzy é maior (texto livre, mais ruidoso). */
    private val LONG_FIELDS = setOf("1", "2", "8")

    private var framesProcessed = 0

    init {
        listOf("1", "2", "3", "4a", "4b", "4c", "4d", "5", "8", "9").forEach { key ->
            votes[key] = mutableMapOf()
        }
    }

    fun reset() {
        votes.values.forEach { it.clear() }
        framesProcessed = 0
        lastDocumentNumber = ""

    }

    fun addFrame(doc: MrzDocument.DrivingLicense) {
        val incomingNumber = doc.documentNumber.trim()
        if (lastDocumentNumber.isNotBlank() &&
            incomingNumber.isNotBlank() &&
            levenshtein(lastDocumentNumber, incomingNumber) > 2
        ) {
            reset()
        }

        if (incomingNumber.isNotBlank()) {
            lastDocumentNumber = incomingNumber
        }

        framesProcessed++
        voteFor("1", doc.surname)
        voteFor("2", doc.givenNames)
        voteFor("3", doc.dateOfBirth + "|" + doc.placeOfBirth)
        voteFor("4a", doc.dateOfIssue)
        voteFor("4b", doc.dateOfExpiry)
        voteFor("4c", doc.issuingAuthority)
        voteFor("4d", doc.auditNumber)
        voteFor("5", doc.documentNumber)
        voteFor("8", doc.address)
        voteFor("9", doc.licenseCategories)
    }

    private fun voteFor(field: String, value: String?) {
        if (value.isNullOrBlank()) return

        val normalized = value.trim()
        val fieldMap   = votes[field] ?: return
        val threshold  = if (field in LONG_FIELDS) FUZZY_THRESHOLD_LONG else FUZZY_THRESHOLD_SHORT

        val existingKey = fieldMap.keys.firstOrNull { candidate ->
            levenshtein(candidate, normalized) <= threshold
        }

        if (existingKey != null) {

            val merged = if (normalized.length > existingKey.length) normalized else existingKey
            val count  = (fieldMap[existingKey] ?: 0) + 1
            fieldMap.remove(existingKey)
            fieldMap[merged] = count
        } else {

            fieldMap[normalized] = (fieldMap[normalized] ?: 0) + 1
        }
    }

    fun isConfident(): Boolean {
        if (framesProcessed >= MAX_FRAMES_LIMIT) return true

        val hasName   = getTopVoteCount("1") >= MIN_VOTES_TO_WIN
        val hasNumber = getTopVoteCount("5") >= MIN_VOTES_TO_WIN
        val hasExpiry = getTopVoteCount("4b") >= MIN_VOTES_TO_WIN 
        val hasDate   = getTopVoteCount("4a") >= MIN_VOTES_TO_WIN

        return hasName && hasNumber && (hasExpiry || hasDate)
    }

    fun getConfidenceProgress(): Float {
        if (framesProcessed >= MAX_FRAMES_LIMIT) return 1.0f

        // Agora reflete TODOS os campos relevantes, não só 3
        val goals = mapOf(
            "1" to MIN_VOTES_TO_WIN,  
            "5" to MIN_VOTES_TO_WIN,  
            "4a" to MIN_VOTES_TO_WIN,  
            "4b" to MIN_VOTES_TO_WIN, 
            "9" to 1                   
        )
        val reached = goals.entries.count { (field, threshold) ->
            getTopVoteCount(field) >= threshold
        }
        return reached.toFloat() / goals.size.toFloat()
    }
    private fun getTopVoteCount(field: String): Int =
        votes[field]?.maxOfOrNull { it.value } ?: 0

    fun getResult(): MrzDocument.DrivingLicense {
        val f3    = getWinner("3").split("|")
        val dob   = f3.getOrNull(0) ?: ""
        val place = f3.getOrNull(1)

        return MrzDocument.DrivingLicense(
            surname          = getWinner("1"),
            givenNames       = getWinner("2"),
            dateOfBirth      = dob,
            placeOfBirth     = place,
            dateOfIssue      = getWinner("4a"),
            dateOfExpiry     = getWinner("4b"),
            issuingAuthority = getWinner("4c"),
            auditNumber      = getWinner("4d"),
            documentNumber   = getWinnerField5(),
            address          = getWinner("8"),
            licenseCategories = getWinner("9"),
            issuingCountry   = "PRT",
            nationality      = "PRT",
            sex              = "Unspecified",
            isValid          = true,
            rawLines         = emptyList()
        )
    }

    private fun getWinner(field: String): String =
        votes[field]?.maxByOrNull { it.value }?.key ?: ""

    private fun getWinnerField5(): String {
        val candidates = votes["5"] ?: return ""
        if (candidates.isEmpty()) return ""

        var bestCandidate = ""
        var maxScore = -1.0

        for ((value, count) in candidates) {
            var score = count.toDouble()

            // Bónus para formato oficial completo: XX-NNNNNN D ou XX-NNNNN D
            if (value.matches(Regex("^[A-Z]{2}-\\d{5,6}\\s\\d$"))) {
                score += 2.0
            }
            // Bónus menor se tiver pelo menos o hífen e espaço
            else if (value.contains("-") && value.contains(" ")) {
                score += 0.5
            }

            if (score > maxScore) {
                maxScore = score
                bestCandidate = value
            }
        }
        return bestCandidate
    }


    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        if (lhs == rhs)       return 0
        if (lhs.isEmpty())    return rhs.length
        if (rhs.isEmpty())    return lhs.length

        val lhsLength = lhs.length + 1
        val rhsLength = rhs.length + 1

        var cost    = Array(lhsLength) { it }
        var newCost = Array(lhsLength) { 0 }

        for (i in 1 until rhsLength) {
            newCost[0] = i
            for (j in 1 until lhsLength) {
                val match      = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                newCost[j]     = min(min(cost[j] + 1, newCost[j - 1] + 1), cost[j - 1] + match)
            }
            val swap = cost; cost = newCost; newCost = swap
        }
        return cost[lhsLength - 1]
    }
}