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

package eu.europa.ec.backuplogic.ui.viewBackup


import android.app.Application
import android.content.Context
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.europa.ec.backuplogic.interactor.BackupInteractor
import eu.europa.ec.backuplogic.model.BackupKey
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.Named

sealed class State : ViewState {
    data class Default(
        val isLoading: Boolean = false,
        val existBackup: Boolean = false,
        val backupKey: BackupKey? = null
    ) : State()
}
sealed class Event : ViewEvent {
    object GoBack : Event()
    object NewBackupBtn : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(val screenRoute: String) : Navigation()
        object Pop : Navigation()
    }
    object Success : Effect()
    object Error : Effect()

    data class ShowExportedFile(val fileUri: String) : Effect()
}


@KoinViewModel
class ViewBackupViewModel(
    private val backupInteractor: BackupInteractor,
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State {
//        val existBackup = backupInteractor.existBackup()
//        val backupKey = if (existBackup) backupInteractor.getBackupKey() else null

        return State.Default(
            existBackup = false,
            backupKey = null
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            Event.GoBack -> {
                setEffect { Effect.Navigation.Pop }
            }
            Event.NewBackupBtn -> {
                setState { State.Default(isLoading = true) }
                viewModelScope.launch {
                    try {
                        val file = backupInteractor.exportBackup()
                    } catch (e: Exception) {
                        setEffect { Effect.Error }
                    } finally {
                        setState { State.Default(isLoading = false) }
                    }
                }
            }
        }
    }
}
