package eu.europa.ec.dashboardfeature.ui.did.qrcode

import androidx.lifecycle.viewModelScope
import eu.europa.ec.corelogic.model.did.DidDocument
import eu.europa.ec.dashboardfeature.interactor.DidDocumentInteractor
import eu.europa.ec.dashboardfeature.ui.home.BleAvailability
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.annotation.KoinViewModel

data class State(
    val isLoading: Boolean = false,
    val error: ContentErrorConfig? = null,
    val qrCode: String? = null,
    val bleAvailability: BleAvailability = BleAvailability.UNKNOWN,
    val isBleCentralClientModeEnabled: Boolean = false
) : ViewState


sealed class Event : ViewEvent {
    data object Pop : Event()
    data object GenerateQrCode : Event()
    data object StartProximityFlow : Event()
    data object OnShowPermissionsRational : Event()
    data class OnPermissionStateChanged(val availability: BleAvailability) : Event()
    data class DocumentDid(val qrcode: String) : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data object Pop : Navigation()
    }

    data class OpenDocumentSelection(val selection: List<String>) : Effect()
    data class ShowError(val message: String) : Effect()
}

@KoinViewModel
class SharingVcViewModel(
    private val interactor: DidDocumentInteractor,
    private val resourceProvider: ResourceProvider,
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State = State(
        qrCode = null,
        isLoading = false
    )

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Pop -> {
                setEffect { Effect.Navigation.Pop }
            }

            is Event.GenerateQrCode -> {
                generateQrCode()
            }

            is Event.DocumentDid -> {
                setState { copy(qrCode = event.qrcode) }
            }

            is Event.StartProximityFlow -> {
                setState {
                    copy(bleAvailability = BleAvailability.AVAILABLE)
                }
            }

            is Event.OnShowPermissionsRational -> {
                // Show rationale dialog or UI
                setState {
                    copy(bleAvailability = BleAvailability.NO_PERMISSION)
                }
            }

            is Event.OnPermissionStateChanged -> {
                setState {
                    copy(bleAvailability = event.availability)
                }
            }
        }
    }

    private fun generateQrCode() {
        setState { copy(isLoading = true, error = null) }

        viewModelScope.launch {


        }
    }
}
