package eu.europa.ec.verifierfeature.model


import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class PresentationResponse(
    @SerialName("transaction_id") val transaction_id: String,
    val client_id: String,
    val request: String,
    val request_uri: String? = null,
    val request_uri_method: String? = null
)