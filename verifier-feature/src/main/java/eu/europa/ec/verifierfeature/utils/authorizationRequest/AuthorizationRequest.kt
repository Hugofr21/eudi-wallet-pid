package eu.europa.ec.verifierfeature.utils.authorizationRequest

import eu.europa.ec.verifierfeature.model.FieldLabel
import eu.europa.ec.verifierfeature.utils.authorizationRequest.AuthorizationRequest.encode
import java.net.URI
import java.net.URLEncoder
import android.net.Uri


object AuthorizationRequest {
    private fun String.encode(): String =
        URLEncoder.encode(this, "UTF-8")

    fun formatAuthorizationRequest(
        clientId: String? = null,
        requestUri: String? = null,
        responseMode: String,
        state: String? = null,
        nonce: String? = null,
        fields: List<FieldLabel>,
        responseType: String?
    ): URI {
        require(requestUri != null || clientId != null) {
            "You must provide either inlineRequest or requestUri"
        }

        val query = dcqlQuery(fields)

        val clientIdWithPrefix = "x509_san_dns:${clientId}"
        val encodedClientId = Uri.encode(clientIdWithPrefix)
        val encodedRequestUri = Uri.encode(requestUri)

        val params = buildList<String> {
            add("response_type=${Uri.encode(responseType)}")
            add("response_mode=${Uri.encode(responseMode)}")
            add("client_id=$encodedClientId")
            add("request_uri=$encodedRequestUri")
            add("dcql_query=${Uri.encode(query)}")
            add("nonce=${Uri.encode(nonce)}")
            add("state=${Uri.encode(state)}")
        }.joinToString("&")

        return URI("eudi-openid4vp://verifier-backend.eudiw.dev/?$params")
    }

    private fun dcqlQuery(fieldLabels: List<FieldLabel>): String {
        val claimsArray = fieldLabels.joinToString(",") { field ->
            """{ "path": ["eu.europa.ec.av.1", "${field.key}"] }"""
        }
        return """
        {
          "credentials": [
            {
              "id": "proof_of_age",
              "format": "mso_mdoc",
              "meta": {
                "doctype_value": "eu.europa.ec.av.1"
              },
              "claims": [
                $claimsArray
              ]
            }
          ]
        }
        """.trimIndent()
    }

}
