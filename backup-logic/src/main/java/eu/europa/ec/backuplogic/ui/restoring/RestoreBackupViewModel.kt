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

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.viewModelScope
import eu.europa.ec.backuplogic.controller.model.RestoreStatus
import eu.europa.ec.backuplogic.interactor.BackupInteractor
import eu.europa.ec.backuplogic.model.BackupKey
import eu.europa.ec.backuplogic.ui.restoring.Effect.*
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream


sealed class State : ViewState {
    data class Default(
        val isLoading: Boolean = false,
        val words: List<String> = emptyList(),
        val backupKey: BackupKey? = null,
        val selectedFileUri: Uri? = null,
        val options: List<String> = emptyList(),
        val selectedOptions: Set<String> = emptySet()
    ) : State()
}


sealed class Event : ViewEvent {
    object GoBack : Event()
    data class FileSelected(val uri: Uri) : Event()
    data class WordsChanged(val words: List<String>) : Event()
    data class SubmitWords(val words: List<String>) : Event()
    data class NextPage(val page: Int) : Event()

    data class Restore(val chosenOptions: Set<String>) : Event()
}


sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(val screenRoute: String) : Navigation()
        object Pop : Navigation()
    }
    object Success : Effect()
    object Error : Effect()
    data class NavigateToPage(val page: Int) : Effect()

    data class Restore(val chosenOptions: Set<String>) : Event()
}


@KoinViewModel
class RestoreBackupViewModel(
    private val backupInteractor: BackupInteractor,
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
                setEffect { Navigation.Pop }
            }
            is Event.FileSelected -> {
                setState { (this as? State.Default)?.copy(selectedFileUri = event.uri) ?: this }
                setEffect { NavigateToPage(1) }
            }
            is Event.WordsChanged -> {
                setState { (this as? State.Default)?.copy(words = event.words) ?: this }
            }
            is Event.SubmitWords -> {
                val currentState = viewState.value as? State.Default
                val uri = currentState?.selectedFileUri
                if (uri == null) {
                    setEffect { Effect.Error }
                    return
                }
                viewModelScope.launch {
                    try {
                        val listOptions: List<String> = backupInteractor.restoreWallet(uri, event.words)
                        if (listOptions.isNotEmpty()) {
                            setState {
                                (this as? State.Default)?.copy(options = listOptions, selectedOptions = emptySet()) ?: this
                            }
                            setEffect { NavigateToPage(2) }
                        }
                    } catch (e: Exception) {
                        setEffect { Effect.Error }
                        e.printStackTrace()
                    } finally {

                    }
                }
            }
            is Event.Restore -> {
                val current = viewState.value as? State.Default ?: run {
                    setEffect { Effect.Error }
                    return
                }
                viewModelScope.launch {
                    val result = backupInteractor.finalizeRestore(
                        options = event.chosenOptions.toList()
                    )
                    if (result == RestoreStatus.SUCCESS) {
                        setEffect { Effect.Success }
                    } else {
                        setEffect { Effect.Error }
                    }
                }
            }
            is Event.NextPage -> {
                val current = viewState.value as? State.Default
                when (event.page) {
                    1 -> {
                        if (current?.selectedFileUri == null) {
                            setEffect { Effect.Error }
                            return
                        }
                        setEffect { NavigateToPage(1) }
                    }
                    2 -> {
                        val words = current?.words ?: emptyList()
                        val allValid = words.size == 12 && words.none { it.isBlank() }
                        if (!allValid) {
                            setEffect { Effect.Error }
                            return
                        }
                        setEffect { NavigateToPage(2) }
                    }
                    0 -> {
                        setEffect { NavigateToPage(0) }
                    }
                }
            }
            is Restore -> TODO()
        }
    }
}
