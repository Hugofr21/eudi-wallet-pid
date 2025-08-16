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

package eu.europa.ec.verifierfeature.interactor

import eu.europa.ec.businesslogic.extension.ifEmptyOrNull
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.businesslogic.provider.UuidProvider
import eu.europa.ec.commonfeature.extension.toExpandableListItems
import eu.europa.ec.commonfeature.util.transformPathsToDomainClaims
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.corelogic.extension.toClaimPath
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import eu.europa.ec.uilogic.component.RelyingPartyDataUi
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.component.wrap.ExpandableListItemUi
import eu.europa.ec.verifierfeature.controller.document.EventPresentationDocumentController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.net.URI

sealed class PresentationSuccessVerifierInteractorGetUiItemsPartialState {
    data class Success(
        val documentsUi: List<ExpandableListItemUi.NestedListItem>,
        val headerConfig: ContentHeaderConfig,
    ) : PresentationSuccessVerifierInteractorGetUiItemsPartialState()

    data class Failed(
        val errorMessage: String
    ) : PresentationSuccessVerifierInteractorGetUiItemsPartialState()
}

interface PresentationSuccessVerifierInteractor {
    val initiatorRoute: String
    val redirectUri: URI?
    fun getUiItems(): Flow<PresentationSuccessVerifierInteractorGetUiItemsPartialState>
    fun stopPresentation()
}

class PresentationSuccessVerifierInteractorImpl(
    private val eventPresentationDocumentController: EventPresentationDocumentController,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val resourceProvider: ResourceProvider,
    private val uuidProvider: UuidProvider
) : PresentationSuccessVerifierInteractor {

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override val initiatorRoute: String = eventPresentationDocumentController.initiatorRoute

    override val redirectUri: URI? = eventPresentationDocumentController.redirectUri

    override fun getUiItems(): Flow<PresentationSuccessVerifierInteractorGetUiItemsPartialState> {
        return flow {

            val documentsUi = mutableListOf<ExpandableListItemUi.NestedListItem>()

            val verifierName = eventPresentationDocumentController.verifierName

            val isVerified = eventPresentationDocumentController.verifierIsTrusted == true

            eventPresentationDocumentController.disclosedDocuments?.forEach { disclosedDocument ->
                try {
                    val documentId = disclosedDocument.documentId
                    val document =
                        walletCoreDocumentsController.getDocumentById(documentId = documentId) as IssuedDocument

                    val disclosedClaimPaths = disclosedDocument.disclosedItems.map {
                        it.toClaimPath()
                    }

                    val disclosedClaims = transformPathsToDomainClaims(
                        paths = disclosedClaimPaths,
                        claims = document.data.claims,
                        resourceProvider = resourceProvider,
                        uuidProvider = uuidProvider
                    )

                    val disclosedClaimsUi = disclosedClaims.map { disclosedClaim ->
                        disclosedClaim.toExpandableListItems(docId = documentId)
                    }

                    if (disclosedClaimsUi.isNotEmpty()) {
                        val disclosedDocumentUi = ExpandableListItemUi.NestedListItem(
                            header = ListItemDataUi(
                                itemId = documentId,
                                mainContentData = ListItemMainContentDataUi.Text(text = document.name),
                                supportingText = resourceProvider.getString(R.string.document_success_collapsed_supporting_text),
                                trailingContentData = ListItemTrailingContentDataUi.Icon(
                                    iconData = AppIcons.KeyboardArrowDown
                                )
                            ),
                            nestedItems = disclosedClaimsUi,
                            isExpanded = false,
                        )

                        documentsUi.add(disclosedDocumentUi)
                    }
                } catch (_: Exception) {
                }
            }

            val headerConfigDescription = if (documentsUi.isEmpty()) {
                resourceProvider.getString(R.string.document_success_header_description_when_error)
            } else {
                resourceProvider.getString(R.string.document_success_header_description)
            }
            val headerConfig = ContentHeaderConfig(
                description = headerConfigDescription,
                relyingPartyData = RelyingPartyDataUi(
                    name = verifierName.ifEmptyOrNull(
                        default = resourceProvider.getString(R.string.document_success_relying_party_default_name)
                    ),
                    isVerified = isVerified,
                )
            )

            emit(
                PresentationSuccessVerifierInteractorGetUiItemsPartialState.Success(
                    documentsUi = documentsUi,
                    headerConfig = headerConfig
                )
            )
        }.safeAsync {
            PresentationSuccessVerifierInteractorGetUiItemsPartialState.Failed(
                errorMessage = it.localizedMessage ?: genericErrorMsg
            )
        }
    }

    override fun stopPresentation() {
        eventPresentationDocumentController.stopPresentation()
    }
}