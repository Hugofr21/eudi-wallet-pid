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
import eu.europa.ec.commonfeature.config.IssuanceFlowType
import eu.europa.ec.commonfeature.config.IssuanceUiConfig
import eu.europa.ec.commonfeature.interactor.BiometricInteractor
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import org.koin.android.annotation.KoinViewModel


sealed class Event : ViewEvent {
    data object ScreenResumed : Event()
    data class NextButtonPressed(val context: Context) : Event()
    data object SkipButtonPressed : Event()
}

data class State(
    val isLoading: Boolean = false,
    val isBiometricsAvailable: Boolean = false,
    val hasFingerprint : Boolean = false,
    val appPassword : Boolean = false,
    val biometricsError: String? = null
) : ViewState {
    val action: ScreenNavigateAction = ScreenNavigateAction.BACKABLE
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(
            val screenRoute: String,
            val argument: String? = null,
            val inclusive: Boolean = false
        ) : Navigation()
    }
}

@KoinViewModel
class BiometricSetupViewModel(
    private val uiSerializer: UiSerializer,
    private val biometricInteractor: BiometricInteractor
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.ScreenResumed -> {
                checkBiometricsAvailability()
            }

            is Event.NextButtonPressed -> {
                clearError()
//                println("NextButtonPressed: pressed")
                if (viewState.value.isBiometricsAvailable) {
//                    println("NextButtonPressed: isBiometricsAvailable")
                    if (viewState.value.hasFingerprint) {
//                        println("NextButtonPressed: hasFingerprint")
                        authenticate(event.context)
                    } else {
//                        println("NextButtonPressed: launchBiometricSystemScreen")
                        biometricInteractor.launchBiometricSystemScreen()
                    }
                }
            }

            is Event.SkipButtonPressed -> {
                biometricInteractor.storeBiometricsUsageDecision(false)
                navigateToNextScreenAddDocument()
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
        println("checkBiometricsAvailability: START")
        setState {
            println("checkBiometricsAvailability: Setting isLoading = true")
            copy(isLoading = true)
        }
        biometricInteractor.getBiometricsAvailability { availability ->
            println("checkBiometricsAvailability: availability = $availability")
            when (availability) {
                is BiometricsAvailability.CanAuthenticate -> {
                    println("checkBiometricsAvailability: CanAuthenticate dete" +
                            "" +
                            "cted")
                    setState {
                        println("checkBiometricsAvailability: Setting isBiometricsAvailable = true")
                        copy(
                            isLoading = false,
                            isBiometricsAvailable = true,
                            hasFingerprint = true,
                            biometricsError = null
                        )
                    }
                }

                is BiometricsAvailability.NonEnrolled -> {
                    println("checkBiometricsAvailability: NonEnrolled detected")
                    setState {
                        println("checkBiometricsAvailability: Setting isBiometricsAvailable = true (but NonEnrolled)")
                        copy(
                            isLoading = false,
                            isBiometricsAvailable = true,
                            hasFingerprint = false,
                            biometricsError = null
                        )
                    }
                }

                is BiometricsAvailability.Failure -> {
                    println("checkBiometricsAvailability: Failure detected - error: ${availability.errorMessage}")
                    setState {
                        println("checkBiometricsAvailability: Setting isBiometricsAvailable = false")
                        copy(
                            isLoading = false,
                            isBiometricsAvailable = false,
                            hasFingerprint = false,
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
        navigateToNextScreenAddDocument()
    }


    private fun navigateToNextScreenAddDocument() {
        val addDocumentScreenRoute = generateComposableNavigationLink(
            screen = IssuanceScreens.AddDocument,
            arguments = generateComposableArguments(
                mapOf(
                    IssuanceUiConfig.serializedKeyName to uiSerializer.toBase64(
                        model = IssuanceUiConfig(
                            flowType = IssuanceFlowType.NoDocument
                        ),
                        parser = IssuanceUiConfig.Parser
                    )
                )
            )
        )
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = addDocumentScreenRoute
            )
        }
    }


}
