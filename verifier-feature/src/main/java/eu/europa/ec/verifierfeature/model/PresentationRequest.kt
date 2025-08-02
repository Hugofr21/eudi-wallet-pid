package eu.europa.ec.verifierfeature.model

import kotlinx.serialization.json.JsonObject

data class PresentationRequest(
    val type: String,
    val dcqlQuery: JsonObject,
    val nonce: String
)