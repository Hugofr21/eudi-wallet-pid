package eu.europa.ec.verifierfeature.model

import eu.europa.ec.eudi.wallet.transfer.openId4vp.ClientId
import eu.europa.ec.eudi.wallet.transfer.openId4vp.JwsAlgorithm
import eu.europa.ec.eudi.wallet.transfer.openId4vp.LegalName
import eu.europa.ec.eudi.wallet.transfer.openId4vp.VerifierApi
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class PresentationEvent(
    val type: String,
    val timestamp: String,
    val detail: String?
)


data class PreregisteredVerifier(
    var clientId: ClientId,
    var legalName: LegalName,
    var verifierApi: VerifierApi,
    var jwsAlgorithm: JwsAlgorithm = JwsAlgorithm.ES256,
    var jwkSetSource: URI = URI("$verifierApi/wallet/public-keys.json"),
)