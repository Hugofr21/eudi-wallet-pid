package eu.europa.ec.dashboardfeature.ui.scanner.livenessFace

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import eu.europa.ec.dashboardfeature.interactor.LivenessInteractor
import eu.europa.ec.mrzscannerLogic.controller.Challenge
import eu.europa.ec.mrzscannerLogic.controller.FaceFeatures
import eu.europa.ec.mrzscannerLogic.controller.ChallengeState
import eu.europa.ec.mrzscannerLogic.controller.ChallengeState.Countdown
import eu.europa.ec.mrzscannerLogic.controller.LivenessResult
import eu.europa.ec.mrzscannerLogic.controller.LivenessUpdate
import eu.europa.ec.mrzscannerLogic.model.ScanType
import eu.europa.ec.mrzscannerLogic.utils.ImageUtils
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import org.koin.android.annotation.KoinViewModel

// ── State ─────────────────────────────────────────────────────────────────────

data class State(
    val isLoading: Boolean = false,
    val isInstructionAcknowledged: Boolean = false,
    val capturedBitmap: Bitmap? = null,
    val isSessionComplete: Boolean = false,
    val previewBitmap: Bitmap? = null,
    val faceFeatures: FaceFeatures? = null,
    val countdownSeconds: Int? = null,
    val errorMessage: String? = null,
    val currentChallengeMessage: String = "A preparar câmara…",
    val completedChallenges: List<String> = emptyList()
) : ViewState


sealed class Event : ViewEvent {
    object AcknowledgeInstructions : Event()
    object GoBack : Event()
    data class InitializeScanner(
        val lifecycleOwner: LifecycleOwner,
        val previewView: PreviewView,
        val scanType: ScanType
    ) : Event()
    object StopScanning : Event()
    object RetryScanning : Event()
    object ConfirmDocument : Event()
    object ResetScreenState : Event()
}


sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        object Pop : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String,
            val inclusive: Boolean = false
        ) : Navigation()
    }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@KoinViewModel
class LivenessFaceViewModel(
    private val livenessInteractor: LivenessInteractor,
) : MviViewModel<Event, State, Effect>() {

    private var capturedSelfieBitmap: Bitmap? = null
    private val passedChallengeLabels = mutableListOf<String>()

    override fun setInitialState() = State()

    override fun handleEvents(event: Event) {
        when (event) {
            Event.AcknowledgeInstructions -> setState { copy(isInstructionAcknowledged = true) }
            is Event.InitializeScanner    -> handleInitializeScanner(event)
            Event.StopScanning            -> stopScanning()
            Event.RetryScanning           -> retryScanning()
            Event.GoBack                  -> setEffect { Effect.Navigation.Pop }
            Event.ConfirmDocument         -> confirmDocument()
            Event.ResetScreenState        -> resetScreenState()
        }
    }

    // ── Scanner lifecycle ─────────────────────────────────────────────────────

    private fun handleInitializeScanner(event: Event.InitializeScanner) {
        passedChallengeLabels.clear()
        setState {
            copy(
                isLoading = true,
                completedChallenges = emptyList(),
                currentChallengeMessage = "A iniciar câmara…"
            )
        }
        viewModelScope.launch {
            startScanning(event.lifecycleOwner, event.previewView, event.scanType)
        }
    }

    private fun startScanning(owner: LifecycleOwner, preview: PreviewView, type: ScanType) {
        viewModelScope.launch {
            livenessInteractor.startLiveness(owner, preview, type)
                .collect { update -> handleLivenessUpdate(update) }
        }
    }

    // ── Liveness update dispatcher ────────────────────────────────────────────

    private fun handleLivenessUpdate(update: LivenessUpdate) {
        when (update) {
            is LivenessUpdate.ActiveFrame -> {
                // Forward both bitmap and face contour features for live overlay rendering
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
                    isLoading = false,
                    currentChallengeMessage = challengeState.challenge.toInstruction()
                )
            }

            is ChallengeState.Passed -> {
                val label = challengeState.challenge.toLabel()
                if (!passedChallengeLabels.contains(label)) passedChallengeLabels.add(label)
                setState {
                    copy(
                        isLoading = false,
                        currentChallengeMessage = "✓ $label",
                        completedChallenges = passedChallengeLabels.toList()
                    )
                }
            }

            is ChallengeState.Failed -> setState {
                copy(isLoading = false, currentChallengeMessage = challengeState.reason)
            }

            is Countdown -> setState {
                copy(
                    isLoading = false,
                    countdownSeconds = challengeState.seconds,
                    currentChallengeMessage = "A capturar em ${challengeState.seconds}…"
                )
            }
        }
    }

    private fun handleSessionResult(result: LivenessResult) {
        when (result) {
            is LivenessResult.Success -> {
                val bitmap = result.capturedJpeg
                    .takeIf { it.isNotEmpty() }
                    ?.let { ImageUtils.bytesToBitmap(it) }

                if (bitmap != null) {
                    capturedSelfieBitmap = bitmap
                    setState {
                        copy(
                            isLoading = false,
                            isSessionComplete = true,
                            capturedBitmap = bitmap,
                            errorMessage = null
                        )
                    }
                } else {
                    // FIX: JPEG empty → still mark complete so FaceIdCaptureErrorPanel shows
                    // instead of falling back to the scanning panel
                    setState {
                        copy(
                            isLoading = false,
                            isSessionComplete = true,
                            capturedBitmap = null,
                            errorMessage = null
                        )
                    }
                }
            }

            is LivenessResult.Failure -> setState {
                copy(isLoading = false, errorMessage = result.reason)
            }

            LivenessResult.InProgress -> setState { copy(isLoading = false) }
        }
    }

    // ── State resets ──────────────────────────────────────────────────────────

    private fun resetScreenState() {
        capturedSelfieBitmap = null
        passedChallengeLabels.clear()
        setState {
            copy(
                isLoading = false,
                isInstructionAcknowledged = false,
                capturedBitmap = null,
                isSessionComplete = false,
                previewBitmap = null,
                faceFeatures = null,
                countdownSeconds = null,
                errorMessage = null,
                completedChallenges = emptyList(),
                currentChallengeMessage = "A preparar câmara…"
            )
        }
    }

    private fun retryScanning() {
        capturedSelfieBitmap = null
        passedChallengeLabels.clear()
        livenessInteractor.stop()
        setState {
            copy(
                isLoading = false,
                isInstructionAcknowledged = true,
                capturedBitmap = null,
                isSessionComplete = false,
                previewBitmap = null,
                faceFeatures = null,
                countdownSeconds = null,
                errorMessage = null,
                completedChallenges = emptyList(),
                currentChallengeMessage = "A preparar câmara…"
            )
        }
    }

    private fun stopScanning() {
        livenessInteractor.stop()
    }

    private fun confirmDocument() {
        val selfie = capturedSelfieBitmap ?: return
        viewModelScope.launch(Dispatchers.IO) {
            setState { copy(isLoading = true) }
            @Suppress("UNUSED_VARIABLE")
            val selfieBytes = ImageUtils.bitmapToBytes(selfie)
            // FIX: navigation was commented out in original
            setEffect {
                Effect.Navigation.SwitchScreen(
                    screenRoute = DashboardScreens.Profile.screenRoute,
                    popUpToScreenRoute = DashboardScreens.Profile.screenRoute,
                    inclusive = true
                )
            }
        }
    }


    private fun Challenge.toLabel(): String = when (this) {
        Challenge.LOOK_LEFT  -> "Olhou para a esquerda"
        Challenge.LOOK_RIGHT -> "Olhou para a direita"
        Challenge.BLINK      -> "Piscou os olhos"
        Challenge.SMILE      -> "Sorriu"
        Challenge.OPEN_MOUTH -> "Abriu a boca"
        Challenge.NOD        -> "Acenou com a cabeça"
    }

    private fun Challenge.toInstruction(): String = when (this) {
        Challenge.LOOK_LEFT  -> "Olhe para a esquerda"
        Challenge.LOOK_RIGHT -> "Olhe para a direita"
        Challenge.BLINK      -> "Pisque os olhos"
        Challenge.SMILE      -> "Sorria"
        Challenge.OPEN_MOUTH -> "Abra a boca"
        Challenge.NOD        -> "Acene com a cabeça"
    }
}