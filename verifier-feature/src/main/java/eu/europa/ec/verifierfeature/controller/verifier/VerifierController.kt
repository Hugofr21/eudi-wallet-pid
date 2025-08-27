package eu.europa.ec.verifierfeature.controller.verifier

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
import eu.europa.ec.eudi.openid4vp.JwkSetSource
import eu.europa.ec.eudi.openid4vp.PreregisteredClient
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.verifierfeature.model.WalletResponse
import eu.europa.ec.verifierfeature.ui.initVerifierOther.IntFlowVerifierOtherRequest
import kotlinx.serialization.json.Json

interface VerifierController {
    suspend fun metadataVerifier(): Response<ClientMetadata>
    suspend fun createPresentationRequest(fields: List<FieldLabel>): PresentationResponse
    suspend fun getPresentationState(transactionID: String): PresentationState
    suspend fun getTransactionEventsLogs(transactionID: String): List<TransactionEvents>
    suspend fun validateSdJwtVc(sdJwtVc: String, nonce: String, issuerChain: String?): JsonObject
    fun getLastNonce(): String

    suspend fun asPreregisteredClient(): PreregisteredClient

    suspend fun getWalletResponse(
        presentationId: String,
        responseCode: String
    ): Response<WalletResponse>

    suspend fun directPost(state: String, vpToken: String): Response<JsonObject>
    suspend fun createPresentationRequestOther(request: IntFlowVerifierOtherRequest): PresentationResponse
}

class VerifierControllerImpl(
    private val api: VerifierApiSwaggerController,
    private val uuidProvider: UuidProvider
) : VerifierController {

    private var lastNonce: String? = null

    private fun randomNonce(): String = Nonce().value.also {
        lastNonce = it
    }

    override suspend fun metadataVerifier(): Response<ClientMetadata> {
        return api.getClientMetadata()
    }


    override suspend fun asPreregisteredClient(): PreregisteredClient {
        val resp = api.getPublicKeysJson()
        if (!resp.isSuccessful) {
            throw RuntimeException("Failed to get public JWKs: HTTP ${resp.code()}")
        }
        val jwksJson = resp.body()!!

        val jwkSource = JwkSetSource.ByValue(jwksJson)

        val jwsAlg = JWSAlgorithm.ES256

        return PreregisteredClient(
            clientId = "verifier-backend.eudiw.dev",
            legalName = "verifier age proof age",
            jarConfig = jwsAlg to jwkSource
        )

    }

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

    override suspend fun createPresentationRequestOther(request: IntFlowVerifierOtherRequest): PresentationResponse {
        println("------------------------------ createPresentationRequestOther -------------------------------------")
        println("Issuer: ${request.issuerName}")
        println("Logo URI: ${request.issuerLogo}")
        println("Is bookmarked: ${request.isBookmarked}")
        println("Is revoked: ${request.isRevoked}")
        println("Claims: ${request.allClaims}")
        println("Doc Name: ${request.issuerName}")
        println("Document Name: ${request.docName}")
        println("Document Identifier (raw): ${request.docIdentifier}")
        println("-------------------------------------------------------------------------\n")

        val nonce = randomNonce()
        val credentialId = uuidProvider.provideUuid()
        val pem = R.raw.pidissuerca02_eu

        val rawDocIdentifier = request.docIdentifier?.toString() ?: "eu.europa.ec.eudi.pid.1"

        val normalizedDocIdentifier = when {
            rawDocIdentifier.equals("MdocPid", ignoreCase = true) -> {
                "eu.europa.ec.eudi.pid.1"
            }
            rawDocIdentifier.equals("SdJwtPid", ignoreCase = true) -> {
                "urn:eudi:pid:1"
            }
            rawDocIdentifier.startsWith("OTHER(", ignoreCase = true) -> {
                val regex = Regex("formatType=([^)]*)")
                val match = regex.find(rawDocIdentifier)
                match?.groupValues?.get(1) ?: rawDocIdentifier
            }
            else -> {
                rawDocIdentifier
            }
        }
        println("Document Identifier (normalized): $normalizedDocIdentifier")

        val excludedClaims = setOf("un_distinguishing_sign")

        val fields = request.allClaims.keys
            .filterNot { it in excludedClaims }
            .toList()

        val credentialFormat = when {
            normalizedDocIdentifier.contains("SdJwt", ignoreCase = true) -> "vc+sd-jwt"
            else -> "mso_mdoc"
        }
        println("Using credential format: $credentialFormat")

        val credentialsArray = buildJsonArray {
            add(buildJsonObject {
                put("id", JsonPrimitive(credentialId))
                put("format", JsonPrimitive(credentialFormat))
                putJsonObject("meta") {
                    put("doctype_value", JsonPrimitive(normalizedDocIdentifier))
                }
                putJsonArray("claims") {
                    fields.forEach { fld ->
                        add(buildJsonObject {
                            putJsonArray("path") {
                                add(JsonPrimitive(normalizedDocIdentifier))
                                add(JsonPrimitive(fld))
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

        val presentationRequest = PresentationRequest(
            type = "vp_token",
            dcqlQuery = dcqlQuery,
            jarMode = "by_reference",
            nonce = nonce,
            requestUriMethod = "get",
            // issuerChain = pem.toString().trimIndent()
        )

        println(
            "[createPresentationRequest] request JSON: ${
                Json.encodeToString(JsonObject.serializer(), buildJsonObject {
                    put("type", JsonPrimitive(presentationRequest.type))
                    put("dcql_query", presentationRequest.dcqlQuery)
                    put("nonce", JsonPrimitive(presentationRequest.nonce))
                    presentationRequest.jarMode?.let { put("jar_mode", JsonPrimitive(it)) }
                    presentationRequest.requestUriMethod?.let {
                        put("request_uri_method", JsonPrimitive(it))
                    }
                    presentationRequest.issuerChain?.let { put("issuer_chain", JsonPrimitive(it)) }
                })
            }"
        )

        val response = api.createPresentation(presentationRequest)

        if (!response.isSuccessful) {
            throw RuntimeException(
                "Error ${response.code()}: ${response.errorBody()?.string()}"
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

    override suspend fun getTransactionEventsLogs(transactionID: String): List<TransactionEvents> {
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


//     suspend fun createPresentationRequest(nameSpace: NameSpace, format: Format, claims: List<String>): PresentationResponse {
//        val id = uuidProvider.provideUuid()
//        val nonce = randomNonce()
//        val chain = """
//        -----BEGIN CERTIFICATE-----
//        MIIDHTCCAqOgAwIBAgIUVqjgtJqf4hUYJkqdYzi+0xwhwFYwCgYIKoZIzj0EAwMw
//        XDEeMBwGA1UEAwwVUElEIElzc3VlciBDQSAtIFVUIDAxMS0wKwYDVQQKDCRFVURJ
//        IFdhbGxldCBSZWZlcmVuY2UgSW1wbGVtZW50YXRpb24xCzAJBgNVBAYTAlVUMB4X
//        DTIzMDkwMTE4MzQxN1oXDTMyMTEyNzE4MzQxNlowXDEeMBwGA1UEAwwVUElEIElz
//        c3VlciBDQSAtIFVUIDAxMS0wKwYDVQQKDCRFVURJIFdhbGxldCBSZWZlcmVuY2Ug
//        SW1wbGVtZW50YXRpb24xCzAJBgNVBAYTAlVUMHYwEAYHKoZIzj0CAQYFK4EEACID
//        YgAEFg5Shfsxp5R/UFIEKS3L27dwnFhnjSgUh2btKOQEnfb3doyeqMAvBtUMlClh
//        sF3uefKinCw08NB31rwC+dtj6X/LE3n2C9jROIUN8PrnlLS5Qs4Rs4ZU5OIgztoa
//        O8G9o4IBJDCCASAwEgYDVR0TAQH/BAgwBgEB/wIBADAfBgNVHSMEGDAWgBSzbLiR
//        FxzXpBpmMYdC4YvAQMyVGzAWBgNVHSUBAf8EDDAKBggrgQICAAABBzBDBgNVHR8E
//        PDA6MDigNqA0hjJodHRwczovL3ByZXByb2QucGtpLmV1ZGl3LmRldi9jcmwvcGlk
//        X0NBX1VUXzAxLmNybDAdBgNVHQ4EFgQUs2y4kRcc16QaZjGHQuGLwEDMlRswDgYD
//        VR0PAQH/BAQDAgEGMF0GA1UdEgRWMFSGUmh0dHBzOi8vZ2l0aHViLmNvbS9ldS1k
//        aWdpdGFsLWlkZW50aXR5LXdhbGxldC9hcmNoaXRlY3R1cmUtYW5kLXJlZmVyZW5j
//        ZS1mcmFtZXdvcmswCgYIKoZIzj0EAwMDaAAwZQIwaXUA3j++xl/tdD76tXEWCikf
//        M1CaRz4vzBC7NS0wCdItKiz6HZeV8EPtNCnsfKpNAjEAqrdeKDnr5Kwf8BA7tATe
//        hxNlOV4Hnc10XO1XULtigCwb49RpkqlS2Hul+DpqObUs
//        -----END CERTIFICATE-----
//    """.trimIndent()
//
//        val response = api.createPresentation()
//
//        if (!response.isSuccessful) {
//            throw RuntimeException("Error ${response.code()}: ${response.errorBody()?.string()}")
//        }
//        println("Response presentation state: ${response.body()}")
//        return response.body()!!
//
//    }

    override fun getLastNonce(): String =
        lastNonce
            ?: throw IllegalStateException("No nonce has been generated yet. Call createPresentationRequest() first.")

}
