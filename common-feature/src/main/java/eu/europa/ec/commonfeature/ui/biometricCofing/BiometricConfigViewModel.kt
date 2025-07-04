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

package eu.europa.ec.commonfeature.ui.biometricCofing
import android.content.Context
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAuthenticate
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAvailability
import eu.europa.ec.commonfeature.interactor.BiometricInteractor
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.CommonScreens
import org.koin.android.annotation.KoinViewModel


sealed class Event : ViewEvent {
    data object ScreenResumed : Event()
    data class NextButtonPressed(val context: Context) : Event()
    data object SkipButtonPressed : Event()
}

data class State(
    val isLoading: Boolean = false,
    val isBiometricsAvailable: Boolean = false,
    val enrolled: Boolean = false,
    val biometricsError: String? = null
) : ViewState {
    val action: ScreenNavigateAction = ScreenNavigateAction.BACKABLE
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(val screen: String) : Navigation()
    }
}

@KoinViewModel
class BiometricSetupViewModel(
    private val biometricInteractor: BiometricInteractor
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State {
        return State()
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.ScreenResumed -> {
                checkBiometricsAvailability()
            }

            is Event.NextButtonPressed -> {
                clearError()
                if (viewState.value.isBiometricsAvailable) {
                    if (viewState.value.enrolled) {
                        authenticate(event.context)
                    } else {
                        biometricInteractor.launchBiometricSystemScreen()
                    }
                }
            }

            is Event.SkipButtonPressed -> {
                biometricInteractor.storeBiometricsUsageDecision(false)
                navigateToNextScreen()
            }
        }
    }

    private fun authenticate(context: Context) {
        biometricInteractor.authenticateWithBiometrics(
            context = context,
            notifyOnAuthenticationFailure = true
        ) {
            when (it) {
                is BiometricsAuthenticate.Success -> authenticationSuccess()
                BiometricsAuthenticate.Cancelled -> clearError()
                is BiometricsAuthenticate.Failed -> showError("Failed " + it.errorMessage)
            }
        }
    }

    private fun checkBiometricsAvailability() {
        setState { copy(isLoading = true) }
        biometricInteractor.getBiometricsAvailability { availability ->
            when (availability) {
                is BiometricsAvailability.CanAuthenticate -> {
                    setState {
                        copy(
                            isLoading = false,
                            isBiometricsAvailable = true,
                            enrolled = true,
                            biometricsError = null
                        )
                    }
                }

                is BiometricsAvailability.NonEnrolled -> {
                    setState {
                        copy(
                            isLoading = false,
                            isBiometricsAvailable = true,
                            enrolled = false,
                            biometricsError = null
                        )
                    }
                }

                is BiometricsAvailability.Failure -> {
                    setState {
                        copy(
                            isLoading = false,
                            isBiometricsAvailable = false,
                            biometricsError = availability.errorMessage
                        )
                    }
                }
            }
        }
    }

    private fun clearError() {
        setState { copy(biometricsError = null) }
    }

    private fun showError(error: String) {
        setState { copy(biometricsError = error) }
    }

    private fun authenticationSuccess() {
        biometricInteractor.storeBiometricsUsageDecision(true)
        navigateToNextScreen()
    }


    private fun navigateToNextScreen() {
        setEffect {
            Effect.Navigation.SwitchScreen(CommonScreens.Biometric.screenRoute)
        }
    }
}
