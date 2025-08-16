package eu.europa.ec.verifierfeature.model


import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class PresentationResponse(
    @SerialName("transaction_id") val transaction_id: String,
    @SerialName("client_id") val client_id: String,
    @SerialName("request") val request: String? = null,
    @SerialName("request_uri") val request_uri: String? = null,
    @SerialName("request_uri_method")  val request_uri_method: String? = null
)