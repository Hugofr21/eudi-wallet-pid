package eu.europa.ec.dashboardfeature.ui.scanner.livenessFace

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import eu.europa.ec.dashboardfeature.interactor.LivenessInteractor
import eu.europa.ec.mrzscannerLogic.controller.LivenessResult
import eu.europa.ec.mrzscannerLogic.model.ScanType
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import org.koin.android.annotation.KoinViewModel
import androidx.lifecycle.viewModelScope
import eu.europa.ec.mrzscannerLogic.controller.ChallengeState
import eu.europa.ec.mrzscannerLogic.controller.FaceFeatures
import eu.europa.ec.mrzscannerLogic.controller.LivenessUpdate
import kotlinx.coroutines.launch


data class State(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentChallengeState: ChallengeState = ChallengeState.Idle,
    val features: FaceFeatures = FaceFeatures(),
    val isChallengePassed: Boolean = false
) : ViewState

sealed class Event : ViewEvent {
    object GoBack : Event()
    data class InitializeScanner(
        val lifecycleOwner: LifecycleOwner,
        val previewView: PreviewView,
        val scanType: ScanType
    ) : Event()
    object StopScanning : Event()
    object RetryScanning : Event()
    object ConfirmDocument : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        object Pop : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Profile.screenRoute,
            val inclusive: Boolean = false,
        ) : Navigation()
    }


    sealed class ShowDialog : Effect() {
        object Help : ShowDialog()
    }
}

@KoinViewModel
class LivenessFaceViewModel(
    private val livenessInteractor: LivenessInteractor,
) : MviViewModel<Event, State,Effect>() {

    override fun setInitialState() = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.InitializeScanner -> {
                startLivenessFlow(event.lifecycleOwner, event.previewView, event.scanType)
            }
            Event.StopScanning -> {
                livenessInteractor.stop()
                setState { copy(isLoading = false) }
            }
            Event.RetryScanning -> {

            }
            Event.GoBack -> setEffect { Effect.Navigation.Pop }
            else -> {}
        }
    }
    private fun startLivenessFlow(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        scanType: ScanType
    ) {
        viewModelScope.launch {
            livenessInteractor.startLiveness(lifecycleOwner, previewView, scanType)
                .collect { update ->
                    when (update) {
                        is LivenessUpdate.ActiveFrame -> {
                            setState {
                                copy(
                                    currentChallengeState = update.challengeState,
                                    features = update.features
                                )
                            }
                        }
                        is LivenessUpdate.SessionResult -> {
                            when (val result = update.result) {
                                is LivenessResult.InProgress -> setState { copy(isLoading = true, errorMessage = null) }
                                is LivenessResult.Failure -> setState { copy(isLoading = false, errorMessage = result.reason) }
                                is LivenessResult.Success -> handleLivenessSuccess(result.capturedJpeg)
                            }
                        }
                    }
                }
        }
    }

    private fun handleLivenessSuccess(jpegData: ByteArray) {
        setState { copy(isLoading = false) }
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = DashboardScreens.Profile.screenRoute,
                inclusive = true
            )
        }
    }
}



