/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.corelogic.controller

import android.net.Uri
import androidx.activity.ComponentActivity
import eu.europa.ec.authenticationlogic.model.biometric.BiometricCrypto
import eu.europa.ec.businesslogic.extension.addOrReplace
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.businesslogic.extension.toUri
import eu.europa.ec.corelogic.di.WalletPresentationScope
import eu.europa.ec.corelogic.model.AuthenticationData
import eu.europa.ec.corelogic.util.EudiWalletListenerWrapper
import eu.europa.ec.eudi.iso18013.transfer.response.DisclosedDocument
import eu.europa.ec.eudi.iso18013.transfer.response.DisclosedDocuments
import eu.europa.ec.eudi.iso18013.transfer.response.RequestProcessor
import eu.europa.ec.eudi.iso18013.transfer.response.RequestedDocument
import eu.europa.ec.eudi.iso18013.transfer.toKotlinResult
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.DocumentExtensions.getDefaultKeyUnlockData
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped
import java.net.URI
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn

sealed class PresentationControllerConfig(val initiatorRoute: String) {
    data class OpenId4VP(val uri: String, val initiator: String) :
        PresentationControllerConfig(initiator)

    data class Ble(val initiator: String) : PresentationControllerConfig(initiator)
}

sealed class TransferEventPartialState {
    data object Connected : TransferEventPartialState()
    data object Connecting : TransferEventPartialState()
    data object Disconnected : TransferEventPartialState()
    data class Error(val error: String) : TransferEventPartialState()
    data class QrEngagementReady(val qrCode: String) : TransferEventPartialState()
    data class RequestReceived(
        val requestData: List<RequestedDocument>,
        val verifierName: String?,
        val verifierIsTrusted: Boolean,
    ) : TransferEventPartialState()

    data object ResponseSent : TransferEventPartialState()
    data class Redirect(val uri: URI) : TransferEventPartialState()
}

sealed class CheckKeyUnlockPartialState {
    data class Failure(val error: String) : CheckKeyUnlockPartialState()
    data class UserAuthenticationRequired(
        val authenticationData: List<AuthenticationData>,
    ) : CheckKeyUnlockPartialState()

    data object RequestIsReadyToBeSent : CheckKeyUnlockPartialState()
}

sealed class SendRequestedDocumentsPartialState {
    data class Failure(val error: String) : SendRequestedDocumentsPartialState()
    data object RequestSent : SendRequestedDocumentsPartialState()
}

sealed class ResponseReceivedPartialState {
    data object Success : ResponseReceivedPartialState()
    data class Redirect(val uri: URI) : ResponseReceivedPartialState()
    data class Failure(val error: String) : ResponseReceivedPartialState()
}

sealed class WalletCorePartialState {
    data class UserAuthenticationRequired(
        val authenticationData: List<AuthenticationData>,
    ) : WalletCorePartialState()

    data class Failure(val error: String) : WalletCorePartialState()
    data object Success : WalletCorePartialState()
    data class Redirect(val uri: URI) : WalletCorePartialState()
    data object RequestIsReadyToBeSent : WalletCorePartialState()
}

/**
 * Common scoped interactor that has all the complexity and required interaction with the EudiWallet Core.
 * */
interface WalletCorePresentationController {
    /**
     * On initialization it adds the core listener and remove it when scope is canceled.
     * When the scope is canceled so does the presentation
     *
     * @return Hot Flow that emits the Core's status callback.
     * */
    val events: SharedFlow<TransferEventPartialState>

    /**
     * User selection data for request step
     * */
    val disclosedDocuments: MutableList<DisclosedDocument>?

    /**
     * Verifier name so it can be retrieve across screens
     * */
    val verifierName: String?

    val verifierIsTrusted: Boolean?

    /**
     * Who started the presentation
     * */
    val initiatorRoute: String

    val redirectUri: URI?

    /**
     * Set [PresentationControllerConfig]
     * */
    fun setConfig(config: PresentationControllerConfig)

    /**
     * Terminates the presentation and kills the coroutine scope that [events] live in
     * */
    fun stopPresentation()

    /**
     * Starts QR engagement. This will trigger [events] emission.
     *
     * [TransferEventPartialState.QrEngagementReady] -> QR String to show QR
     *
     * [TransferEventPartialState.Connecting] -> Connecting
     *
     * [TransferEventPartialState.Connected] -> Connected. We can proceed to the next screen
     * */
    fun startQrEngagement()

    /**
     * Enable/Disable NFC service
     * */
    fun toggleNfcEngagement(componentActivity: ComponentActivity, toggle: Boolean)

    /**
     * Transform UI models to Domain and create -> sent the request.
     *
     * @return Flow that emits the creation state. On Success send the request.
     * The response of that request is emitted through [events]
     *  */
    fun checkForKeyUnlock(): Flow<CheckKeyUnlockPartialState>

    fun sendRequestedDocuments(): SendRequestedDocumentsPartialState

    /**
     * Updates the UI model
     * @param disclosedDocuments User updated data through UI Events
     * */
    fun updateRequestedDocuments(disclosedDocuments: MutableList<DisclosedDocument>?)

    /**
     * @return flow that maps the state from [events] emission to what we consider as success state
     * */
    fun mappedCallbackStateFlow(): Flow<ResponseReceivedPartialState>

    /**
     * The main observation point for collecting state for the Request flow.
     * Exposes a single flow for two operations([checkForKeyUnlock] - [mappedCallbackStateFlow])
     * and a single state
     * @return flow that emits the create, sent, receive states
     * */
    fun observeSentDocumentsRequest(): Flow<WalletCorePartialState>
}

@Scope(WalletPresentationScope::class)
@Scoped
class WalletCorePresentationControllerImpl(
    private val eudiWallet: EudiWallet,
    private val resourceProvider: ResourceProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val documentsController: WalletCoreDocumentsController
) : WalletCorePresentationController {

    private val genericErrorMessage = resourceProvider.genericErrorMessage()

    private val coroutineScope = CoroutineScope(dispatcher + SupervisorJob())

    private lateinit var _config: PresentationControllerConfig

    override var disclosedDocuments: MutableList<DisclosedDocument>? = null

    private var processedRequest: RequestProcessor.ProcessedRequest.Success? = null

    override var verifierName: String? = null

    override var verifierIsTrusted: Boolean? = null

    override val initiatorRoute: String
        get() {
            val config = requireInit { _config }
            return config.initiatorRoute
        }

    override var redirectUri: URI? = null

    override fun setConfig(config: PresentationControllerConfig) {
        println("WalletcorePresentationController config: $config")
        _config = config
    }

    override val events: SharedFlow<TransferEventPartialState> =
        callbackFlow<TransferEventPartialState> {
            // 1) Cria o listener DENTRO do callbackFlow, assim trySendBlocking() funciona
            val eventListenerWrapper = EudiWalletListenerWrapper(
                onQrEngagementReady = { qrCode ->
                    println("[WalletCorePresentationControllerImpl events] onQrEngagementReady: $qrCode")
                    trySendBlocking(TransferEventPartialState.QrEngagementReady(qrCode))
                },
                onConnected = {
                    println("[WalletCorePresentationControllerImpl events] onConnected")
                    trySendBlocking(TransferEventPartialState.Connected)
                },
                onConnecting = {
                    println("[WalletCorePresentationControllerImpl events] onConnecting")
                    trySendBlocking(TransferEventPartialState.Connecting)
                },
                onDisconnected = {
                    println("[WalletCorePresentationControllerImpl events] onDisconnected")
                    trySendBlocking(TransferEventPartialState.Disconnected)
                },
                onError = { errorMessage ->
                    println("[WalletCorePresentationControllerImpl events] onError: $errorMessage")
                    trySendBlocking(
                        TransferEventPartialState.Error(error = errorMessage.ifEmpty { genericErrorMessage })
                    )
                },
                onRequestReceived = { requestedDocumentData ->
                    println("[WalletCorePresentationControllerImpl events] onRequestReceived: $requestedDocumentData")
                    trySendBlocking(
                        requestedDocumentData.getOrNull()?.let { requestedDocuments ->
                            // … seu processamento …
                            println("[WalletCorePresentationControllerImpl events] verifierName: $verifierName, isTrusted: $verifierIsTrusted")
                            TransferEventPartialState.RequestReceived(
                                requestData = requestedDocuments.requestedDocuments,
                                verifierName = verifierName,
                                verifierIsTrusted = true
                            )
                        } ?: TransferEventPartialState.Error(error = genericErrorMessage)
                    )
                },
                onResponseSent = {
                    println("[WalletCorePresentationControllerImpl events] onResponseSent")
                    trySendBlocking(TransferEventPartialState.ResponseSent)
                },
                onRedirect = { uri ->
                    println("[WalletCorePresentationControllerImpl events] onRedirect full URI: $uri")
                    println("[WalletCorePresentationControllerImpl events] onRedirect scheme: ${uri.scheme}")
                    redirectUri = uri
                    trySendBlocking(TransferEventPartialState.Redirect(uri = uri))
                }
            )


            addListener(eventListenerWrapper)
            println("[WalletCorePresentationControllerImpl events] Listener added")

            awaitClose {
                println("[WalletCorePresentationControllerImpl events] Listener removed, stopping presentation")
                removeListener(eventListenerWrapper)
                eudiWallet.stopProximityPresentation()
            }
        }

            .safeAsync<TransferEventPartialState> { throwable ->
                println("[WalletCorePresentationControllerImpl events] Exception in flow: ${throwable.localizedMessage}")
                throwable.printStackTrace()
                TransferEventPartialState.Error(
                    error = throwable.localizedMessage
                        ?: resourceProvider.genericErrorMessage()
                )
            }
            .shareIn(coroutineScope, SharingStarted.Lazily, replay = 2)



    override fun startQrEngagement() {
        eudiWallet.startProximityPresentation()
        println("[WalletCorePresentationControllerImpl] startQrEngagement!")
    }

    override fun toggleNfcEngagement(componentActivity: ComponentActivity, toggle: Boolean) {
        try {
            if (toggle) {
                eudiWallet.enableNFCEngagement(componentActivity)
            } else {
                eudiWallet.disableNFCEngagement(componentActivity)
            }
        } catch (_: Exception) {
        }
    }

    override fun checkForKeyUnlock() = flow {
        disclosedDocuments?.let { documents ->

            val authenticationData = mutableListOf<AuthenticationData>()

            if (eudiWallet.config.userAuthenticationRequired) {

                val keyUnlockDataMap = documents.associateWith { disclosedDocument ->
                    eudiWallet.getDefaultKeyUnlockData(documentId = disclosedDocument.documentId)
                }

                for ((doc, kud) in keyUnlockDataMap) {

                    val cryptoObject = kud?.getCryptoObjectForSigning()

                    authenticationData.add(
                        AuthenticationData(
                            crypto = BiometricCrypto(cryptoObject),
                            onAuthenticationSuccess = {
                                disclosedDocuments?.addOrReplace(
                                    value = doc.copy(keyUnlockData = kud),
                                    replaceCondition = { disclosedDocument ->
                                        disclosedDocument.documentId == doc.documentId
                                    }
                                )
                            }
                        )
                    )
                }

                emit(
                    CheckKeyUnlockPartialState.UserAuthenticationRequired(
                        authenticationData
                    )
                )

            } else {
                emit(
                    CheckKeyUnlockPartialState.RequestIsReadyToBeSent
                )
            }
        }
    }.safeAsync {
        CheckKeyUnlockPartialState.Failure(
            error = it.localizedMessage ?: genericErrorMessage
        )
    }

    override fun sendRequestedDocuments(): SendRequestedDocumentsPartialState {
        return disclosedDocuments?.let { safeDisclosedDocuments ->

            var result: SendRequestedDocumentsPartialState =
                SendRequestedDocumentsPartialState.RequestSent

            processedRequest?.generateResponse(DisclosedDocuments(safeDisclosedDocuments.toList()))
                ?.toKotlinResult()
                ?.onFailure {
                    val errorMessage = it.localizedMessage ?: genericErrorMessage
                    result = SendRequestedDocumentsPartialState.Failure(
                        error = errorMessage
                    )
                }
                ?.onSuccess {
                    eudiWallet.sendResponse(it.response)
                    result = SendRequestedDocumentsPartialState.RequestSent
                }
            result
        } ?: SendRequestedDocumentsPartialState.Failure(
            error = genericErrorMessage
        )
    }

    override fun mappedCallbackStateFlow(): Flow<ResponseReceivedPartialState> {
        return events
            .onEach { response ->
                println("[mappedCallbackStateFlow] raw response: $response")
            }
            .mapNotNull { response ->
                println("[mappedCallbackStateFlow] mapeando response: $response")
                when (response) {
                    is TransferEventPartialState.Error -> {
                        if (response.error == "Peer disconnected without proper session termination") {
                            println("[mappedCallbackStateFlow] -> Success (peer disconnect)")
                            ResponseReceivedPartialState.Success
                        } else {
                            println("[mappedCallbackStateFlow] -> Failure(error=${response.error})")
                            ResponseReceivedPartialState.Failure(error = response.error)
                        }
                    }

                    is TransferEventPartialState.Redirect -> {
                        println("[mappedCallbackStateFlow] -> Redirect(uri=${response.uri})")
                        ResponseReceivedPartialState.Redirect(uri = response.uri)
                    }

                    is TransferEventPartialState.Disconnected -> {
                        val firstWasRedirect = events.replayCache.firstOrNull() is TransferEventPartialState.Redirect
                        println("[mappedCallbackStateFlow] -> Disconnected; firstWasRedirect=$firstWasRedirect")
                        when {
                            firstWasRedirect -> null
                            else -> {
                                println("[mappedCallbackStateFlow] -> Success (normal disconnect)")
                                ResponseReceivedPartialState.Success
                            }
                        }
                    }

                    is TransferEventPartialState.ResponseSent -> {
                        println("[mappedCallbackStateFlow] -> Success (response sent)")
                        ResponseReceivedPartialState.Success
                    }

                    else -> {
                        println("[mappedCallbackStateFlow] -> Ignoring event")
                        null
                    }
                }
            }
            .safeAsync { throwable ->
                println("[mappedCallbackStateFlow] Exception in mapping: ${throwable.localizedMessage}")
                throwable.printStackTrace()
                ResponseReceivedPartialState.Failure(
                    error = throwable.localizedMessage ?: genericErrorMessage
                )
            }
    }

    override fun observeSentDocumentsRequest(): Flow<WalletCorePartialState> =
        merge(checkForKeyUnlock(), mappedCallbackStateFlow()).mapNotNull {
            when (it) {
                is CheckKeyUnlockPartialState.Failure -> {
                    WalletCorePartialState.Failure(it.error)
                }

                is CheckKeyUnlockPartialState.UserAuthenticationRequired -> {
                    WalletCorePartialState.UserAuthenticationRequired(it.authenticationData)
                }

                is ResponseReceivedPartialState.Failure -> {
                    WalletCorePartialState.Failure(it.error)
                }

                is ResponseReceivedPartialState.Redirect -> {
                    WalletCorePartialState.Redirect(
                        uri = it.uri
                    )
                }

                is CheckKeyUnlockPartialState.RequestIsReadyToBeSent -> {
                    WalletCorePartialState.RequestIsReadyToBeSent
                }

                else -> {
                    WalletCorePartialState.Success
                }
            }
        }.safeAsync {
            WalletCorePartialState.Failure(
                error = it.localizedMessage ?: genericErrorMessage
            )
        }

    override fun updateRequestedDocuments(disclosedDocuments: MutableList<DisclosedDocument>?) {
        this.disclosedDocuments = disclosedDocuments
    }

    override fun stopPresentation() {
        coroutineScope.cancel()
        CoroutineScope(dispatcher).launch {
            println("[WalletCorePresentationControllerImpl] stopPresentation")
            eudiWallet.stopProximityPresentation()
        }
    }

    /**
     *If you have problems with the schemas, the function consists of viewing records and comparing
     * them with what you receive, go to Corelogic WalletCoreConfigImpl and then add new records.
     */
//
//    private fun addListener(listener: EudiWalletListenerWrapper) {
//        val config = requireInit { _config }
//        eudiWallet.addTransferEventListener(listener)
//
//        if (config is PresentationControllerConfig.OpenId4VP) {
//            val originalUri = config.uri.toUri()
//            when (originalUri.scheme) {
//                "openid-credential-offer" -> {
//                    val fullOfferUri = config.uri.toString()
//                    println("[addListener] Detected credential-offer URI: $fullOfferUri")
//
//                    documentsController
//                        .resolveDocumentOffer(fullOfferUri)
//                        .onEach { resolveState ->
//                            when (resolveState) {
//                                is ResolveDocumentOfferPartialState.Success -> {
//                                    val offer = resolveState.offer
//                                    println("[addListener] Offer resolved: issuer=${resolveState.offer.credentialOffer}, " +
//                                            "configs=${resolveState.offer.offeredDocuments}," +
//                                            " txCodeSpec=${resolveState.offer.txCodeSpec} " +
//                                            "issuerMetadata ${resolveState.offer.issuerMetadata}")
//
//                                    val rawHttps = originalUri.getQueryParameter("credential_offer_uri")
//                                        ?: throw IllegalStateException("credential_offer_uri missing")
//
//                                    documentsController
//                                        .issueDocumentsByOfferUri(
//                                            offerUri = rawHttps,
//                                            txCode   = (offer.txCodeSpec ?: null) as String?
//                                        )
//                                        .onEach { issueState -> handleIssueState(issueState) }
//                                        .launchIn(coroutineScope)
//                                }
//                                is ResolveDocumentOfferPartialState.Failure -> {
//                                    println("[addListener] Failed to resolve offer: ${resolveState.errorMessage}")
//                                    println("[addListener] originalUri : ${originalUri.host}")
//                                }
//                            }
//                        }
//                        .launchIn(coroutineScope)
//                }
//                else -> {
//                    println("[addListener] Starting presentation on $originalUri")
//                    eudiWallet.startRemotePresentation(originalUri)
//                }
//            }
//        } else {
//            println("[addListener] Config não é OpenId4VP. Pulando.")
//        }
//    }
//
//
//
//    private fun handleIssueState(state: IssueDocumentsPartialState) {
//        when (state) {
//            is IssueDocumentsPartialState.Success -> {
//                println("Issuance SUCCESS: docs = ${state.documentIds}")
//            }
//            is IssueDocumentsPartialState.DeferredSuccess -> {
//                println("Issuance DEFERRED: docs = ${state.deferredDocuments}")
//            }
//            is IssueDocumentsPartialState.UserAuthRequired -> {
//                println("Issuance USER_AUTH_REQUIRED: crypto=${state.crypto}, handler=${state.resultHandler}")
//                // state.resultHandler.onAuthenticationSuccess or .onAuthenticationError
//            }
//            is IssueDocumentsPartialState.Failure -> {
//                println("Issuance FAILED: ${state.errorMessage}")
//            }
//            else -> {
//                println("Issuance STATE: $state")
//            }
//        }
//    }
//


    private fun addListener(listener: EudiWalletListenerWrapper) {
        val config = requireInit { _config }
        eudiWallet.addTransferEventListener(listener)
        if (config is PresentationControllerConfig.OpenId4VP) {
            eudiWallet.startRemotePresentation(config.uri.toUri())
        }
    }


    private fun removeListener(listener: EudiWalletListenerWrapper) {
        requireInit { _config }
        eudiWallet.removeTransferEventListener(listener)
    }

    private fun <T> requireInit(block: () -> T): T {
        if (!::_config.isInitialized) {
            throw IllegalStateException("setConfig() must be called before using the WalletCorePresentationController")
        }
        return block()
    }
}