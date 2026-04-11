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

package eu.europa.ec.dashboardfeature.ui.document_sign

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import eu.europa.ec.dashboardfeature.interactor.DocumentSignInteractor
import eu.europa.ec.dashboardfeature.interactor.HomeInteractor
import eu.europa.ec.dashboardfeature.interactor.HomeInteractorGetUserNameViaMainPidDocumentPartialState
import eu.europa.ec.dashboardfeature.ui.document_sign.Effect.*
import eu.europa.ec.dashboardfeature.ui.document_sign.model.DocumentSignButtonUi
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import java.util.Locale

data class State(
    val isLoading: Boolean = false,
    val error: ContentErrorConfig? = null,
    val title: String,
    val subtitle: String,
    val buttonUi: DocumentSignButtonUi,
    val errorDoc: ContentErrorConfig? = null,
    val welcomeUserMessage: String? = null
) : ViewState

sealed class Event : ViewEvent {
    data object Pop : Event()
    data object OnSelectDocument : Event()
    // Evento atualizado para apenas repassar a URI original para o ViewModel processar
    data class DocumentSelected(val context: Context, val uri: Uri) : Event()
    data class ErrorProcessingDocument(val reason: String) : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data object Pop : Navigation()
    }

    data class OpenDocumentSelection(val selection: List<String>) : Effect()
}
@KoinViewModel
class DocumentSignViewModel(
    private val documentSignInteractor: DocumentSignInteractor,
    private val resourceProvider: ResourceProvider,
    private val homeInteractor: HomeInteractor
) : MviViewModel<Event, State, Effect>() {
    private var cachedUserName: String = "user"

    init {
        fetchUserName()
    }

    override fun setInitialState(): State = State(
        title = resourceProvider.getString(R.string.document_sign_title),
        subtitle = resourceProvider.getString(R.string.document_sign_subtitle),
        buttonUi = documentSignInteractor.getItemUi(),
    )

    private fun fetchUserName() {
        viewModelScope.launch {
            homeInteractor.getUserNameViaMainPidDocument().collect { response ->
                if (response is HomeInteractorGetUserNameViaMainPidDocumentPartialState.Success) {
                    cachedUserName = response.userFirstName
                    setState {
                        copy(
                            welcomeUserMessage = if (response.userFirstName.isNotBlank()) {
                                resourceProvider.getString(R.string.home_screen_welcome_user_message, response.userFirstName)
                            } else resourceProvider.getString(R.string.home_screen_welcome)
                        )
                    }
                }
            }
        }
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.OnSelectDocument -> {
                setEffect { Effect.OpenDocumentSelection(listOf("application/pdf")) }
            }
            is Event.Pop -> setEffect { Effect.Navigation.Pop }
            is Event.DocumentSelected -> processSelectedDocument(event.context, event.uri)
            is Event.ErrorProcessingDocument -> {
                setState {
                    copy(
                        errorDoc = ContentErrorConfig(
                            errorTitle = resourceProvider.getString(R.string.error_title),
                            errorSubTitle = event.reason,
                            onCancel = { setState { copy(errorDoc = null) } }
                        )
                    )
                }
            }
        }
    }

    private fun processSelectedDocument(context: Context, originalUri: Uri) {
        setState { copy(isLoading = true) }
        viewModelScope.launch {
            val processedUri = documentSignInteractor.processAndSanitizeDocument(
                context = context,
                originalUri = originalUri,
                userName = cachedUserName
            )

            setState { copy(isLoading = false) }

            if (processedUri != null) {
                documentSignInteractor.launchRqesSdk(context, processedUri)
            } else {
                handleEvents(Event.ErrorProcessingDocument("Failed to process and sanitize the PDF document."))
            }
        }
    }
}