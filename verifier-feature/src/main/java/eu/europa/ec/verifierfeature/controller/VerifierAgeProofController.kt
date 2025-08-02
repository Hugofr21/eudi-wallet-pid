package eu.europa.ec.verifierfeature.controller

import eu.europa.ec.businesslogic.provider.UuidProvider
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

interface  VerifierAgeProofController{
    suspend fun metadataVerifier(): Response<ClientMetadata>
    suspend fun createPresentationRequest(fields: List<FieldLabel>): PresentationResponse
    suspend fun getPresentationState(transactionID: String): PresentationState
    suspend fun getTransactionEventsLogs(transactionID: String): List<TransactionEvents>
    suspend fun validateSdJwtVc(sdJwtVc: String, nonce: String): JsonObject
}


class VerifierAgeProofControllerImpl(
    private val api: VerifierApiSwaggerController,
    private val uuidProvider: UuidProvider
):VerifierAgeProofController {

    override suspend fun metadataVerifier(): Response<ClientMetadata> {
        return api.getClientMetadata()
    }

    override suspend fun createPresentationRequest(
        fields: List<FieldLabel>
    ): PresentationResponse {
        val credentialsArray = buildJsonArray {
            add(buildJsonObject {
                put("id", JsonPrimitive("proof_of_age"))
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
                            put("value", JsonPrimitive(true))
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
            nonce = uuidProvider.provideUuid(),
        )

        val response = api.createPresentation(request)
        if (!response.isSuccessful) {
            throw RuntimeException("Erro ${response.code()}: ${response.errorBody()?.string()}")
        }
        return response.body()!!
    }

    override suspend fun getPresentationState(transactionID: String): PresentationState {
        val response = api.getPresentation(transactionID)

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

    override suspend fun validateSdJwtVc(sdJwtVc: String, nonce: String): JsonObject {

        val response = api.validateSdJwtVc(sdJwtVc, nonce, issuerChain = null)

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
}
