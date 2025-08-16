package eu.europa.ec.verifierfeature.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject

@Serializable
data class PresentationRequest(
    val type: String,
    @SerialName("dcql_query")
    val dcqlQuery: JsonObject,
    val nonce: String,
    @SerialName("jar_mode")
    val jarMode: String? = null,
    @SerialName("request_uri_method")
    val requestUriMethod: String? = null,
    @SerialName("issuer_chain")
    val issuerChain: String? = null
)