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


import eu.europa.ec.backuplogic.interactor.BackupInteractor
import eu.europa.ec.backuplogic.model.BackupKey
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import org.koin.android.annotation.KoinViewModel

sealed class State : ViewState {
    data class Default(
        val isLoading: Boolean = false,
        val existBackup: Boolean = false,
        val backupKey: BackupKey? = null
    ) : State()
}
sealed class Event : ViewEvent {
    object GoBack : Event()
    object Share : Event()
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
class ViewBackupViewModel(
    private val backupInteractor: BackupInteractor
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State {
        val existBackup = backupInteractor.existBackup()
        return State.Default(
            existBackup = existBackup
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            Event.GoBack -> TODO()
            Event.Share -> TODO()
        }
    }
}
