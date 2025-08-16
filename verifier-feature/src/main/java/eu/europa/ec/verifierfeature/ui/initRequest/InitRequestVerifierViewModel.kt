package eu.europa.ec.verifierfeature.ui.initRequest


import android.net.Uri
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
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.PresentationScreens
import eu.europa.ec.uilogic.navigation.VerifierScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import eu.europa.ec.verifierfeature.controller.document.EventPresentationDocumentController
import eu.europa.ec.verifierfeature.interactor.PresentationRequestVerifierInteractor
import eu.europa.ec.verifierfeature.ui.fieldLabelsPrrofAge.model.RequestArgs
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam


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
class InitRequestVerifierViewModel(
    private val resourceProvider: ResourceProvider,
    private val uiSerializer: UiSerializer,
    private val presentationRequestVerifierInteractor: PresentationRequestVerifierInteractor,
    private val eventPresentationDocumentController: EventPresentationDocumentController,
    @InjectedParam private val encodedArgs: String,
    ) : MviViewModel<Event, State, Effect>() {
    private lateinit var requestArgs: RequestArgs

    override fun setInitialState(): State {

        val decodedJson = Uri.decode(encodedArgs)
        requestArgs = Json.decodeFromString(decodedJson)

        println("[RequestVerifierViewModel] documentId: ${requestArgs.documentId}")
        println("[RequestVerifierViewModel] detailsType: ${requestArgs.detailsType}")
        println("[RequestVerifierViewModel] fieldLabels: ${requestArgs.fieldLabels.size}")
        viewModelScope.launch {

            val success = eventPresentationDocumentController.getDocumentDetails(requestArgs.documentId)
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

            setState {
                copy(
                    isLoading = false,
                    availableFields = availableFields,
                )
            }


        }

        return State(
            isLoading = true,
            availableFields = emptyList(),
        )

    }

    override fun handleEvents(event: Event) {
        when (event) {
            Event.SubmitSelection -> {
                val selected = viewState.value.selectedFieldLabels.toList()
              viewModelScope.launch {
                  val uri =  eventPresentationDocumentController
                        .intFlowVerifier(requestArgs.documentId, requestArgs.fieldLabels)

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
