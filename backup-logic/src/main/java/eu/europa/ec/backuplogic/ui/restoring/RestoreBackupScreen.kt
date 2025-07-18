/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.backuplogic.ui.restoring

import android.net.Uri
import eu.europa.ec.backuplogic.interactor.BackupInteractor
import eu.europa.ec.backuplogic.model.BackupKey
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import org.koin.android.annotation.KoinViewModel


enum class RestoreStatus {
    SUCCESS, ERROR
}


sealed class State : ViewState {
    data class Default(
        val isLoading: Boolean = false,
        val words: List<String> = emptyList(),
        val backupKey: BackupKey? = null,
        val selectedFileUri: Uri? = null
    ) : State()
}


sealed class Event : ViewEvent {
    object GoBack : Event()
    object Restore : Event()
    data class FileSelected(val uri: Uri) : Event()
    data class WordsChanged(val words: List<String>) : Event()
    data class SubmitWords(val words: List<String>) : Event()
    data class NextPage(val page: Int) : Event()
}


sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(val screenRoute: String) : Navigation()
        object Pop : Navigation()
    }
    object Success : Effect()
    object Error : Effect()
    data class NavigateToPage(val page: Int) : Effect()
}


@KoinViewModel
class RestoreBackupViewModel(
    private val backupInteractor: BackupInteractor
) : MviViewModel<Event, State, Effect>() {


    override fun setInitialState(): State {
        return State.Default(
            words = List(12) { "" },
            backupKey = null
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.GoBack -> {
                setEffect { Effect.Navigation.Pop }
            }
            is Event.FileSelected -> {
                setState { (this as? State.Default)?.copy(selectedFileUri = event.uri) ?: this }
                setEffect { Effect.NavigateToPage(1) }
            }
            is Event.WordsChanged -> {
                setState { (this as? State.Default)?.copy(words = event.words) ?: this }
            }
            is Event.SubmitWords -> {
                if (backupInteractor.validateRecoveryPhrase(event.words)) {
                    setEffect { Effect.NavigateToPage(2) }
                } else {
                    setEffect { Effect.Error }
                }
            }
            is Event.Restore -> {
//                val currentState = state.value as? State.Default
//                if (currentState?.selectedFileUri != null && backupInteractor.restoreWallet(currentState.selectedFileUri.toString())) {
//                    setEffect { Effect.Success }
//                } else {
//                    setEffect { Effect.Error }
//                }
            }
            is Event.NextPage -> {
                setEffect { Effect.NavigateToPage(event.page) }
            }
        }
    }
}
