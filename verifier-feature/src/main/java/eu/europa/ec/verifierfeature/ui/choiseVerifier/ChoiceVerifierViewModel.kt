package eu.europa.ec.verifierfeature.ui.choiseVerifier

import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.verifierfeature.model.VerifierModule
import org.koin.android.annotation.KoinViewModel

data class VerifierItem(
    val id: String,
    val displayName: String,
    val isSelected: Boolean
)

data class State(
    val isLoading: Boolean = false,
    val trustListVerifier: List<String> = emptyList(),
    val verifiers: List<VerifierItem> = emptyList()
) : ViewState

sealed class Event : ViewEvent {
    object GoBack : Event()
    data class ToggleVerifier(val verifierId: String, val isChecked: Boolean) : Event()
    object SubmitSelection : Event()
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
class ChoiceVerifierViewModel(
) : MviViewModel<Event, State, Effect>() {
    override fun setInitialState(): State = with(VerifierModule) {
        val options = optionsVerifierName()
        val items = options.map { id -> VerifierItem(id = id, displayName = id, isSelected = false) }
        return State(
            trustListVerifier = options,
            verifiers = items
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.ToggleVerifier -> {
                val updated = viewState.value.verifiers.map {
                    if (it.id == event.verifierId) it.copy(isSelected = event.isChecked) else it
                }
                setState { copy(verifiers = updated) }
            }
            Event.SubmitSelection -> {
                val selected = viewState.value.verifiers.filter { it.isSelected }.map { it.id }

            }
            Event.GoBack -> setEffect { Effect.Navigation.Pop }
        }
    }
}
