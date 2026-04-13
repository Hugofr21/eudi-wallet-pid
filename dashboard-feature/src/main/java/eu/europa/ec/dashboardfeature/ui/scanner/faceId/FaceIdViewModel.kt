package eu.europa.ec.dashboardfeature.ui.scanner.faceId

import android.annotation.SuppressLint
import android.graphics.Bitmap
import org.koin.core.annotation.InjectedParam
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.nimbusds.jose.shaded.gson.Gson
import eu.europa.ec.dashboardfeature.interactor.ScannerInteractor
import eu.europa.ec.mrzscannerLogic.controller.MrzScanState
import eu.europa.ec.mrzscannerLogic.model.MrzDocument
import eu.europa.ec.mrzscannerLogic.model.ScanType
import eu.europa.ec.mrzscannerLogic.utils.ImageUtils
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel


data class State(
    val isLoading: Boolean = false,
    val scanState: MrzScanState = MrzScanState.Idle,
    val scannedDocument: MrzDocument? = null,
    val errorMessage: String? = null,
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
class FaceIdScreenViewModel(
    private val scannerInteractor: ScannerInteractor,
    @InjectedParam private val documentId: String
) : MviViewModel<Event, State, Effect>() {
    private lateinit var document: MrzDocument
    private var lifecycleOwner: LifecycleOwner? = null

    @SuppressLint("StaticFieldLeak")
    private var previewView: PreviewView? = null

    private var capturedSelfieBitmap: Bitmap? = null
    private var scanningJob: Job? = null

    override fun setInitialState() = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.InitializeScanner -> handleInitializeScanner(event)
            Event.StopScanning -> stopScanning()
            Event.RetryScanning -> retryScanning()
            Event.GoBack -> setEffect { Effect.Navigation.Pop }
            Event.ConfirmDocument -> confirmDocument()
            else -> {}
        }
    }

    private fun loadDocument() {
        val document = Gson().fromJson(documentId, MrzDocument::class.java)
        if (document != null) {
            this.document = document
        } else {
            setState { copy(errorMessage = "Document not found") }
        }
    }

    private fun handleInitializeScanner(event: Event.InitializeScanner) {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            try {
                lifecycleOwner = event.lifecycleOwner
                previewView = event.previewView
                startScanning()
            } catch (e: Exception) {
                setState { copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private fun startScanning() {
        val owner = lifecycleOwner ?: return
        val preview = previewView ?: return
        scannerInteractor.resetScanner()
        scanningJob?.cancel()

        scanningJob = viewModelScope.launch {
            scannerInteractor.startScanning(owner, preview, ScanType.Face)
                .collect { scanState ->
                    when (scanState) {
                        is MrzScanState.Scanning -> {
                            setState { copy(scanState = scanState, isLoading = false, errorMessage = null) }
                        }
                        is MrzScanState.Success -> {
                            val faceBitmap = scanState.capturedImage
                            if (faceBitmap != null) {
                                handleFaceCaptured(faceBitmap)
                            }
                        }
                        is MrzScanState.Error -> {
                            setState { copy(scanState = scanState, isLoading = false) }
                        }
                        else -> {
                            setState { copy(scanState = scanState, isLoading = false) }
                        }
                    }
                }
        }
    }

    private fun handleFaceCaptured(bitmap: Bitmap) {
        stopScanning()
        capturedSelfieBitmap = bitmap

        setState {
            copy(
                isLoading = false,
                scanState = MrzScanState.Success(capturedImage = bitmap)
            )
        }
    }

    private fun retryScanning() {
        capturedSelfieBitmap = null
        scannerInteractor.resetScanner()

        setState {
            copy(
                scanState = MrzScanState.Scanning,
                isLoading = false,
                errorMessage = null
            )
        }
        startScanning()
    }

    private fun stopScanning() {
        scanningJob?.cancel()
        scanningJob = null
        scannerInteractor.stopScanning()
    }

    private fun confirmDocument() {
        val selfie = capturedSelfieBitmap ?: return

        viewModelScope.launch(Dispatchers.IO) {
            setState { copy(isLoading = true) }

            // CBOR logic here...
            val selfieBytes = ImageUtils.bitmapToBytes(selfie)

            setEffect {
                Effect.Navigation.SwitchScreen(
                    screenRoute = DashboardScreens.Profile.screenRoute,
                    inclusive = true
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
        lifecycleOwner = null
        previewView = null
    }
}