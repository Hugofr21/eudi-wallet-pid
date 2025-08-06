package eu.europa.ec.verifierfeature.interactor



import android.content.Context
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.util.Base64URL
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAvailability
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationResult
import eu.europa.ec.authenticationlogic.model.biometric.BiometricCrypto
import eu.europa.ec.commonfeature.interactor.DeviceAuthenticationInteractor
import eu.europa.ec.corelogic.controller.PresentationControllerConfig
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.corelogic.model.AuthenticationData
import eu.europa.ec.dashboardfeature.interactor.DocumentDetailsInteractor
import eu.europa.ec.dashboardfeature.interactor.DocumentDetailsInteractorPartialState
import eu.europa.ec.eudi.openid4vp.Consensus
import eu.europa.ec.eudi.openid4vp.Consensus.PositiveConsensus.*
import eu.europa.ec.eudi.openid4vp.DispatchOutcome
import eu.europa.ec.eudi.openid4vp.EncryptionParameters
import eu.europa.ec.eudi.openid4vp.PresentationQuery
import eu.europa.ec.eudi.openid4vp.Resolution
import eu.europa.ec.eudi.openid4vp.ResolvedRequestObject
import eu.europa.ec.eudi.openid4vp.SiopOpenId4Vp
import eu.europa.ec.eudi.openid4vp.VpContent
import eu.europa.ec.eudi.openid4vp.VpContent.*
import eu.europa.ec.eudi.openid4vp.asException
import eu.europa.ec.eudi.prex.JsonPath
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.verifierfeature.config.SiopIdTokenBuilder
import eu.europa.ec.verifierfeature.controller.VerifierAgeProofController
import eu.europa.ec.verifierfeature.model.FieldLabel
import eu.europa.ec.eudi.prex.PresentationSubmission
import eu.europa.ec.eudi.prex.DescriptorMap
import eu.europa.ec.eudi.prex.Id
import eu.europa.ec.verifierfeature.model.WalletResponse
import eu.europa.ec.verifierfeature.utils.authorizationRequest.AuthorizationRequest
import eu.europa.ec.verifierfeature.utils.json.DecodeUtils.decodeAuthRequest
import eu.europa.ec.verifierfeature.utils.prepareVc.prepareSdJwtVcVerifiablePresentation
import io.ktor.client.request.request
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.net.URI
import java.util.UUID


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
        println("method         = ${authData.responseMode.substringAfter("_")}")


        val originalNonce = verifierAgeProofController.getLastNonce()

        when (val result = verifyNonce(expected = originalNonce, actual = authData.nonce)) {
            is VerifyNonceResult.Success -> {
                val deepLink = AuthorizationRequest.formatAuthorizationRequest(
                    pres.client_id,
                    authData.requestUri,
                    authData.responseMode.substringAfter("_")
                )
                println("Deeplink init floe verifier $deepLink")
              val presentationState = verifierAgeProofController
                  .getPresentationState(pres.transaction_id)
                println("PresentationState ${presentationState.vpToken}")


//                when (val dispatchOutcome = handle(deepLink)) {
//                    is DispatchOutcome.RedirectURI -> error("Unexpected")
//                    is DispatchOutcome.VerifierResponse.Accepted -> getWalletResponse(
//                        dispatchOutcome,
//                        pres.transaction_id
//                    )
//                    DispatchOutcome.VerifierResponse.Rejected -> error("Unexpected failure")
//                }
            }
            is VerifyNonceResult.Failure -> {
                throw IllegalStateException(
                    "Nonce mismatch: expected='${result.expected}', received='${result.actual}'"
                )
            }
        }
    }

//    suspend fun handle(uri: URI): DispatchOutcome {
//        println("Handling $uri ...")
//         val siopOpenId4Vp: SiopOpenId4Vp(walletConfig)
//
//        return withContext(Dispatchers.IO) {
//            siopOpenId4Vp.handle(uri.toString()) { holderConsent(it) }.also {
//                println("Response was sent to verifierApi which replied with $it")
//            }
//        }
//    }
//
//    suspend fun holderConsent(request: ResolvedRequestObject): Consensus = withContext(Dispatchers.Default) {
//        when (request) {
//            is ResolvedRequestObject.SiopAuthentication -> handleSiop(request)
//            is ResolvedRequestObject.OpenId4VPAuthorization -> handleOpenId4VP(request)
//            else -> Consensus.NegativeConsensus
//        }
//    }
//
//     fun handleOpenId4VP(request: ResolvedRequestObject.OpenId4VPAuthorization): Consensus {
//         return when (val presentationQuery = request.presentationQuery) {
//             is PresentationQuery.ByPresentationDefinition -> {
//                 val presentationDefinition = presentationQuery.value
//                 check(1 == presentationDefinition.inputDescriptors.size) { "found more than 1 input descriptors" }
//                 val inputDescriptor = presentationDefinition.inputDescriptors.first()
//                 val requestedFormats =
//                     checkNotNull(
//                         inputDescriptor.format?.jsonObject()?.keys
//                             ?: presentationDefinition.format?.jsonObject()?.keys,
//                     ) { "formats not defined" }
//                 check(1 == requestedFormats.size) { "found more than 1 formats" }
//
//                 val format = requestedFormats.first()
//                 val verifiablePresentation = when (format) {
//                     "mso_mdoc" ->
//                         prepareSdJwtVcVerifiablePresentation(
//                             audience = request.client,
//                             nonce = request.nonce,
//                             transactionData = request.transactionData,
//                         )
//
//                     "vc+sd-jwt" ->
//                         prepareSdJwtVcVerifiablePresentation(
//                             audience = request.client,
//                             nonce = request.nonce,
//                             transactionData = request.transactionData,
//                         )
//
//                     else -> error("unsupported format $format")
//                 }
//
//                 VPTokenConsensus(
//                     vpContent = PresentationExchange(
//                         verifiablePresentations = listOf(verifiablePresentation),
//                         presentationSubmission = PresentationSubmission(
//                             id = Id(UUID.randomUUID().toString()),
//                             definitionId = presentationDefinition.id,
//                             listOf(
//                                 DescriptorMap(
//                                     id = inputDescriptor.id,
//                                     format = format,
//                                     path = JsonPath.jsonPath("$")!!,
//                                 ),
//                             ),
//                         ),
//                     ),
//                 )
//             }
//
//             is PresentationQuery.ByDigitalCredentialsQuery -> TODO()
//         }
//     }
//
//
//    @Suppress("KotlinConstantConditions")
//    private fun handleSiop(request: ResolvedRequestObject.SiopAuthentication, walletKeyPair: RSAKey): Consensus {
//        println("Received an SiopAuthentication request")
//        fun showScreen() = true.also {
//            println("User consensus was $it")
//        }
//
//        val userConsent: Boolean = showScreen()
//        return if (userConsent) {
//            val idToken = SiopIdTokenBuilder.build(request, walletKeyPair)
//            Consensus.PositiveConsensus.IdTokenConsensus(idToken)
//        } else {
//            Consensus.NegativeConsensus
//        }
//    }
//
//    suspend fun SiopOpenId4Vp.handle(
//        uri: String,
//        holderConsensus: suspend (ResolvedRequestObject) -> Consensus,
//    ): DispatchOutcome =
//        when (val resolution = resolveRequestUri(uri)) {
//            is Resolution.Invalid -> throw resolution.error.asException() as Throwable
//            is Resolution.Success -> {
//                val requestObject = resolution.requestObject
//                val consensus = holderConsensus(requestObject)
//                dispatch(requestObject, consensus, EncryptionParameters.DiffieHellman(Base64URL.encode("dummy_apu")))
//            }
//        }
//

    suspend fun getWalletResponse(dispatchOutcome: DispatchOutcome.VerifierResponse.Accepted, transactionId:String): Response<WalletResponse> {
        val responseCode = Url(checkNotNull(dispatchOutcome.redirectURI)).parameters["response_code"]
        checkNotNull(responseCode) { "Failed to extract response_code" }
        val walletResponse = verifierAgeProofController.getWalletResponse(transactionId, responseCode)
        return walletResponse
    }

    fun verifyNonce(expected: String, actual: String): VerifyNonceResult =
        if (expected == actual) {
            VerifyNonceResult.Success(actual)
        } else {
            VerifyNonceResult.Failure(expected = expected, actual = actual)
        }


}