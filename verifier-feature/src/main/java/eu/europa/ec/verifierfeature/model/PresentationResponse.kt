package eu.europa.ec.verifierfeature.model

data class PresentationResponse(
    val transaction_id: String,
    val client_id: String,
    val request: String,
    val request_uri: String? = null,
    val request_uri_method: String? = null
)