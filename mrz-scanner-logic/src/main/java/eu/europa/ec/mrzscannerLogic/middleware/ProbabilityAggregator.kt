package eu.europa.ec.mrzscannerLogic.middleware

import eu.europa.ec.mrzscannerLogic.model.MrzDocument

class ProbabilityAggregator {

    // Mapas de Frequência: Campo -> (Valor Lido -> Contagem de Votos)
    private val votes = mutableMapOf<String, MutableMap<String, Int>>()

    // Configuração de Confiança
    private val MIN_VOTES_TO_WIN = 4 // Precisa de 4 frames idênticos para aceitar
    private val MAX_FRAMES_LIMIT = 30 // Se ao fim de 30 frames não tiver certeza, aceita o melhor

    private var framesProcessed = 0

    init {
        // Inicializa os mapas para os campos que nos interessam
        listOf("1", "2", "3", "4a", "4b", "4c", "4d", "5", "8", "9").forEach { key ->
            votes[key] = mutableMapOf()
        }
    }

    fun reset() {
        votes.values.forEach { it.clear() }
        framesProcessed = 0
    }

    /**
     * Adiciona um documento parcial à votação.
     * Analisa campo a campo e incrementa os contadores.
     */
    fun addFrame(doc: MrzDocument.DrivingLicense) {
        framesProcessed++

        voteFor("1", doc.surname)
        voteFor("2", doc.givenNames)
        voteFor("3", doc.dateOfBirth + "|" + doc.placeOfBirth) // Vota no par Data+Local
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

        // Normalização simples para evitar duplicados por espaços extra nas pontas
        val normalized = value.trim()

        val count = votes[field]?.getOrDefault(normalized, 0) ?: 0
        votes[field]?.put(normalized, count + 1)
    }

    /**
     * Verifica se já temos confiança suficiente nos campos críticos.
     */
    fun isConfident(): Boolean {
        // Se já lemos demasiados frames, paramos para não prender o utilizador
        if (framesProcessed >= MAX_FRAMES_LIMIT) return true

        // Campos Obrigatórios para Confiança
        val hasName = getTopVoteCount("1") >= MIN_VOTES_TO_WIN
        val hasNumber = getTopVoteCount("5") >= MIN_VOTES_TO_WIN
        val hasDate = getTopVoteCount("4a") >= MIN_VOTES_TO_WIN

        return hasName && hasNumber && hasDate
    }

    /**
     * Retorna a contagem de votos do vencedor atual de um campo
     */
    private fun getTopVoteCount(field: String): Int {
        return votes[field]?.maxOfOrNull { it.value } ?: 0
    }

    /**
     * Constrói o documento final escolhendo os vencedores.
     * Aplica lógica especial para o Campo 5 (Espaço).
     */
    fun getResult(): MrzDocument.DrivingLicense {
        val f3 = getWinner("3").split("|")
        val dob = f3.getOrNull(0) ?: ""
        val place = f3.getOrNull(1)

        return MrzDocument.DrivingLicense(
            surname = getWinner("1"),
            givenNames = getWinner("2"),
            dateOfBirth = dob,
            placeOfBirth = place,

            // Aqui garantimos a estabilidade dos pontos 4a, 4b, etc.
            // O valor mais lido (ex: "25.02.2015") ganha de erros esporádicos
            dateOfIssue = getWinner("4a"),
            dateOfExpiry = getWinner("4b"),
            issuingAuthority = getWinner("4c"),
            auditNumber = getWinner("4d"),

            // Lógica Especial para Campo 5
            documentNumber = getWinnerField5(),

            address = getWinner("8"), // A morada mais comum vence
            licenseCategories = getWinner("9"),

            issuingCountry = "PRT",
            nationality = "PRT",
            sex = "Unspecified",
            isValid = true,
            rawLines = emptyList()
        )
    }

    private fun getWinner(field: String): String {
        val candidates = votes[field] ?: return ""
        if (candidates.isEmpty()) return ""
        // Retorna a chave com o maior valor (votos)
        return candidates.maxByOrNull { it.value }?.key ?: ""
    }

    /**
     * Lógica ESPECIAL para o Campo 5 (O Espaço)
     * Se tivermos empate ou proximidade, preferimos AQUELE QUE TEM O ESPAÇO.
     */
    private fun getWinnerField5(): String {
        val candidates = votes["5"] ?: return ""
        if (candidates.isEmpty()) return ""

        // Exemplo:
        // "L-123456 7" (3 votos)
        // "L-1234567" (4 votos)
        // Normalmente ganharia o sem espaço. Mas nós queremos o com espaço.

        var bestCandidate = ""
        var maxScore = -1.0

        for ((value, count) in candidates) {
            var score = count.toDouble()

            // BÓNUS DE PROBABILIDADE: Se tiver o formato "NNNNNN N", ganha +1.5 votos virtuais
            // Isto resolve o seu problema do espaço
            if (value.matches(Regex(".*\\d{6}\\s\\d$"))) {
                score += 1.5
            }

            if (score > maxScore) {
                maxScore = score
                bestCandidate = value
            }
        }
        return bestCandidate
    }

    // Calcula progresso 0.0 -> 1.0 para a UI
    fun getConfidenceProgress(): Float {
        if (framesProcessed >= MAX_FRAMES_LIMIT) return 1.0f

        val goals = listOf("1", "5", "4a")
        var reached = 0
        goals.forEach {
            if (getTopVoteCount(it) >= MIN_VOTES_TO_WIN) reached++
        }
        return reached / goals.size.toFloat()
    }
}