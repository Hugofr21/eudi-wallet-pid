package eu.europa.ec.verifierfeature.interactor

import androidx.lifecycle.viewModelScope
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.businesslogic.provider.UuidProvider
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.config.toDomainConfig
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentItemUi
import eu.europa.ec.commonfeature.ui.request.transformer.RequestTransformer
import eu.europa.ec.corelogic.controller.TransferEventPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.verifierfeature.controller.document.EventPresentationDocumentController
import eu.europa.ec.verifierfeature.controller.document.TransferEventVerifiedPartialState
import eu.europa.ec.verifierfeature.model.FieldLabel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch


sealed class PresentationRequestVerifierInteractorPartialState {
    data class Success(
        val verifierName: String?,
        val verifierIsTrusted: Boolean,
        val requestDocuments: List<RequestDocumentItemUi>
    ) : PresentationRequestVerifierInteractorPartialState()

    data class NoData(
        val verifierName: String?,
        val verifierIsTrusted: Boolean,
    ) : PresentationRequestVerifierInteractorPartialState()

    data class Failure(val error: String) : PresentationRequestVerifierInteractorPartialState()
    data object Disconnect : PresentationRequestVerifierInteractorPartialState()
}

interface PresentationRequestVerifierInteractor {
    suspend fun  getRequestDocuments(): Flow<PresentationRequestVerifierInteractorPartialState>
    fun stopPresentation()
    fun updateRequestedDocuments(items: List<RequestDocumentItemUi>)
    fun setConfig(config: RequestUriConfig)
}

class PresentationRequestVerifierInteractorImpl(
    private val resourceProvider: ResourceProvider,
    private val uuidProvider: UuidProvider,
    private val eventPresentationDocumentController: EventPresentationDocumentController,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : PresentationRequestVerifierInteractor {

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    override fun setConfig(config: RequestUriConfig) {
        eventPresentationDocumentController.setConfig(config.toDomainConfig())
    }

    override suspend fun getRequestDocuments(): Flow<PresentationRequestVerifierInteractorPartialState> {

        return eventPresentationDocumentController.events.mapNotNull { response ->
            println("Received response: $response")

            when (response) {
                is TransferEventVerifiedPartialState.RequestReceived -> {
                    println("RequestReceived from: ${response.verifierName}, Trusted: ${response.verifierIsTrusted}")
                    println("Request data: ${response.requestData}")

                    if (response.requestData.all { it.requestedItems.isEmpty() }) {
                        println("All requested items are empty")
                        PresentationRequestVerifierInteractorPartialState.NoData(
                            verifierName = response.verifierName,
                            verifierIsTrusted = response.verifierIsTrusted,
                        )
                    } else {
                        val allDocuments = walletCoreDocumentsController.getAllIssuedDocuments()
                        println("All issued documents: $allDocuments")

                        val documentsDomain = RequestTransformer.transformToDomainItems(
                            storageDocuments = allDocuments,
                            requestDocuments = response.requestData,
                            resourceProvider = resourceProvider,
                            uuidProvider = uuidProvider
                        ).getOrThrow()
                            .filterNot { document ->
                                val isRevoked =
                                    walletCoreDocumentsController.isDocumentRevoked(document.docId)
                                println(
                                    buildString {
                                        appendLine("────────── Document ──────────")
                                        appendLine("ID.............: ${document.docId}")
                                        appendLine("Name...........: ${document.docName}")
                                        appendLine("Format........: ${document.domainDocFormat}")
                                        appendLine("Revoked.......: $isRevoked")
                                        appendLine("Claims:")
                                        document.docClaimsDomain.forEach { claim ->
                                            appendLine("  - Title: ${claim.displayTitle}")
                                            appendLine("    Key : ${claim.key}")
                                            appendLine("    Path  : ${claim.path}")
                                        }
                                        appendLine("──────────────────────────────")
                                    }
                                )

                                isRevoked
                            }

                        println("Filtered domain documents: $documentsDomain")

                        if (documentsDomain.isNotEmpty()) {
                            val uiItems = RequestTransformer.transformToUiItems(
                                documentsDomain = documentsDomain,
                                resourceProvider = resourceProvider,
                            )
                            println("UI Items: $uiItems")

                            PresentationRequestVerifierInteractorPartialState.Success(
                                verifierName = response.verifierName,
                                verifierIsTrusted = response.verifierIsTrusted,
                                requestDocuments = uiItems
                            )
                        } else {
                            println("No valid documents matched the request")
                            PresentationRequestVerifierInteractorPartialState.NoData(
                                verifierName = response.verifierName,
                                verifierIsTrusted = response.verifierIsTrusted,
                            )
                        }
                    }
                }

                is TransferEventVerifiedPartialState.Error -> {
                    println("Error received: ${response.error}")
                    PresentationRequestVerifierInteractorPartialState.Failure(error = response.error)
                }

                is TransferEventVerifiedPartialState.Disconnected -> {
                    println("Disconnected from verifier")
                    PresentationRequestVerifierInteractorPartialState.Disconnect
                }

                else -> {
                    println("Unknown response type: $response")
                    null
                }
            }
        }.safeAsync {
            println("Exception caught in flow: ${it.localizedMessage}")
            PresentationRequestVerifierInteractorPartialState.Failure(
                error = it.localizedMessage ?: genericErrorMsg
            )
        }
    }


    override fun stopPresentation() {
        eventPresentationDocumentController.stopPresentation()
    }

    override fun updateRequestedDocuments(items: List<RequestDocumentItemUi>) {
        val disclosedDocuments = RequestTransformer.createDisclosedDocuments(items)
        eventPresentationDocumentController.updateRequestedDocuments(disclosedDocuments.toMutableList())
    }
}