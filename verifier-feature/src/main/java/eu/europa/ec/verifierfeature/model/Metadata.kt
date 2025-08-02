package eu.europa.ec.verifierfeature.model


import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class ClientMetadata(
    @SerialName("vp_formats")
    val vpFormats: Map<String, VpFormat>
)

@Serializable
data class VpFormat(
    @SerialName("sd-jwt_alg_values")
    val sdJwtAlgValues: List<String>? = null,

    @SerialName("kb-jwt_alg_values")
    val kbJwtAlgValues: List<String>? = null,

    @SerialName("alg")
    val alg: List<String>? = null
)