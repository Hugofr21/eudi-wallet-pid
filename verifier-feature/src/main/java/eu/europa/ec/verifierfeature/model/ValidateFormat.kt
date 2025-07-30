package eu.europa.ec.verifierfeature.model

import retrofit2.http.Field



data class SdJwtVcRequest(
    @Field("sd_jwt_vc") val sdJwtVc: String,
    @Field("nonce") val nonce: String,
    @Field("issuer_chain") val issuerChain: String? = null
)


data class MsoDeviceResponseValidation(
    val docType: String,
    val attributes: Map<String, Any>
)
