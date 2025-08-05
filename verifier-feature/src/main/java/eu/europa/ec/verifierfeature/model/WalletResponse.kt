package eu.europa.ec.verifierfeature.model

import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement
import java.security.interfaces.RSAKey

@Serializable
data class WalletResponse(
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("vp_token") val vpToken: JsonElement? = null,
    @SerialName("presentation_submission") val presentationSubmission: PresentationSubmission? = null,
    @SerialName("error") val error: String? = null,
)
