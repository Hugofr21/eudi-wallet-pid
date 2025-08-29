package eu.europa.ec.verifierfeature.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PresentationState(
    @SerialName("vp_token")
    val vpToken: VpTokenSate,
    @SerialName("presentation_submission")
    val presentationSubmission: PresentationSubmission? = null,
    @SerialName("trust_info")
    val trustInfo: List<TrustInfoState>? = null
)

@Serializable
data class VpTokenSate(
    @SerialName("proof_of_age")
    val proofOfAge: String? = null,
    val token: Map<String, String>? = null
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
data class TrustInfoState(
    val issuer: String? = null,
    val status: String? = null,
    val details: String? = null
)