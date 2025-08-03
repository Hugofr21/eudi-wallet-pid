package eu.europa.ec.verifierfeature.controller

import eu.europa.ec.verifierfeature.model.ClientMetadata
import eu.europa.ec.verifierfeature.model.FieldLabel
import eu.europa.ec.verifierfeature.model.PresentationResponse
import eu.europa.ec.verifierfeature.model.PresentationState
import eu.europa.ec.verifierfeature.model.TransactionEvents
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import com.nimbusds.openid.connect.sdk.Nonce



interface  VerifierDefaultController{
    suspend fun metadataVerifier(): Response<ClientMetadata>
    suspend fun createPresentationRequest(fields: List<FieldLabel>): PresentationResponse
    suspend fun getPresentationState(transactionID: String): PresentationState
    suspend fun getTransactionEventsLogs(transactionID: String): List<TransactionEvents>
    suspend fun validateSdJwtVc(sdJwtVc: String, nonce: String): JsonObject
}

class VerifierDefaultControllerImpl(

): VerifierDefaultController{

    private fun randomNonce(): String = Nonce().value

    override suspend fun metadataVerifier(): Response<ClientMetadata> {
        TODO("Not yet implemented")
    }

    override suspend fun createPresentationRequest(fields: List<FieldLabel>): PresentationResponse {
        TODO("Not yet implemented")
    }

    override suspend fun getPresentationState(transactionID: String): PresentationState {
        TODO("Not yet implemented")
    }

    override suspend fun getTransactionEventsLogs(transactionID: String): List<TransactionEvents> {
        TODO("Not yet implemented")
    }

    override suspend fun validateSdJwtVc(
        sdJwtVc: String,
        nonce: String
    ): JsonObject {
        TODO("Not yet implemented")
    }

}



