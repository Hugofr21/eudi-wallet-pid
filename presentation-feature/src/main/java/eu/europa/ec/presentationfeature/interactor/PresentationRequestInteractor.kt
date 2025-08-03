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

package eu.europa.ec.presentationfeature.interactor

import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.businesslogic.provider.UuidProvider
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.config.toDomainConfig
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentItemUi
import eu.europa.ec.commonfeature.ui.request.transformer.RequestTransformer
import eu.europa.ec.corelogic.controller.TransferEventPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull


sealed class PresentationRequestInteractorPartialState {
    data class Success(
        val verifierName: String?,
        val verifierIsTrusted: Boolean,
        val requestDocuments: List<RequestDocumentItemUi>
    ) : PresentationRequestInteractorPartialState()

    data class NoData(
        val verifierName: String?,
        val verifierIsTrusted: Boolean,
    ) : PresentationRequestInteractorPartialState()

    data class Failure(val error: String) : PresentationRequestInteractorPartialState()
    data object Disconnect : PresentationRequestInteractorPartialState()
}

interface PresentationRequestInteractor {
    fun getRequestDocuments(): Flow<PresentationRequestInteractorPartialState>
    fun stopPresentation()
    fun updateRequestedDocuments(items: List<RequestDocumentItemUi>)
    fun setConfig(config: RequestUriConfig)
}

class PresentationRequestInteractorImpl(
    private val resourceProvider: ResourceProvider,
    private val uuidProvider: UuidProvider,
    private val walletCorePresentationController: WalletCorePresentationController,
    private val walletCoreDocumentsController: WalletCoreDocumentsController
) : PresentationRequestInteractor {

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override fun setConfig(config: RequestUriConfig) {
        walletCorePresentationController.setConfig(config.toDomainConfig())
    }

    override fun getRequestDocuments(): Flow<PresentationRequestInteractorPartialState> =
        walletCorePresentationController.events.mapNotNull { response ->
            println("Received response: $response")

            when (response) {
                is TransferEventPartialState.RequestReceived -> {
                    println("RequestReceived from: ${response.verifierName}, Trusted: ${response.verifierIsTrusted}")
                    println("Request data: ${response.requestData}")

                    if (response.requestData.all { it.requestedItems.isEmpty() }) {
                        println("All requested items are empty")
                        PresentationRequestInteractorPartialState.NoData(
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
                            .filterNot {
                                val isRevoked = walletCoreDocumentsController.isDocumentRevoked(it.docId)
                                println("DocId: ${it.docId}, isRevoked: $isRevoked")
                                isRevoked
                            }

                        println("Filtered domain documents: $documentsDomain")

                        if (documentsDomain.isNotEmpty()) {
                            val uiItems = RequestTransformer.transformToUiItems(
                                documentsDomain = documentsDomain,
                                resourceProvider = resourceProvider,
                            )
                            println("UI Items: $uiItems")

                            PresentationRequestInteractorPartialState.Success(
                                verifierName = response.verifierName,
                                verifierIsTrusted = response.verifierIsTrusted,
                                requestDocuments = uiItems
                            )
                        } else {
                            println("No valid documents matched the request")
                            PresentationRequestInteractorPartialState.NoData(
                                verifierName = response.verifierName,
                                verifierIsTrusted = response.verifierIsTrusted,
                            )
                        }
                    }
                }

                is TransferEventPartialState.Error -> {
                    println("Error received: ${response.error}")
                    PresentationRequestInteractorPartialState.Failure(error = response.error)
                }

                is TransferEventPartialState.Disconnected -> {
                    println("Disconnected from verifier")
                    PresentationRequestInteractorPartialState.Disconnect
                }

                else -> {
                    println("Unknown response type: $response")
                    null
                }
            }
        }.safeAsync {
            println("Exception caught in flow: ${it.localizedMessage}")
            PresentationRequestInteractorPartialState.Failure(
                error = it.localizedMessage ?: genericErrorMsg
            )
        }


    override fun stopPresentation() {
        walletCorePresentationController.stopPresentation()
    }

    override fun updateRequestedDocuments(items: List<RequestDocumentItemUi>) {
        val disclosedDocuments = RequestTransformer.createDisclosedDocuments(items)
        walletCorePresentationController.updateRequestedDocuments(disclosedDocuments.toMutableList())
    }
}