package eu.europa.ec.verifierfeature.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PresentationState(
    @SerialName("vp_token")
    val vpToken: VpToken,

    @SerialName("presentation_submission")
    val presentationSubmission: PresentationSubmission,

    @SerialName("trust_info")
    val trustInfo: List<TrustInfo>
)

@Serializable
data class VpToken(
    @SerialName("proof_of_age")
    val proofOfAge: String
)

@Serializable
data class PresentationSubmission(
    val id: String,
    @SerialName("definition_id")
    val definitionId: String,
    @SerialName("descriptor_map")
    val descriptorMap: List<DescriptorMap>
)

@Serializable
data class DescriptorMap(
    val id: String,
    val format: String,
    val path: String
)

@Serializable
data class TrustInfo(
    val issuer: String,
    val status: String,
    val details: String? = null
)

