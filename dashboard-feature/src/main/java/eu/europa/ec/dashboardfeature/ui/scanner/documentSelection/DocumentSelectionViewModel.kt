package eu.europa.ec.dashboardfeature.ui.scanner.documentSelection


import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import org.koin.android.annotation.KoinViewModel



data class State(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) : ViewState

sealed class Event : ViewEvent {
    object GoBack : Event()
    object GoIdentifierDocument : Event()
    object GoDrivingLicense : Event()

    object LivenessFace : Event()

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
class DocumentSelectionViewModel() : MviViewModel<Event, State, Effect>() {


    override fun setInitialState(): State {
        return State(
            isLoading = false,
        )
    }


    override fun handleEvents(event: Event) {
        when (event) {
            Event.GoBack -> setEffect { Effect.Navigation.Pop }
            Event.GoIdentifierDocument -> navigateIdentifierDocument()
            Event.GoDrivingLicense -> navigateDriverLicense()
            Event.LivenessFace -> navigateLivenessFace()

        }
    }

    private fun navigateIdentifierDocument(){
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = DashboardScreens.IdentificationDocument.screenName
            )
        }
    }

    private fun navigateDriverLicense(){
        setEffect {
          Effect.Navigation.SwitchScreen(
                screenRoute = DashboardScreens.DrivingLicense.screenName
            )
        }
    }

    private fun navigateLivenessFace(){
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = DashboardScreens.LivenessFace.screenName
            )
        }
    }

}