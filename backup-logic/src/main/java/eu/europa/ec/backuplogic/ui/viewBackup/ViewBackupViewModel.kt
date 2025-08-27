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
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.europa.ec.backuplogic.interactor.BackupInteractor
import eu.europa.ec.backuplogic.model.BackupKey
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.storagelogic.model.BackupLog
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.BackupScreens
import eu.europa.ec.uilogic.navigation.DashboardScreens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.Named

sealed class State : ViewState {
    data class Default(
        val isLoading: Boolean = false,
        val existBackup: Boolean = false,
        val backupLog: BackupLog? = null
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

    data class ShareLogFile(val intent: Intent, val chooserTitle: String) : Effect()
}


@KoinViewModel
class ViewBackupViewModel(
    private val backupInteractor: BackupInteractor,
    private val resourceProvider: ResourceProvider,
    @InjectedParam private val encodedString: String,
) : MviViewModel<Event, State, Effect>() {

    private lateinit var originalWordList: List<String>

    override fun setInitialState(): State {
        val listwordsValue = Uri.parse(encodedString).getQueryParameter("listwords") ?: ""
        val decoded = Uri.decode(listwordsValue)
        originalWordList = decoded.split("|")

        println("List response the words: $originalWordList")

        val initialState = State.Default(
            existBackup = false,
            backupLog = null
        )

        viewModelScope.launch {
            val existBackup = backupInteractor.existBackup()
            val backupKey = if (existBackup) backupInteractor.getLastBackup() else null

            setState {
                State.Default(
                    existBackup = existBackup,
                    backupLog = backupKey
                )
            }
        }
        return initialState
    }

    override fun handleEvents(event: Event) {
        when (event) {
            Event.GoBack -> {
                setEffect { Effect.Navigation.Pop }
            }
            Event.NewBackupBtn -> {
                setState { State.Default(isLoading = true) }
                viewModelScope.launch {
                    val zipEnc = backupInteractor.exportBackup(originalWordList)
                    val zipToShare = zipEnc.filter { uri ->
                        uri.path?.endsWith(".zip.enc", ignoreCase = true) == true
                    }

                    if (zipToShare.isNotEmpty()) {
                        setEffect {
                            Effect.ShareLogFile(
                                intent = Intent().apply {
                                    action = Intent.ACTION_SEND_MULTIPLE
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM,
                                        ArrayList(zipToShare))
                                    type = "*/*"
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                },
                                chooserTitle = resourceProvider.getString(R.string.settings_intent_chooser_logs_share_title)
                            )
                        }
                    }

                    delay(600)

                    setEffect {
                        Effect.Navigation.SwitchScreen(
                            screenRoute = DashboardScreens.Dashboard.screenRoute
                        )
                    }
                }
            }
        }
    }
}
