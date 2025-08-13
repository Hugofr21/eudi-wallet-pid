package eu.europa.ec.verifierfeature.interactor



import android.content.Context
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAvailability
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationResult
import eu.europa.ec.authenticationlogic.model.biometric.BiometricCrypto
import eu.europa.ec.commonfeature.interactor.DeviceAuthenticationInteractor
import eu.europa.ec.corelogic.controller.PresentationControllerConfig
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.model.AuthenticationData
import eu.europa.ec.dashboardfeature.interactor.DocumentDetailsInteractor
import eu.europa.ec.dashboardfeature.interactor.DocumentDetailsInteractorPartialState
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.verifierfeature.controller.VerifierAgeProofController
import eu.europa.ec.verifierfeature.model.FieldLabel
import eu.europa.ec.verifierfeature.utils.authorizationRequest.AuthorizationRequest
import eu.europa.ec.verifierfeature.utils.json.DecodeUtils.decodeAuthRequest
import kotlinx.coroutines.flow.first
import androidx.core.net.toUri

import eu.europa.ec.eudi.openid4vp.Format


sealed class VerifierLoadingObserveResponsePartialState {
    data class UserAuthenticationRequired(
        val authenticationData: List<AuthenticationData>,
    ) : VerifierLoadingObserveResponsePartialState()

    data class Failure(val error: String) : VerifierLoadingObserveResponsePartialState()
    data object Success : VerifierLoadingObserveResponsePartialState()
    data object RequestReadyToBeSent : VerifierLoadingObserveResponsePartialState()
}

sealed class VerifyNonceResult {
    data class Success(val nonce: String) : VerifyNonceResult()
    data class Failure(val expected: String, val actual: String) : VerifyNonceResult()
}

interface AgeProofInteractor {
    suspend fun getDocumentDetails(documentId: DocumentId): DocumentDetailsInteractorPartialState.Success
    suspend fun intFlowVerifier(documentId: DocumentId, fields: List<FieldLabel>)

    fun handleUserAuthentication(
        context: Context,
        crypto: BiometricCrypto,
        notifyOnAuthenticationFailure: Boolean,
        resultHandler: DeviceAuthenticationResult,
    )

}



class AgeProofInteractorImpl(
    private val verifierAgeProofController: VerifierAgeProofController,
    private val resourceProvider: ResourceProvider,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val documentDetailsInteractor: DocumentDetailsInteractor,
    private val deviceAuthenticationInteractor: DeviceAuthenticationInteractor,
    private val eudiWallet: EudiWallet,
    ): AgeProofInteractor {

    private lateinit var _config: PresentationControllerConfig

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override fun handleUserAuthentication(
        context: Context,
        crypto: BiometricCrypto,
        notifyOnAuthenticationFailure: Boolean,
        resultHandler: DeviceAuthenticationResult,
    ) {
        deviceAuthenticationInteractor.getBiometricsAvailability {
            when (it) {
                is BiometricsAvailability.CanAuthenticate -> {
                    deviceAuthenticationInteractor.authenticateWithBiometrics(
                        context = context,
                        crypto = crypto,
                        notifyOnAuthenticationFailure = notifyOnAuthenticationFailure,
                        resultHandler = resultHandler
                    )
                }

                is BiometricsAvailability.NonEnrolled -> {
                    deviceAuthenticationInteractor.launchBiometricSystemScreen()
                }

                is BiometricsAvailability.Failure -> {
                    resultHandler.onAuthenticationFailure()
                }
            }
        }
    }



    override suspend fun getDocumentDetails(documentId: DocumentId): DocumentDetailsInteractorPartialState.Success {
        val partialState = documentDetailsInteractor
            .getDocumentDetails(documentId)
            .first()
        return when (partialState) {
            is DocumentDetailsInteractorPartialState.Success -> partialState
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
        val authData = decodeAuthRequest(pres.request)
        println("transaction_id = ${pres.transaction_id}")
        println("client_id      = ${pres.client_id}")
        println("request        = ${pres.request}")
        println("request_uri    = ${authData.requestUri}")
        println("method         = ${authData.responseMode}")
        println("state         = ${authData.state}")
        println("responseType  =  ${authData.responseType}")
        fields.forEach { println("Fields {$it.key}") }


        val originalNonce = verifierAgeProofController.getLastNonce()

        when (val result = verifyNonce(expected = originalNonce, actual = authData.nonce)) {
            is VerifyNonceResult.Success -> {
                val deepLink = AuthorizationRequest.formatAuthorizationRequest(
                    pres.client_id,
                    authData.requestUri,
                    authData.state,
                    authData.state,
                    originalNonce,
                    fields,
                    authData.responseType
                )
                println("Deeplink init floe verifier $deepLink")
//              val presentationState = verifierAgeProofController
//                  .getPresentationState(pres.transaction_id)
//                println("PresentationState ${presentationState.vpToken}")
                val deepLinkUri = deepLink.toString().toUri()
                eudiWallet.startRemotePresentation(deepLinkUri)

            }
            is VerifyNonceResult.Failure -> {
                throw IllegalStateException(
                    "Nonce mismatch: expected='${result.expected}', received='${result.actual}'"
                )
            }
        }
    }

    fun verifyNonce(expected: String, actual: String): VerifyNonceResult =
        if (expected == actual) {
            VerifyNonceResult.Success(actual)
        } else {
            VerifyNonceResult.Failure(expected = expected, actual = actual)
        }

}