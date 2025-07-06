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

package eu.europa.ec.dashboardfeature.ui.profile

import eu.europa.ec.corelogic.model.FormatType
import eu.europa.ec.dashboardfeature.interactor.PersonIdentificationDataInteractor
import eu.europa.ec.dashboardfeature.model.ClaimsUI
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import org.koin.android.annotation.KoinViewModel

data class State(
    val documentsUi: List<String> = emptyList(),
    val firstName: String,
    val lastName: String,
    val isLoading: Boolean = false,
    val imageBase64: String? = null,
    val claimsUi: List<ClaimsUI> = emptyList(),
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
    data object GetDocuments : Event()
    data object BackPressed : Event()
    data object GoBack : Event()

}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data object Pop : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Profile.screenRoute,
            val inclusive: Boolean = false,
        ) : Navigation()
    }

    data class DocumentsFetched(val deferredDocs: Map<DocumentId, FormatType>) : Effect()

    data object ShowBottomSheet : Effect()
    data object CloseBottomSheet : Effect()

}


@KoinViewModel
class ProfileViewModel(
    private val personIdentificationDataInteractor: PersonIdentificationDataInteractor,
) : MviViewModel<Event, State, Effect>(

) {
    override fun setInitialState(): State {
       val (firstName, lastName) = personIdentificationDataInteractor.getUserFristandLastName()
        personIdentificationDataInteractor.printAllDocumentDetails()
        personIdentificationDataInteractor.getUserWithPortrait()
        return  State(

            documentsUi = personIdentificationDataInteractor.getPidDocuments(),
            firstName = firstName,
            lastName = lastName,
        )
    }

    override fun handleEvents(event: Event) {
       when (event) {
           Event.BackPressed -> TODO()
           Event.GetDocuments -> TODO()
           Event.GoBack -> {
               setEffect { Effect.Navigation.Pop }
           }
           Event.Init -> TODO()
       }
    }
}
