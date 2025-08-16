package eu.europa.ec.verifierfeature.controller.verifier

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import eu.europa.ec.verifierfeature.model.ClientMetadata
import eu.europa.ec.verifierfeature.model.MsoDeviceResponseValidation
import eu.europa.ec.verifierfeature.model.PresentationRequest
import eu.europa.ec.verifierfeature.model.PresentationResponse
import eu.europa.ec.verifierfeature.model.PresentationState
import eu.europa.ec.verifierfeature.model.TransactionEvents
import eu.europa.ec.verifierfeature.model.WalletResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query


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
    ): Response<PresentationState>


    @GET("/ui/presentations/{transactionId}/events")
    @Headers("Accept: application/json")
    suspend fun getPresentationEvents(
        @Path("transactionId") transactionId: String
    ): Response<List<TransactionEvents>>


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
    ): Response<JsonObject>

    @GET("/wallet/public-keys.json")
    suspend fun getPublicKeysJson(): Response<JsonObject>

    @GET("/ui/presentations/{presentationId}")
    @Headers("Accept: application/json")
    suspend fun getWalletResponse(
        @Path("presentationId") presentationId: String,
        @Query("response_code") responseCode: String
    ): Response<WalletResponse>

    @GET("/wallet/request.jwt/{requestId}")
    @Headers(
        "Accept: application/jwt"
    )
    suspend fun getAuthorizationRequest(
        @Path("requestId") requestId: String
    ): Response<String>

}

class VerifierApiSwaggerControllerImpl(
    private val okHttpClient: OkHttpClient,

    ):VerifierApiSwaggerController{

    companion object{
        val BASE_URL_AGE  = "https://verifier-backend.ageverification.dev/"
        val BASE_URL  = "https://verifier-backend.eudiw.dev/"
    }

    private val json = Json { ignoreUnknownKeys = true }
    val contentType = "application/json".toMediaType()
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(json.asConverterFactory(contentType))
        .client(okHttpClient)
        .build()

    private val api = retrofit.create(VerifierApiSwaggerController::class.java)

    override suspend fun createPresentation(request: PresentationRequest) =
        api.createPresentation(request)

    override suspend fun getPresentation(transactionId: String) =
        api.getPresentation(transactionId)

    override suspend fun getPresentationEvents(transactionId: String) =
        api.getPresentationEvents(transactionId)

    override suspend fun getClientMetadata() =
        api.getClientMetadata()

    override suspend fun validateMsoDeviceResponse(
        deviceResponse: String,
        issuerChain: String?
    ) = api.validateMsoDeviceResponse(deviceResponse, issuerChain)

    override suspend fun validateSdJwtVc(
        sdJwtVc: String,
        nonce: String,
        issuerChain: String?
    ) = api.validateSdJwtVc(sdJwtVc, nonce, issuerChain)

    override suspend fun getPublicKeysJson(): Response<JsonObject> =
        api.getPublicKeysJson()

    override suspend fun getWalletResponse(
        presentationId: String,
        responseCode: String
    ): Response<WalletResponse> = api.getWalletResponse(presentationId, responseCode)

    override suspend fun getAuthorizationRequest(requestId: String): Response<String> =
        api.getAuthorizationRequest(requestId)


}