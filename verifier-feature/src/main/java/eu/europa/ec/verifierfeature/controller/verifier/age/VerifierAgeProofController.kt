package eu.europa.ec.verifierfeature.controller.verifier.age

import com.nimbusds.jose.JWSAlgorithm
import eu.europa.ec.verifierfeature.model.ClientMetadata
import eu.europa.ec.verifierfeature.model.FieldLabel
import eu.europa.ec.verifierfeature.model.PresentationRequest
import eu.europa.ec.verifierfeature.model.PresentationResponse
import eu.europa.ec.verifierfeature.model.PresentationState
import eu.europa.ec.verifierfeature.model.TransactionEvents
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import retrofit2.Response
import com.nimbusds.openid.connect.sdk.Nonce
import eu.europa.ec.businesslogic.provider.UuidProvider
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.verifierfeature.model.WalletResponse
import kotlinx.serialization.json.Json

interface VerifierAgeProofController {
    suspend fun metadataVerifier(): Response<ClientMetadata>
    suspend fun createPresentationRequest(fields: List<FieldLabel>): PresentationResponse
    suspend fun getPresentationState(transactionID: String): PresentationState
    suspend fun getTransactionEventsLogs(transactionID: String): TransactionEvents
    suspend fun validateSdJwtVc(sdJwtVc: String, nonce: String, issuerChain: String?): JsonObject
    fun getLastNonce(): String

//    suspend fun asPreregisteredClient(): PreregisteredClient

    suspend fun getWalletResponse(
        presentationId: String,
        responseCode: String
    ): Response<WalletResponse>

    suspend fun directPost(state: String, vpToken: String): Response<JsonObject>
}


class VerifierAgeProofControllerImpl(
    private val api: VerifierAgeProofApiSwaggerController,
    private val uuidProvider: UuidProvider
) : VerifierAgeProofController {

    private var lastNonce: String? = null

    private fun randomNonce(): String = Nonce().value.also {
        lastNonce = it
    }

    override suspend fun metadataVerifier(): Response<ClientMetadata> {
        return api.getClientMetadata()
    }


//    override suspend fun asPreregisteredClient(): PreregisteredClient {
//        val resp = api.getPublicKeysJson()
//        if (!resp.isSuccessful) {
//            throw RuntimeException("Failed to get public JWKs: HTTP ${resp.code()}")
//        }
//        val jwksJson = resp.body()!!
//
//        val jwkSource = JwkSetSource.ByValue(jwksJson)
//
//        val jwsAlg = JWSAlgorithm.ES256
//
//        return PreregisteredClient(
//            clientId = "verifier-backend.eudiw.dev",
//            legalName = "verifier age proof age",
//            jarConfig = jwsAlg to jwkSource
//        )
//
//    }

    override suspend fun getWalletResponse(
        presentationId: String,
        responseCode: String
    ): Response<WalletResponse> {
        return api.getWalletResponse(presentationId, responseCode)
    }

    override suspend fun directPost(
        state: String,
        vpToken: String
    ): Response<JsonObject> {
        return api.directPost(state, state)
    }


    override suspend fun createPresentationRequest(
        fields: List<FieldLabel>,
    ): PresentationResponse {
        val nonce = randomNonce()
        val credentialId = "proof_of_age"
        val pem = R.raw.av_issuer_ca01

        val credentialsArray = buildJsonArray {
            add(buildJsonObject {
                put("id", JsonPrimitive(credentialId))
                put("format", JsonPrimitive("mso_mdoc"))
                putJsonObject("meta") {
                    put("doctype_value", JsonPrimitive("eu.europa.ec.av.1"))
                }
                putJsonArray("claims") {
                    fields.forEach { fld ->
                        add(buildJsonObject {
                            putJsonArray("path") {
                                add(JsonPrimitive("eu.europa.ec.av.1"))
                                add(JsonPrimitive(fld.key))
                            }
                        })
                    }
                }
            })
        }


        val dcqlQuery = buildJsonObject {
            putJsonArray("credentials") {
                credentialsArray.forEach { add(it) }
            }
        }

        val request = PresentationRequest(
            type = "vp_token",
            dcqlQuery = dcqlQuery,
            nonce = nonce,
            requestUriMethod = "get",
        )

        println(
            "[createPresentationRequest] request JSON: ${
                Json.encodeToString(JsonObject.serializer(), buildJsonObject {
                    put("type", JsonPrimitive(request.type))
                    put("dcql_query", request.dcqlQuery)
                    put("nonce", JsonPrimitive(request.nonce))
                    request.jarMode?.let { put("jar_mode", JsonPrimitive(it)) }
                    request.requestUriMethod?.let {
                        put(
                            "request_uri_method",
                            JsonPrimitive(it)
                        )
                    }
                    request.issuerChain?.let { put("issuer_chain", JsonPrimitive(it)) }
                })
            }"
        )

        val response = api.createPresentation(request)

        if (!response.isSuccessful) {
            throw RuntimeException(
                "Error ${response.code()}: ${
                    response.errorBody()?.string()
                }"
            )
        }
        println("Response presentation state: ${response.body()}")
        return response.body()!!
    }


    override suspend fun getPresentationState(transactionID: String): PresentationState {
        val response = api.getPresentation(transactionID)
        println("getPresentationState ${response}")

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            throw RuntimeException("Error getting status: ${response.code()}: $errorBody")
        }

        return response.body()!!
    }

    override suspend fun getTransactionEventsLogs(transactionID: String): TransactionEvents {
        val response = api.getPresentationEvents(transactionID)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            throw RuntimeException("Error getting status: ${response.code()}: $errorBody")
        }

        return response.body()!!
    }

    override suspend fun validateSdJwtVc(
        sdJwtVc: String,
        nonce: String,
        issuerChain: String?
    ): JsonObject {

        val response = api.validateSdJwtVc(sdJwtVc, nonce, issuerChain = issuerChain)

        if (response.isSuccessful) {
            return response.body()!!
        }

        if (response.code() == 400) {
            val errorList = response.errorBody()?.string().orEmpty()
            throw RuntimeException("SD-JWT-VC invalid: $errorList")
        }

        val err = response.errorBody()?.string().orEmpty()
        throw RuntimeException("Err ${response.code()} when validating SD-JWT-VC: $err")
    }


    override fun getLastNonce(): String =
        lastNonce
            ?: throw IllegalStateException("No nonce has been generated yet. Call createPresentationRequest() first.")

}
