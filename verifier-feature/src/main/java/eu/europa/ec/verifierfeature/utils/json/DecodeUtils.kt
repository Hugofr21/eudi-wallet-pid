package eu.europa.ec.verifierfeature.utils.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

data class AuthRequestData(
    val nonce: String,
    val state: String,
    val requestUri: String?,
    val dcqlQuery: JsonObject,
    val responseMode: String,
    val responseType: String?,
)

object DecodeUtils {

    fun decodeAndEnrichPayload(jwt: String?): JsonObject {
        val parts = jwt?.split(".")
        require(parts?.size == 3) { "Malformed JWT: Expected 3 parts, got ${parts?.size}" }

        val payload = Base64.getUrlDecoder()
            .decode(parts?.get(1))
            .toString(Charsets.UTF_8)
        val jsonObj = Json.parseToJsonElement(payload).jsonObject
            .toMutableMap()

        jsonObj.putIfAbsent("request_uri", JsonNull)
        jsonObj.putIfAbsent("method",      JsonNull)

        return JsonObject(jsonObj)
    }

    fun decodeAuthRequest(jwt: String?): AuthRequestData {
        val payload = decodeAndEnrichPayload(jwt)

        val nonce = payload["nonce"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'nonce' in JWT")

        val state = payload["state"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'state' in JWT")

        val requestUri = payload["response_uri"]?.takeIf { it !is JsonNull }
            ?.jsonPrimitive?.content

        val requestType = payload["response_type"]?.takeIf { it !is JsonNull }
            ?.jsonPrimitive?.content

        val dcql = payload["dcql_query"]?.jsonObject
            ?: throw IllegalArgumentException("Missing 'dcql_query' in JWT")

        val responseMode = payload["response_mode"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'response_mode' in JWT")

        return AuthRequestData(
            nonce = nonce,
            state = state,
            requestUri = requestUri,
            dcqlQuery = dcql,
            responseMode = responseMode,
            requestType
        )
    }

}