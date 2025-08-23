package eu.europa.ec.verifierfeature.ui.initVerifierOther


import androidx.lifecycle.viewModelScope
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.corelogic.model.ClaimDomain
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.VerifierScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import eu.europa.ec.verifierfeature.controller.document.EventPresentationDocumentController
import eu.europa.ec.verifierfeature.interactor.PresentationRequestVerifierInteractor
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam


data class IntFlowVerifierOtherRequest(
    val documentId: String,
    val docName: String?,
    val docIdentifier: String?,
    val cleanDocumentId: String,
    val issuerName: String?,
    val issuerLogo: String?,
    val isBookmarked: Boolean,
    val isRevoked: Boolean,
    val selectedFields: List<String>,
    val allClaims: Map<String, String>,
    val timestampUtc: Long = System.currentTimeMillis()
)

data class ListItemData(
    val itemId: String,
    val title: String,
    val subtitle: String? = null,
    val isMandatory: Boolean = false,
)

data class State(
    val isLoading: Boolean = false,
    val availableFields: List<ListItemData> = emptyList(),
    val selectedFieldLabels: Set<String> = emptySet(),
    val issuerName: String? = null,
    val issuerLogo: String? = null,
    val docName: String? = null,
    val documentIdentifier: String? = null,
    val isRevoked: Boolean = false,
    val isBookmarked: Boolean = false,
    val allClaims: Map<String, String> = emptyMap(),
    val notifyOnAuthenticationFailure: Boolean = false
) : ViewState

sealed class Event : ViewEvent {
    data class ToggleFieldLabel(val key: String, val isChecked: Boolean) : Event()
    object SubmitSelection : Event()
    object GoBack : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        object Pop : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Profile.screenRoute,
            val inclusive: Boolean = false
        ) : Navigation()
    }
}


@KoinViewModel
class InitRequestVerifierOtherViewModel(
    private val resourceProvider: ResourceProvider,
    private val uiSerializer: UiSerializer,
    private val presentationRequestVerifierInteractor: PresentationRequestVerifierInteractor,
    private val eventPresentationDocumentController: EventPresentationDocumentController,
    @InjectedParam private val documentId: String,
    ) : MviViewModel<Event, State, Effect>() {

    private val cleanDocumentId: String by lazy {
        documentId.substringAfterLast("=")
            .substringAfterLast("?")
    }



    override fun setInitialState(): State {
        println("[InitRequestVerifierOtherViewModel] documentId (raw): $documentId")
        println("[InitRequestVerifierOtherViewModel] documentId (clean): $cleanDocumentId")
        viewModelScope.launch {

            val success = eventPresentationDocumentController.getDocumentDetails(cleanDocumentId)
            println("------------------------------ Doc type -------------------------------------")
            println("Issuer: ${success.issuerName}")
            println("Logo URI: ${success.issuerLogo}")
            println("Is bookmarked: ${success.documentIsBookmarked}")
            println("Is revoked: ${success.isRevoked}")
            println("Credentials info: ${success.documentCredentialsInfoUi}")
            println("Claims: ${success.documentDetailsDomain.documentClaims}")
            println("Doc Name: ${success.documentDetailsDomain.docName}")
            println("Document Identifier: ${success.documentDetailsDomain.documentIdentifier}")
            println("-------------------------------------------------------------------------\n")

            val availableFields = success.documentDetailsDomain.documentClaims.mapNotNull { claim ->
                when (claim) {
                    is ClaimDomain.Primitive -> ListItemData(
                        itemId = claim.key,
                        title = claim.displayTitle,
                        subtitle = claim.value,
                        isMandatory = true
                    )
                    else -> null
                }
            }

            val allClaims: Map<String, String> = success.documentDetailsDomain.documentClaims.mapNotNull { claim ->
                when (claim) {
                    is ClaimDomain.Primitive -> claim.key to (claim.value ?: "")
                    else -> null
                }
            }.toMap()


            setState {
                copy(
                    isLoading = false,
                    availableFields = availableFields,
                    issuerName = success.issuerName,
                    issuerLogo = success.issuerLogo?.toString(),
                    docName = success.documentDetailsDomain.docName,
                    documentIdentifier = success.documentDetailsDomain.documentIdentifier?.toString(),
                    isRevoked = success.isRevoked,
                    isBookmarked = success.documentIsBookmarked,
                    allClaims = allClaims
                )
            }


        }

        return State(isLoading = true)
    }

    override fun handleEvents(event: Event) {
        when (event) {
            Event.SubmitSelection -> {
                val selected = viewState.value.selectedFieldLabels.toList()
                val current = viewState.value

                val request = IntFlowVerifierOtherRequest(
                    documentId = documentId,
                    docName = current.docName,
                    docIdentifier = current.documentIdentifier,
                    cleanDocumentId = cleanDocumentId,
                    issuerName = current.issuerName,
                    issuerLogo = current.issuerLogo,
                    isBookmarked = current.isBookmarked,
                    isRevoked = current.isRevoked,
                    selectedFields = selected,
                    allClaims = current.allClaims,
                    timestampUtc = System.currentTimeMillis()
                )

              viewModelScope.launch {
                  val uri =  eventPresentationDocumentController
                        .intFlowVerifierOther(request)

                    setEffect {
                        Effect.Navigation.SwitchScreen(
                            generateComposableNavigationLink(
                                VerifierScreens.PresentationRequestVerifier,
                                generateComposableArguments(
                                    mapOf(
                                        RequestUriConfig.serializedKeyName to uiSerializer.toBase64(
                                            RequestUriConfig(
                                                PresentationMode.OpenId4Vp(
                                                    uri,
                                                    VerifierScreens.RequestVerifier.screenRoute
                                                )
                                            ),
                                            RequestUriConfig.Parser
                                        )
                                    )
                                )
                            ),
                            inclusive = false
                        )
                    }
                }

            }
            Event.GoBack -> setEffect { Effect.Navigation.Pop }
            is Event.ToggleFieldLabel -> {
                val updated = if (event.isChecked) {
                    viewState.value.selectedFieldLabels + event.key
                } else {
                    viewState.value.selectedFieldLabels - event.key
                }
                setState { copy(selectedFieldLabels = updated) }
            }
        }
    }
}
