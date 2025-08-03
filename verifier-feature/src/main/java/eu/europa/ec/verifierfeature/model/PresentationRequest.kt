package eu.europa.ec.verifierfeature.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject

@Serializable
data class PresentationRequest(
    val type: String,
    @SerialName("dcql_query")
    val dcqlQuery: JsonObject,
    val nonce: String
)