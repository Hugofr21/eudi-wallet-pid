package eu.europa.ec.verifierfeature.ui.fieldLabelsPrrofAge

import android.net.Uri
import androidx.lifecycle.viewModelScope
import eu.europa.ec.commonfeature.config.IssuanceFlowType
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.VerifierScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.verifierfeature.model.FieldLabel
import eu.europa.ec.verifierfeature.model.fieldLabels
import eu.europa.ec.verifierfeature.ui.fieldLabelsPrrofAge.model.RequestArgs
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam


data class State(
    val isLoading: Boolean = false,
    val options: List<FieldLabel> = emptyList(),
    val selectedKeys: Set<String> = emptySet()
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
            val inclusive: Boolean = false,
        ) : Navigation()
    }
}

@KoinViewModel
class FieldLabelsProofAgeViewModel(
    @InjectedParam private val documentId: DocumentId,
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State {
        println("[FieldLabelsProofAgeViewModel] document $documentId")
        return State(
            options = fieldLabels
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.ToggleFieldLabel -> {
//                val current = viewState.value.selectedKeys.toMutableSet()
//                if (event.isChecked) current += event.key
//                else                current -= event.key
//                setState { copy(selectedKeys = current) }
                val newSelection = if (event.isChecked) {
                    setOf(event.key)
                } else {
                    emptySet()
                }
                setState { copy(selectedKeys = newSelection) }
            }

            Event.SubmitSelection -> {
                val selected = viewState.value.options
                    .filter { it.key in viewState.value.selectedKeys }
                viewModelScope.launch {
                    setEffect {

                        val requestArgs = RequestArgs(
                            detailsType = IssuanceFlowType.ExtraDocument(formatType = null),
                            documentId = documentId,
                            fieldLabels = selected
                        )

                        val json = Json.encodeToString(requestArgs)
                        val encodedJson = Uri.encode(json)


                        Effect.Navigation.SwitchScreen(
                            screenRoute = "${VerifierScreens.RequestVerifier.screenRoute}?args=$encodedJson"
                        )
                    }
                }
            }

            Event.GoBack -> setEffect { Effect.Navigation.Pop }
        }
    }


}
