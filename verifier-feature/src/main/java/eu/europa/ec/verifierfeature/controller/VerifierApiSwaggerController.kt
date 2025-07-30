package eu.europa.ec.verifierfeature.controller

import eu.europa.ec.verifierfeature.model.ClientMetadata
import eu.europa.ec.verifierfeature.model.MsoDeviceResponseValidation
import eu.europa.ec.verifierfeature.model.PresentationEvent
import eu.europa.ec.verifierfeature.model.PresentationRequest
import eu.europa.ec.verifierfeature.model.PresentationResponse
import eu.europa.ec.verifierfeature.model.SdJwtVcRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path


interface VerifierApiSwaggerController {

    @POST("/ui/presentations")
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json"
    )
    suspend fun createPresentation(
        @Body request: PresentationRequest
    ): Response<PresentationResponse>


    @GET("/ui/presentations/{transactionId}")
    @Headers("Accept: application/json")
    suspend fun getPresentation(
        @Path("transactionId") transactionId: String
    ): Response<PresentationResponse>


    @GET("/ui/presentations/{transactionId}/events")
    @Headers("Accept: application/json")
    suspend fun getPresentationEvents(
        @Path("transactionId") transactionId: String
    ): Response<List<PresentationEvent>>


    @GET("/ui/clientMetadata")
    @Headers("Accept: application/json")
    suspend fun getClientMetadata(): Response<ClientMetadata>


    @FormUrlEncoded
    @POST("/utilities/validations/msoMdoc/deviceResponse")
    @Headers("Accept: application/json")
    suspend fun validateMsoDeviceResponse(
        @Field("device_response") deviceResponse: String,
        @Field("issuer_chain") issuerChain: String? = null
    ): Response<MsoDeviceResponseValidation>



    @FormUrlEncoded
    @POST("/utilities/validations/sdJwtVc")
    @Headers("Accept: application/json")
    suspend fun validateSdJwtVc(
        @Field("sd_jwt_vc") sdJwtVc: String,
        @Field("nonce") nonce: String,
        @Field("issuer_chain") issuerChain: String? = null
    ): Response<SdJwtVcRequest>

}

class VerifierApiSwaggerControllerImpl(

):VerifierApiSwaggerController{

    override suspend fun createPresentation(request: PresentationRequest): Response<PresentationResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun getPresentation(transactionId: String): Response<PresentationResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun getPresentationEvents(transactionId: String): Response<List<PresentationEvent>> {
        TODO("Not yet implemented")
    }

    override suspend fun getClientMetadata(): Response<ClientMetadata> {
        TODO("Not yet implemented")
    }

    override suspend fun validateMsoDeviceResponse(
        deviceResponse: String,
        issuerChain: String?
    ): Response<MsoDeviceResponseValidation> {
        TODO("Not yet implemented")
    }

    override suspend fun validateSdJwtVc(
        sdJwtVc: String,
        nonce: String,
        issuerChain: String?
    ): Response<SdJwtVcRequest> {
        TODO("Not yet implemented")
    }
}