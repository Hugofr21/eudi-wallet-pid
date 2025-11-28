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

package eu.europa.ec.consentuser.ui.verification

import eu.europa.ec.commonfeature.model.PinFlow
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import org.koin.android.annotation.KoinViewModel

data class State(
    val tosAccepted: Boolean = false,
    val dataProtectionAccepted: Boolean = false,
    val isButtonEnabled: Boolean = true // active
) : ViewState

sealed class Event : ViewEvent {
    object GoNext : Event()
    object GoBack : Event()
    object TosSelected : Event()
    object DataProtectionSelected : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(val screenRoute: String) : Navigation()
        object Pop : Navigation()
    }
    }

@KoinViewModel
class VerificationViewModel : MviViewModel<Event, State, Effect>() {
    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            Event.GoNext -> {
                val nextRoute = getQuickPinConfig()
                setEffect { Effect.Navigation.SwitchScreen(nextRoute) }
            }
            Event.GoBack -> setEffect { Effect.Navigation.Pop }
            Event.TosSelected -> {
                setState { copy(tosAccepted = !tosAccepted) }
                validateForm()
            }
            Event.DataProtectionSelected -> {
                setState { copy(dataProtectionAccepted = !dataProtectionAccepted) }
                validateForm()
            }
        }
    }

    private fun validateForm() {
        val enabled = viewState.value.tosAccepted && viewState.value.dataProtectionAccepted
        setState { copy(isButtonEnabled = enabled) }
    }

    private fun getQuickPinConfig(): String =
        generateComposableNavigationLink(
            screen = CommonScreens.QuickPin,
            arguments = generateComposableArguments(mapOf("pinFlow" to PinFlow.CREATE))
        )
}
