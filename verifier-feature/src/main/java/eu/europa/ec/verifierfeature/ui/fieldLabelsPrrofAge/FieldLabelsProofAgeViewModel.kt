package eu.europa.ec.verifierfeature.ui.fieldLabelsPrrofAge

import androidx.lifecycle.viewModelScope
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.verifierfeature.model.FieldLabel
import eu.europa.ec.verifierfeature.model.VerifierModule
import eu.europa.ec.verifierfeature.model.fieldLabels
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel


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
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State {
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

                }
            }

            Event.GoBack -> setEffect { Effect.Navigation.Pop }
        }
    }
}
