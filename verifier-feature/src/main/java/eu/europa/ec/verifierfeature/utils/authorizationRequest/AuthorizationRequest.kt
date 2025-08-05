package eu.europa.ec.verifierfeature.utils.authorizationRequest

import kotlinx.serialization.json.JsonObject
import okio.ByteString.Companion.encode
import java.net.URI
import java.net.URLEncoder


object AuthorizationRequest {
    private fun String.encode(): String =
        URLEncoder.encode(this, "UTF-8")

    fun formatAuthorizationRequest(
        clientId: String,
        requestUri: String? = null,
        requestMethod: String? = null,
        inlineRequest: String? = null
    ): URI {
        require(inlineRequest != null || requestUri != null) {
            "You must provide either inlineRequest or requestUri"
        }

        val params = buildList<String> {
            add("client_id=${clientId.encode()}")
            if (!inlineRequest.isNullOrBlank()) {
                add("request=${inlineRequest.encode()}")
            } else {
                add("request_uri=${requestUri!!.encode()}")
                requestMethod
                    ?.takeIf { it.isNotBlank() }
                    ?.also { add("request_uri_method=${it.encode()}") }
            }
        }.joinToString("&")

        return URI("eudi-wallet://authorize?$params")
    }
}
