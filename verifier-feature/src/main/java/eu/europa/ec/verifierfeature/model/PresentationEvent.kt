package eu.europa.ec.verifierfeature.model

data class PresentationEvent(
    val type: String,
    val timestamp: String,
    val detail: String?
)