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

package eu.europa.ec.backuplogic.ui.listWordsBackup

import eu.europa.ec.backuplogic.interactor.BackupInteractor
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.BackupScreens
import org.koin.android.annotation.KoinViewModel

data class State(
    val listWords: List<String> = emptyList(),
    val isLoading: Boolean = false
) : ViewState

sealed class Event : ViewEvent {
    object GoNext : Event()
    object GoBack : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(val screenRoute: String) : Navigation()
        object Pop : Navigation()
    }
}

@KoinViewModel
class ListWordsBackupViewModel(
    private val interactor: BackupInteractor
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State =
        State(
            listWords = interactor.initListWordsPreview()
        )

    override fun handleEvents(event: Event) {
        when (event) {
            Event.GoBack -> {
                setEffect { Effect.Navigation.Pop }
            }
            Event.GoNext -> {
                    setEffect { Effect.Navigation.SwitchScreen(
                        screenRoute = BackupScreens.BackupPhraseListCreated.screenRoute
                    )
                }
            }
        }
    }
}