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
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import org.koin.android.annotation.KoinViewModel

enum class RestoreStatus {
    SUCCESS, ERROR
}


sealed class State : ViewState {
    data class Init(
        val isLoading: Boolean = false,
    ) : State()
    data class SelectFile(
        val isLoading: Boolean = false,
        val selectedFileUri: Uri? = null,
        val errorMessage: String? = null
    ) : State()

    data class EnterPhrase(
        val isLoading: Boolean = false,
        val words: List<String> = List(12) { "" },
        val errorMessage: String? = null
    ) : State()

    data class RestoreWallet(
        val isLoading: Boolean = false,
        val restoreStatus: RestoreStatus? = null
    ) : State()
}
sealed class Event : ViewEvent {
    data class SelectFile(val uri: Uri) : Event()
    data class EnterPhrase(val words: List<String>) : Event()
    object RestoreWallet : Event()
    object GoBack : Event()
}
sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(val screenRoute: String) : Navigation()
        object Pop : Navigation()
    }
    object Success : Effect()
    object Error : Effect()
}


@KoinViewModel
class RestoreBackupViewModel(
    private val backupInteractor: BackupInteractor
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State {
        val existBackup = backupInteractor.existBackup()
        return State.Init()
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.SelectFile -> {
                setState {
                    State.SelectFile(
                        isLoading = true,
                        selectedFileUri = event.uri
                    )
                }
            }

            is Event.EnterPhrase -> TODO()
            Event.GoBack -> TODO()
            Event.RestoreWallet -> TODO()
        }
    }
}
