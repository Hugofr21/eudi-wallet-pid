package eu.europa.ec.verifierfeature.model

import retrofit2.http.Field
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


@Serializable
data class SdJwtVcRequest(
    @Field("sd_jwt_vc") val sdJwtVc: String,
    @Field("nonce") val nonce: String,
    @Field("issuer_chain") val issuerChain: String? = null
)

@Serializable
data class MsoDeviceResponseValidation(
    val docType: String,
    val attributes: Map<String, JsonElement>
)
