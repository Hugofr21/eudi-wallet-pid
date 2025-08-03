package eu.europa.ec.verifierfeature.interactor


import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.corelogic.controller.PresentationControllerConfig
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.corelogic.di.getOrCreatePresentationScope
import eu.europa.ec.corelogic.model.ClaimDomain
import eu.europa.ec.dashboardfeature.interactor.DocumentDetailsInteractor
import eu.europa.ec.dashboardfeature.interactor.DocumentDetailsInteractorPartialState
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.verifierfeature.controller.VerifierAgeProofController
import eu.europa.ec.verifierfeature.model.FieldLabel
import eu.europa.ec.verifierfeature.model.PresentationResponse
import kotlinx.coroutines.flow.first
import eu.europa.ec.eudi.wallet.transfer.openId4vp.ProcessedGenericOpenId4VpRequest
import eu.europa.ec.verifierfeature.utils.json.DecodeUtils.decodeJwt

interface AgeProofInteractor {
     suspend fun getDocumentDetails(documentId: DocumentId): List<ClaimDomain>
    suspend fun intFlowVerifier(documentId: DocumentId, fields: List<FieldLabel>)
}



class AgeProofInteractorImpl(
    private val verifierAgeProofController: VerifierAgeProofController,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val resourceProvider: ResourceProvider,
    private val documentDetailsInteractor: DocumentDetailsInteractor,

    ): AgeProofInteractor {

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override suspend fun getDocumentDetails(documentId: DocumentId): List<ClaimDomain> {
        val partialState = documentDetailsInteractor
            .getDocumentDetails(documentId)
            .first()
        return when (partialState) {
            is DocumentDetailsInteractorPartialState.Success -> {
                partialState.documentDetailsDomain.documentClaims
            }
            is DocumentDetailsInteractorPartialState.Failure -> {
                throw RuntimeException(
                    partialState.error.takeIf { it.isNotBlank() } ?: genericErrorMsg
                )
            }
        }
    }


    override suspend fun intFlowVerifier(documentId: DocumentId, fields: List<FieldLabel>) {
        val metadata = verifierAgeProofController.metadataVerifier()

        val pres = verifierAgeProofController.createPresentationRequest(fields)
        println("transaction_id = ${pres.transaction_id}")
        println("client_id      = ${pres.client_id}")
        println("request        = ${pres.request}")
        println("request_uri    = ${pres.request_uri}")
        println("method         = ${pres.request_uri_method}")

        decodeJwt(pres.request)

        val claims = verifierAgeProofController.fetchAndVerifyJwt(pres.request)
        println("Claims validated in the presentation request:")
        claims.claims.forEach { (k, v) ->
            println("  $k = $v")
        }

    }


}