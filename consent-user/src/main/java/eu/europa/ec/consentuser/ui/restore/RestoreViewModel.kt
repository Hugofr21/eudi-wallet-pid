package eu.europa.ec.consentuser.ui.restore

import android.net.Uri
import androidx.lifecycle.viewModelScope
import eu.europa.ec.commonfeature.model.PinFlow
import eu.europa.ec.consentuser.interactor.BackupRestoreInteractor
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.ConsentUserScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

sealed class RestorePage {
    object First : RestorePage()
    object Second : RestorePage()
    object Third : RestorePage()
}

data class State(
    val isLoading: Boolean = false,
    val page: RestorePage = RestorePage.First,
    val selectedFileUri: Uri? = null,
    val mnemonicWords: List<String> = List(12) { "" },
    val selectedOptions: Set<String> = emptySet(),
    val tosAccepted: Boolean = false,
    val dataProtectionAccepted: Boolean = false,
    val isButtonEnabled: Boolean = true,
    val options: List<String> = emptyList()
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

    data class ShowError(val message: String) : Effect()
}

@KoinViewModel
class RestoreViewModel(
   private  val interactor: BackupRestoreInteractor
) : MviViewModel<Event, State, Effect>() {
    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            Event.GoNext -> {
                val nextRoute = getImageCaptureLayout()
                setEffect { Effect.Navigation.SwitchScreen(nextRoute) }
            }
            Event.GoBack -> setEffect { Effect.Navigation.Pop }

            is Event.FileSelected -> {
                setState {
                    copy(
                        selectedFileUri = event.uri,
                        page = RestorePage.Second
                    )
                }
                setEffect { Effect.ShowError("File selected: ${event.uri.lastPathSegment}") }
            }

            is Event.OptionToggled -> {
                val updated = viewState.value.selectedOptions.toMutableSet().apply {
                    if (contains(event.option)) remove(event.option) else add(event.option)
                }
                setState { copy(selectedOptions = updated) }
            }

            Event.Restore -> {
                val current = viewState.value

                viewModelScope.launch {
                    setState { copy(isButtonEnabled = false) }
                    val passPhrase = current.mnemonicWords.filter { it.isNotBlank() }

                    val listOptions: List<String> =
                        interactor.restoreWallet(current.selectedFileUri, passPhrase)
                    print("ListOptions $listOptions")
                    if (listOptions.isNotEmpty()) {
                        setState {
                            copy(
                                page = RestorePage.Third,
                                options = listOptions,
                                selectedOptions = emptySet(),
                                isButtonEnabled = true
                            )
                        }
                    } else {
                        setState { copy(page = RestorePage.Third) }
                    }
                    setState { copy(isButtonEnabled = true) }
                }
            }

            Event.SubmitWords -> {
                val current = viewState.value
                if (current.mnemonicWords.any { it.isBlank() }) {
                    setEffect { Effect.ShowError("All mnemonic words must be filled.") }
                    return
                }
                val passPhrase = current.mnemonicWords.filter { it.isNotBlank() }
                perRestore(current.selectedFileUri, passPhrase)
            }

            is Event.WordsChanged -> setState { copy(mnemonicWords = event.words) }
        }
    }

    private fun validateForm(): Boolean {
        val valid = viewState.value.tosAccepted && viewState.value.dataProtectionAccepted
        setState { copy(isButtonEnabled = valid) }
        return valid
    }

    private fun getImageCaptureLayout(): String {
        return ConsentUserScreens.ImageCapture.screenRoute
    }
//    private fun getQuickPinConfig(): String =
//        generateComposableNavigationLink(
//            screen = CommonScreens.QuickPin,
//            arguments = generateComposableArguments(mapOf("pinFlow" to PinFlow.CREATE))
//        )

    private fun perRestore(selectedUri: Uri?, passPhrase: List<String>) {
        if (selectedUri == null) {
            setEffect { Effect.ShowError("No backup file selected.") }
            return
        }
        viewModelScope.launch {
            setState { copy(isButtonEnabled = false, isLoading = true) }
            val listOptions: List<String> = interactor.restoreWallet(selectedUri, passPhrase)
            if (listOptions.isNotEmpty()) {
                setState {
                    copy(
                        page = RestorePage.Third,
                        options = listOptions,
                        selectedOptions = emptySet()
                    )
                }
            } else {
                setState {
                    copy(
                        page = RestorePage.Third,
                        options = emptyList(),
                        selectedOptions = emptySet()
                    )
                }
                setEffect { Effect.ShowError("Backup restored but no options available.") }
            }

        }
    }
}