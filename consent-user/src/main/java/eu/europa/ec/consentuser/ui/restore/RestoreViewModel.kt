package eu.europa.ec.consentuser.ui.restore

import android.net.Uri
import eu.europa.ec.commonfeature.model.PinFlow
import eu.europa.ec.consentuser.ui.restore.Effect.Navigation.*
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import org.koin.android.annotation.KoinViewModel


sealed class RestorePage {
    object First : RestorePage()
    object Second : RestorePage()
    object Third : RestorePage()
}

data class State(
    val page: RestorePage = RestorePage.First,
    val selectedFileUri: Uri? = null,
    val mnemonicWords: List<String> = List(12) { "" },
    val selectedOptions: Set<String> = emptySet(),
    val tosAccepted: Boolean = false,
    val dataProtectionAccepted: Boolean = false,
    val isButtonEnabled: Boolean = true
) : ViewState

sealed class Event : ViewEvent {
    object GoBack : Event()
    object GoNext : Event()
    data class FileSelected(val uri: Uri) : Event()
    data class WordsChanged(val words: List<String>) : Event()
    object SubmitWords : Event()
    data class OptionToggled(val option: String) : Event()
    object Restore : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(val screenRoute: String) : Navigation()
        object Pop : Navigation()
    }
}

@KoinViewModel
class RestoreViewModel : MviViewModel<Event, State, Effect>() {
    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            Event.GoNext -> {
                val nextRoute = getQuickPinConfig()
                setEffect { SwitchScreen(nextRoute) }
            }
            Event.GoBack -> setEffect { Effect.Navigation.Pop }
            is Event.FileSelected -> TODO()
            is Event.OptionToggled -> TODO()
            Event.Restore -> {
                setEffect{
                    Effect.Navigation.SwitchScreen(
                        screenRoute  = DashboardScreens.Dashboard.screenRoute
                    )
                }
            }
            Event.SubmitWords -> TODO()
            is Event.WordsChanged -> TODO()
        }
    }

    private fun validateForm() {
        val enabled = viewState.value.tosAccepted && viewState.value.dataProtectionAccepted
        setState { copy(isButtonEnabled = enabled) }
    }

    private fun getQuickPinConfig(): String =
        generateComposableNavigationLink(
            screen = CommonScreens.QuickPin,
            arguments = generateComposableArguments(mapOf("pinFlow" to PinFlow.CREATE))
        )
}