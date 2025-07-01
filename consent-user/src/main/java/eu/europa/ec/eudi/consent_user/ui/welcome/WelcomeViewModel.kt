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

package eu.europa.ec.eudi.consent_user.ui.welcome


import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.ConsentUserScreens
import org.koin.android.annotation.KoinViewModel


data class State(
    val pages: List<SinglePageConfig> = emptyList()
) : ViewState

sealed class Event : ViewEvent {
    data object GoNext : Event()
    data object Pop : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(val screenRoute: String) : Navigation()
        data object Finish : Navigation()
    }
}

@KoinViewModel
class WelcomeViewModel : MviViewModel<Event, State, Effect>() {
    override fun setInitialState(): State = State(
        pages = listOf(
            SinglePageConfig(
                title = R.string.user_consent_step_1_title,
                description = R.string.user_consent_step_1_description,
                icon = AppIcons.PresentDocumentInPerson
            ),
            SinglePageConfig(
                title = R.string.user_consent_step_2_title,
                description = R.string.user_consent_step_2_description,
                icon = AppIcons.WalletActivated
            ),
            SinglePageConfig(
                title = R.string.user_consent_step_3_title,
                description = R.string.user_consent_step_3_description,
                icon = AppIcons.WalletSecured
            )
        )
    )

    override fun handleEvents(event: Event) {
        when (event) {
            Event.GoNext -> {
                setEffect { Effect.Navigation.SwitchScreen(ConsentUserScreens.Consent.screenRoute) }
            }

            Event.Pop -> {
                setEffect { Effect.Navigation.Finish }
            }
        }
    }

    fun getNextButtonResId(currentPage: Int, pageCount: Int): Int {
        if (currentPage == pageCount - 1) {
            return R.string.welcome_screen_next
        }
        return R.string.welcome_screen_skip
    }

}