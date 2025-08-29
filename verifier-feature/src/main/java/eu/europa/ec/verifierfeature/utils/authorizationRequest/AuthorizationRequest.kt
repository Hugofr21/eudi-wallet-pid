package eu.europa.ec.verifierfeature.utils.authorizationRequest

import eu.europa.ec.verifierfeature.model.FieldLabel
import java.net.URI
import java.net.URLEncoder
import android.net.Uri
import java.nio.charset.StandardCharsets


object AuthorizationRequest {
    private fun String.encode(): String =
        URLEncoder.encode(this, "UTF-8")

    fun formatAuthorizationRequestApi(
        clientId: String,
        requestUri: String?,
        responseMode: String?
    ): URI {
        require(clientId.isNotBlank()) { "clientId is required" }
        require(!requestUri.isNullOrBlank()) { "requestUri is required" }
        require(!responseMode.isNullOrBlank()) { "responseMode is required" }

        val encodedClientId = Uri.encode(clientId)
        val encodedRequestUri = Uri.encode(requestUri)
        val encodedResponseMode = Uri.encode(responseMode)

        val uriString = "eudi-openid4vp://verifier-backend.eudiw.dev/?" +
                "client_id=$encodedClientId" +
                "&request_uri=$encodedRequestUri" +
                "&response_mode=$encodedResponseMode"

        return URI(uriString)
    }



    fun formatAuthorizationRequest(
        requestUri: String? = null,
        responseMode: String? = null,
        state: String? = null,
        nonce: String? = null,
        fields: List<FieldLabel>,
        responseType: String?
    ): URI {
        require(requestUri != null) { "You must provide either inlineRequest or requestUri" }

        val query = dcqlQuery(fields)

        fun enc(value: String?): String = if (value == null) "" else URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

        val clientIdValue = "redirect_uri:$requestUri"

        val params = listOfNotNull(
            "response_type=${enc(responseType)}",
            "response_mode=${enc(responseMode)}",
            "client_id=${enc(clientIdValue)}",
            "response_uri=${enc(requestUri)}",
            "dcql_query=${enc(query)}",
            "nonce=${enc(nonce)}",
            "state=${enc(state)}"
        ).joinToString("&")

        val uriString = "av://?$params"

        return URI(uriString)
    }

    private fun dcqlQuery(fieldLabels: List<FieldLabel>): String {
        val claimsArray = fieldLabels.joinToString(",") { field ->
            """{"path":["eu.europa.ec.av.1","${field.key}"]}"""
        }

        return """{"credentials":[{"id":"proof_of_age","format":"mso_mdoc","meta":{"doctype_value":"eu.europa.ec.av.1"},"claims":[${claimsArray}]}]}"""
    }

}
