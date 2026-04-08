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

package eu.europa.ec.consentuser.ui.imageCapture

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import eu.europa.ec.commonfeature.model.PinFlow
import eu.europa.ec.consentuser.interactor.ImageCaptureInteractor
import eu.europa.ec.consentuser.ui.imageCapture.utils.Challenge.toInstruction
import eu.europa.ec.consentuser.ui.imageCapture.utils.Challenge.toLabel
import eu.europa.ec.mrzscannerLogic.controller.Challenge
import eu.europa.ec.mrzscannerLogic.controller.ChallengeState
import eu.europa.ec.mrzscannerLogic.controller.FaceFeatures
import eu.europa.ec.mrzscannerLogic.controller.LivenessResult
import eu.europa.ec.mrzscannerLogic.controller.LivenessUpdate
import eu.europa.ec.mrzscannerLogic.model.ScanType
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.ConsentUserScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class State(
    val isLoading: Boolean = false,
    val isInstructionAcknowledged: Boolean = false,
    val isSessionComplete: Boolean = false,
    val capturedBitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val faceFeatures: FaceFeatures? = null,
    val countdownSeconds: Int? = null,
    val currentChallengeMessage: String = "A preparar câmara…",
    val completedChallenges: List<String> = emptyList(),
    val totalChallenges: Int = 3,
    val errorMessage: String? = null,
    val isButtonEnabled: Boolean = false
) : ViewState

sealed class Event : ViewEvent {
    object AcknowledgeInstructions : Event()

    object GoNext : Event()
    object GoBack : Event()
    object RetryCapture : Event()
    object StopCapture : Event()
    data class InitializeCapture(
        val lifecycleOwner: LifecycleOwner,
        val previewView: PreviewView,
        val scanType: ScanType
    ) : Event()
}
sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(val screenRoute: String) : Navigation()
        data object Pop : Navigation()
    }
}
@KoinViewModel
class ImageCaptureViewModel(
    private val interactor: ImageCaptureInteractor,
) : MviViewModel<Event, State, Effect>() {

    private var capturedSelfieBitmap: Bitmap? = null
    private val passedChallengeLabels = mutableListOf<String>()

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            Event.AcknowledgeInstructions -> setState { copy(isInstructionAcknowledged = true) }

            is Event.InitializeCapture -> {
                passedChallengeLabels.clear()
                setState {
                    copy(
                        isLoading               = true,
                        completedChallenges     = emptyList(),
                        currentChallengeMessage = "A iniciar câmara…"
                    )
                }
                viewModelScope.launch {
                    startLiveness(event.lifecycleOwner, event.previewView, event.scanType)
                }
            }

            Event.StopCapture -> interactor.stop()

            Event.RetryCapture -> {
                capturedSelfieBitmap = null
                passedChallengeLabels.clear()
                interactor.clearSavedSelfie()
                interactor.stop()
                setState {
                    copy(
                        isLoading               = false,
                        isInstructionAcknowledged = true,
                        capturedBitmap          = null,
                        isSessionComplete       = false,
                        previewBitmap           = null,
                        faceFeatures            = null,
                        countdownSeconds        = null,
                        errorMessage            = null,
                        completedChallenges     = emptyList(),
                        currentChallengeMessage = "A preparar câmara…"
                    )
                }
            }

            Event.GoNext -> navigateToCreatePin()

            Event.GoBack -> setEffect { Effect.Navigation.Pop }

            else -> {}
        }
    }


    private fun startLiveness(
        owner: LifecycleOwner,
        preview: PreviewView,
        type: ScanType
    ) {
        viewModelScope.launch {
            interactor.startLiveness(owner, preview, type)
                .collect { update -> handleLivenessUpdate(update) }
        }
    }

    private fun handleLivenessUpdate(update: LivenessUpdate) {
        when (update) {
            is LivenessUpdate.ActiveFrame -> {
                setState {
                    copy(
                        previewBitmap = update.bitmap ?: previewBitmap,
                        faceFeatures  = update.features.takeIf { it.faceOval.isNotEmpty() }
                    )
                }
                handleChallengeState(update.challengeState)
            }
            is LivenessUpdate.SessionResult -> handleSessionResult(update.result)
        }
    }

    private fun handleChallengeState(challengeState: ChallengeState) {
        when (challengeState) {
            ChallengeState.Idle -> setState {
                copy(isLoading = false, currentChallengeMessage = "A processar…")
            }
            is ChallengeState.Pending -> setState {
                copy(
                    isLoading               = false,
                    currentChallengeMessage = challengeState.challenge.toInstruction()
                )
            }
            is ChallengeState.Passed -> {
                val label = challengeState.challenge.toLabel()
                if (!passedChallengeLabels.contains(label)) passedChallengeLabels.add(label)
                setState {
                    copy(
                        isLoading               = false,
                        currentChallengeMessage = "✓ $label",
                        completedChallenges     = passedChallengeLabels.toList()
                    )
                }
            }
            is ChallengeState.Failed -> setState {
                copy(isLoading = false, currentChallengeMessage = challengeState.reason)
            }
            is ChallengeState.Countdown -> setState {
                copy(
                    isLoading               = false,
                    countdownSeconds        = challengeState.seconds,
                    currentChallengeMessage = "To be captured in ${challengeState.seconds}…"
                )
            }
        }
    }

    private fun handleSessionResult(result: LivenessResult) {
        when (result) {
            is LivenessResult.Success -> {
                val jpeg   = result.capturedJpeg
                val saved  = if (jpeg.isNotEmpty()) interactor.saveCapturedSelfie(jpeg) else null
                val bitmap = jpeg.takeIf { it.isNotEmpty() }
                    ?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }

                capturedSelfieBitmap = bitmap

                setState {
                    copy(
                        isLoading         = false,
                        isSessionComplete = true,
                        capturedBitmap    = bitmap,
                        errorMessage      = if (saved == null && jpeg.isNotEmpty())
                            "The image could not be saved." else null
                    )
                }
            }
            is LivenessResult.Failure -> setState {
                copy(isLoading = false, errorMessage = result.reason)
            }
            LivenessResult.InProgress -> setState { copy(isLoading = false) }
        }
    }



    private fun navigateToCreatePin() {
        setState { copy(isLoading = true) }
        val route = generateComposableNavigationLink(
            screen    = CommonScreens.QuickPin,
            arguments = generateComposableArguments(mapOf("pinFlow" to PinFlow.CREATE))
        )
        setEffect { Effect.Navigation.SwitchScreen(route) }
    }
}