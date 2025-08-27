package eu.europa.ec.verifierfeature.controller.document

import android.net.Uri
import eu.europa.ec.authenticationlogic.model.biometric.BiometricCrypto
import eu.europa.ec.businesslogic.extension.addOrReplace
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.businesslogic.extension.toUri
import eu.europa.ec.corelogic.controller.CheckKeyUnlockPartialState
import eu.europa.ec.corelogic.controller.PresentationControllerConfig
import eu.europa.ec.corelogic.controller.ResponseReceivedPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.model.AuthenticationData
import eu.europa.ec.corelogic.util.EudiWalletListenerWrapper
import eu.europa.ec.dashboardfeature.interactor.DocumentDetailsInteractor
import eu.europa.ec.dashboardfeature.interactor.DocumentDetailsInteractorPartialState
import eu.europa.ec.eudi.iso18013.transfer.response.DisclosedDocument
import eu.europa.ec.eudi.iso18013.transfer.response.DisclosedDocuments
import eu.europa.ec.eudi.iso18013.transfer.response.RequestProcessor
import eu.europa.ec.eudi.iso18013.transfer.response.RequestedDocument
import eu.europa.ec.eudi.iso18013.transfer.toKotlinResult
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.DocumentExtensions.getDefaultKeyUnlockData
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.verifierfeature.controller.verifier.VerifierController
import eu.europa.ec.verifierfeature.model.FieldLabel
import eu.europa.ec.verifierfeature.model.PresentationResponse
import eu.europa.ec.verifierfeature.ui.initVerifierOther.IntFlowVerifierOtherRequest
import eu.europa.ec.verifierfeature.utils.authorizationRequest.AuthorizationRequest
import eu.europa.ec.verifierfeature.utils.json.AuthRequestData
import eu.europa.ec.verifierfeature.utils.json.DecodeUtils.decodeAuthRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.net.URI


sealed class PresentationControllerVerifierConfig(val initiatorRoute: String) {
    data class OpenId4VP(val uri: String, val initiator: String) :
        PresentationControllerVerifierConfig(initiator)
}


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


sealed class TransferEventVerifiedPartialState  {
    data object Connected : TransferEventVerifiedPartialState ()
    data object Connecting : TransferEventVerifiedPartialState ()
    data object Disconnected : TransferEventVerifiedPartialState ()
    data class Error(val error: String) : TransferEventVerifiedPartialState ()

    data class RequestReceived(
        val requestData: List<RequestedDocument>,
        val verifierName: String?,
        val verifierIsTrusted: Boolean,
    ) : TransferEventVerifiedPartialState ()

    data object ResponseSent : TransferEventVerifiedPartialState ()
    data class Redirect(val uri: URI) : TransferEventVerifiedPartialState ()
}


sealed class SendRequestedDocumentsVerifierPartialState {
    data class Failure(val error: String) : SendRequestedDocumentsVerifierPartialState()
    data object RequestSent : SendRequestedDocumentsVerifierPartialState()
}


sealed class WalletCoreVerifiedPartialState {
    data class UserAuthenticationRequired(
        val authenticationData: List<AuthenticationData>,
    ) : WalletCoreVerifiedPartialState()

    data class Failure(val error: String) : WalletCoreVerifiedPartialState()
    data object Success : WalletCoreVerifiedPartialState()
    data class Redirect(val uri: URI) : WalletCoreVerifiedPartialState()
    data object RequestIsReadyToBeSent : WalletCoreVerifiedPartialState()
}


private data class VerifierSessionData(
    val transactionId: String?,
    val clientId: String?,
    val nonce: String?,
    val state: String?,
    val requestUri: String?,
    val request: String?,
    val requestUriMethod: String?,
    val responseType: String?,
    val responseMode: String?,
    val fields: List<FieldLabel>?,
    val createdAt: Long = System.currentTimeMillis()
)


interface EventPresentationDocumentController {
    /**
     * Who started the presentation
     * */
    val initiatorRoute: String




    /**
     * Flow de eventos de transferência (conexão, pedido recebido, erro, redirect, etc).
     */
    val events: SharedFlow<TransferEventVerifiedPartialState>

    val redirectUri: URI?

    /**
     * Set [PresentationControllerConfig]
     * */
    fun setConfig(config: PresentationControllerConfig)


    /**
     * Verifier name so it can be retrieve across screens
     * */
    val verifierName: String?

    val verifierIsTrusted: Boolean?

    val disclosedDocuments: MutableList<DisclosedDocument>?

    suspend fun getDocumentDetails(documentId: DocumentId): DocumentDetailsInteractorPartialState.Success
    suspend fun intFlowVerifierOther(request: IntFlowVerifierOtherRequest):String


    suspend fun intFlowVerifierAge(documentId: DocumentId, fields: List<FieldLabel>): String

    fun updateRequestedDocuments(disclosedDocuments: MutableList<DisclosedDocument>?)

    fun sendRequestedDocuments(): SendRequestedDocumentsVerifierPartialState

    fun mappedCallbackStateFlow(): Flow<ResponseReceivedPartialState>

    fun observeSentDocumentsRequest(): Flow<WalletCoreVerifiedPartialState>

    fun checkForKeyUnlock(): Flow<CheckKeyUnlockPartialState>

    fun stopListeningAndCleanup()

    fun stopPresentation()

}



class EventPresentationDocumentControllerImpl(
    private val api: VerifierController,
    private val resourceProvider: ResourceProvider,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val documentDetailsInteractor: DocumentDetailsInteractor,
    private val eudiWallet: EudiWallet,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): EventPresentationDocumentController {

    private lateinit var _config: PresentationControllerConfig

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()


    private val listenerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentListener: EudiWalletListenerWrapper? = null

    private var processedRequest: RequestProcessor.ProcessedRequest.Success? = null

    override var verifierName: String? = null

    override var verifierIsTrusted: Boolean? = null

    private val genericErrorMessage = resourceProvider.genericErrorMessage()

    private val _events = MutableSharedFlow<TransferEventVerifiedPartialState>(replay = 1)

    override val events: SharedFlow<TransferEventVerifiedPartialState> = _events.asSharedFlow()


    override var redirectUri: URI? = null

    override val initiatorRoute: String
        get() {
            val config = requireInit { _config }
            return config.initiatorRoute
        }


    override fun setConfig(config: PresentationControllerConfig) {
        _config = config
    }

    override var disclosedDocuments: MutableList<DisclosedDocument>? = null

    @Volatile
    private var verifierSession: VerifierSessionData? = null

    private fun saveVerifierSession(session: VerifierSessionData) {
        verifierSession = session
    }

    private fun clearVerifierSession() {
        verifierSession = null
    }

    private fun getVerifierSessionOrNull(): VerifierSessionData? = verifierSession

    private fun getVerifierSessionOrThrow(): VerifierSessionData {
        return verifierSession ?: throw IllegalStateException("Verifier session not initialized")
    }

    fun getSavedNonce(): String? = verifierSession?.nonce

    fun getSavedTransactionId(): String? = verifierSession?.transactionId

    fun getSavedState(): String? = verifierSession?.state

    fun getSavedRequestUri(): String? = verifierSession?.requestUri


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


    override suspend fun intFlowVerifierAge(documentId: DocumentId, fields: List<FieldLabel>): String {
        val metadata = api.metadataVerifier()

        val pres = api.createPresentationRequest(fields)
//        val pres = api.createPresentationRequestOther()
        println("transaction_id = ${pres.transaction_id}")
        println("client_id      = ${pres.client_id}")
        println("request        = ${pres.request}")
        println("request_uri_method        = ${pres.request_uri_method}")
        println("request_uri        = ${pres.request_uri}")
        fields.forEach { println("Fields {$it.key}") }

        val nonce = api.getLastNonce()

        if (!pres.request.isNullOrBlank()) {
            val authData = decodeAuthRequest(pres.request)
            println("request_uri    = ${authData.requestUri}")
            println("method         = ${authData.responseMode}")
            println("state          = ${authData.state}")
            println("responseType   = ${authData.responseType}")

            val deepLink = AuthorizationRequest.formatAuthorizationRequest(
                pres.client_id,
                authData.requestUri,
                authData.responseMode,
                authData.state,
                nonce,
                fields,
                authData.responseType
            )

            val deepLinkUri = deepLink.toString().toUri()
            println("[intFlowVerifier] deepLink = $deepLinkUri")

            listenerScope.launch {
                startRemotePresentationAndListen(deepLinkUri)
            }

            return deepLink.toString()

        } else {
            val deepLink = AuthorizationRequest.formatAuthorizationRequestApi(
                pres.client_id,
                pres.request_uri,
                pres.request_uri_method
            )

            val deepLinkUri = deepLink.toString().toUri()
            println("[intFlowVerifier] deepLink = $deepLinkUri")

            listenerScope.launch {
                startRemotePresentationAndListen(deepLinkUri)
            }

            return deepLink.toString()
        }
        return ""
    }


    override suspend fun intFlowVerifierOther(request: IntFlowVerifierOtherRequest): String {
        val metadata = api.metadataVerifier()
        val pres = api.createPresentationRequestOther(request)
        println("transaction_id = ${pres.transaction_id}")
        println("client_id      = ${pres.client_id}")
        println("request        = ${pres.request}")
        println("request_uri_method        = ${pres.request_uri_method}")
        println("request_uri        = ${pres.request_uri}")
//        fields.forEach { println("Fields {$it.key}") }

        val nonce = api.getLastNonce()

        val deepLink = AuthorizationRequest.formatAuthorizationRequestApi(
            pres.client_id,
            pres.request_uri,
            pres.request_uri_method
        )

        val deepLinkUri = deepLink.toString().toUri()
        println("[intFlowVerifier] deepLink = $deepLinkUri")
        listenerScope.launch {
            startRemotePresentationAndListen(deepLinkUri)
        }
        return deepLink.toString()
    }


    private fun startRemotePresentationAndListen(deepLinkUri: Uri) {
        println("=== startRemotePresentationAndListen ===")
        println("DeepLink URI: $deepLinkUri")
        println("========================================")

        eudiWallet.startRemotePresentation(deepLinkUri)

        val listener = EudiWalletListenerWrapper(
            onRequestReceived = { requestedDocumentData ->
                listenerScope.launch {
                    val processed = requestedDocumentData.getOrNull()
                    println("\n--- onRequestReceived ---")
                    println("processed ${processed}")
                    println("verifierName ${processed?.requestedDocuments?.firstOrNull()?.readerAuth?.isVerified}")
                    println("verifierIsTrusted ${processed?.requestedDocuments?.firstOrNull()?.readerAuth?.readerCommonName}")

                    processed?.let { p ->
                        processedRequest = p

                        verifierName = p.requestedDocuments
                            .firstOrNull()?.readerAuth?.readerCommonName
                        verifierIsTrusted = p.requestedDocuments
                            .firstOrNull()?.readerAuth?.isVerified == true

                        val isTrusted = verifierIsTrusted == true

                        val state = TransferEventVerifiedPartialState.RequestReceived(
                            requestData = p.requestedDocuments,
                            verifierName = verifierName,
                            verifierIsTrusted = isTrusted
                        )

                        println("Verified State $state")
                        _events.emit(state)
                    } ?: run {

                        _events.emit(
                            TransferEventVerifiedPartialState.Error(
                                error = genericErrorMessage
                            )
                        )
                    }
                }
            },

            onQrEngagementReady = { qrCode ->
                println("\n--- onQrEngagementReady ---")
                println("QR Code: $qrCode")
                println("----------------------------\n")
                /*
                * In this case it will not always be used empty.
                * */
            },

            onConnected = {
                println("\n--- onConnected ---")
                println("Status: Conectado ao verificador")
                println("--------------------\n")
                listenerScope.launch { _events.emit(TransferEventVerifiedPartialState.Connected) }
            },

            onConnecting = {
                println("\n--- onConnecting ---")
                println("Status: Tentando conectar ao verificador...")
                println("--------------------\n")
                listenerScope.launch { _events.emit(TransferEventVerifiedPartialState.Connecting) }
            },

            onDisconnected = {
                println("\n--- onDisconnected ---")
                println("Status: Desconectado")
                println("----------------------\n")
                listenerScope.launch { _events.emit(TransferEventVerifiedPartialState.Disconnected) }
            },

            onError = { errorMessage ->
                println("\n--- onError ---")
                println("Erro: ${errorMessage.ifEmpty { genericErrorMsg }}")
                println("----------------\n")

                listenerScope.launch {
                    _events.emit(
                        TransferEventVerifiedPartialState.Error(
                            error = errorMessage.ifEmpty { genericErrorMessage }
                        )
                    )
                }
            },

            onRedirect = { uri ->
                println("\n--- onRedirect ---")
                println("URI de redirecionamento: $uri")
                println("------------------------\n")
                listenerScope.launch { _events.emit(TransferEventVerifiedPartialState.Redirect(uri)) }
            },

            onResponseSent = {
                println("\n--- onResponseSent ---")
                println("Resposta enviada para o verificador")
                println("-----------------------\n")

                listenerScope.launch { _events.emit(TransferEventVerifiedPartialState.ResponseSent) }
            }
        )

        currentListener = listener
        eudiWallet.addTransferEventListener(listener)
        println(" Listener registrado\n")
    }


    private fun transactionState(){
        val transactionId = getSavedTransactionId() ?: return
        listenerScope.launch {
            api.getPresentationState(transactionId)
            api.getTransactionEventsLogs(transactionId)
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

    override fun observeSentDocumentsRequest(): Flow<WalletCoreVerifiedPartialState> =
        merge(checkForKeyUnlock(), mappedCallbackStateFlow()).mapNotNull {
            when (it) {
                is CheckKeyUnlockPartialState.Failure -> {
                    WalletCoreVerifiedPartialState.Failure(it.error)
                }

                is CheckKeyUnlockPartialState.UserAuthenticationRequired -> {
                    WalletCoreVerifiedPartialState.UserAuthenticationRequired(it.authenticationData)
                }

                is ResponseReceivedPartialState.Failure -> {
                    WalletCoreVerifiedPartialState.Failure(it.error)
                }

                is ResponseReceivedPartialState.Redirect -> {
                    WalletCoreVerifiedPartialState.Redirect(
                        uri = it.uri
                    )
                }

                is CheckKeyUnlockPartialState.RequestIsReadyToBeSent -> {
                    WalletCoreVerifiedPartialState.RequestIsReadyToBeSent
                }

                else -> {
                    WalletCoreVerifiedPartialState.Success
                }
            }
        }.safeAsync {
            WalletCoreVerifiedPartialState.Failure(
                error = it.localizedMessage ?: genericErrorMessage
            )
        }



    override fun sendRequestedDocuments(): SendRequestedDocumentsVerifierPartialState {
        return disclosedDocuments?.let { safeDisclosedDocuments ->

            var result: SendRequestedDocumentsVerifierPartialState =
                SendRequestedDocumentsVerifierPartialState.RequestSent

            processedRequest?.generateResponse(DisclosedDocuments(safeDisclosedDocuments.toList()))
                ?.toKotlinResult()
                ?.onFailure {
                    val errorMessage = it.localizedMessage ?: genericErrorMessage
                    result = SendRequestedDocumentsVerifierPartialState.Failure(
                        error = errorMessage
                    )
                }
                ?.onSuccess {
                    eudiWallet.sendResponse(it.response)
                    result = SendRequestedDocumentsVerifierPartialState.RequestSent
                }
            result
        } ?: SendRequestedDocumentsVerifierPartialState.Failure(
            error = genericErrorMessage
        )
    }


    override fun mappedCallbackStateFlow(): Flow<ResponseReceivedPartialState> {
        return events.mapNotNull { response ->
            when (response) {

                // Fix: Wallet core should return Success state here
                is TransferEventVerifiedPartialState.Error -> {
                    if (response.error == "Peer disconnected without proper session termination") {
                        ResponseReceivedPartialState.Success
                    } else {
                        ResponseReceivedPartialState.Failure(error = response.error)
                    }
                }

                is TransferEventVerifiedPartialState.Redirect -> {
                    ResponseReceivedPartialState.Redirect(uri = response.uri)
                }

                is TransferEventVerifiedPartialState.Disconnected -> {
                    when {
                        events.replayCache.firstOrNull() is TransferEventVerifiedPartialState.Redirect -> null
                        else -> ResponseReceivedPartialState.Success
                    }
                }

                is TransferEventVerifiedPartialState.ResponseSent -> ResponseReceivedPartialState.Success

                else -> null
            }
        }.safeAsync {
            ResponseReceivedPartialState.Failure(
                error = it.localizedMessage ?: genericErrorMessage
            )
        }
    }


    override fun updateRequestedDocuments(disclosedDocuments: MutableList<DisclosedDocument>?) {
        this.disclosedDocuments = disclosedDocuments
    }


    override fun stopListeningAndCleanup() {
        currentListener?.let {
            println("Removing listener")
            eudiWallet.removeTransferEventListener(it)
            currentListener = null
        }
        listenerScope.coroutineContext.cancel()
        try {
            eudiWallet.stopProximityPresentation()
        } catch (_: Exception) { }
    }


    override fun stopPresentation() {
        listenerScope.cancel()
        CoroutineScope(dispatcher).launch {
            eudiWallet.stopProximityPresentation()
        }
    }

    private fun <T> requireInit(block: () -> T): T {
        if (!::_config.isInitialized) {
            throw IllegalStateException("setConfig() must be called before using the WalletCorePresentationController")
        }
        return block()
    }

    private suspend fun postDirectWallet(authData:AuthRequestData, pres: PresentationResponse ){
                    val vpTokenString: String? = if (authData.responseType?.contains("vp_token") == true) {
                val key = pres.client_id ?: pres.transaction_id ?: "vp_token"

                val vpJson = buildJsonObject {
                    put(key, buildJsonArray {
                        add(buildJsonObject {
                            put("id", JsonPrimitive("proof_of_age"))
                        })
                    })
                }
                Json.encodeToString(JsonObject.serializer(), vpJson)
            } else {
                null
            }

            val response = if (vpTokenString != null) {
                api.directPost(authData.state, vpTokenString)
            } else {
                api.directPost(authData.state, "{}")
            }

            if (!response.isSuccessful) {
                val err = response.errorBody()?.string()
                throw RuntimeException("directPost failed ${response.code()}: $err")
            }

            val body = response.body()
            println("directPost response: $body")
    }


}