package eu.europa.ec.verifierfeature.ui.request


import android.net.Uri
import androidx.lifecycle.viewModelScope
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.verifierfeature.interactor.AgeProofInteractor
import eu.europa.ec.verifierfeature.ui.fieldLabelsPrrofAge.model.RequestArgs
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam


data class State(
    val isLoading: Boolean = false,
    val availableFields: List<ListItemDataUi> = emptyList(),
    val selectedFieldLabels: Set<String> = emptySet()
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
class RequestVerifierViewModel(
    private val interactor: AgeProofInteractor,
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
          val document =  interactor.getDocumentDetails(requestArgs.documentId)

            interactor.intFlowVerifier(requestArgs.documentId,requestArgs.fieldLabels)
        }

        return State(

        )
    }

    override fun handleEvents(event: Event) {
        when (event) {

            Event.SubmitSelection -> {


            }

            Event.GoBack -> setEffect { Effect.Navigation.Pop }

            is Event.ToggleFieldLabel -> {

            }
        }
    }
}
