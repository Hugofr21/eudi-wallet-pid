package eu.europa.ec.dashboardfeature.ui.scanner.identificationDocument

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.nimbusds.jose.shaded.gson.Gson
import eu.europa.ec.dashboardfeature.interactor.ScannerInteractor
import eu.europa.ec.mrzscannerLogic.controller.MrzScanState
import eu.europa.ec.mrzscannerLogic.model.AntiSpoofingCheck
import eu.europa.ec.mrzscannerLogic.model.MrzDocument
import eu.europa.ec.mrzscannerLogic.model.ScanType
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel


enum class CameraAvailability {
    AVAILABLE, NO_PERMISSION, DISABLED, UNKNOWN
}


data class State(
    val isLoading: Boolean = false,
    val isCameraAvailability: CameraAvailability? = null,
    val scanState: MrzScanState = MrzScanState.Idle,
    val scannedDocument: MrzDocument? = null,
    val errorMessage: String? = null,
    val isPermissionChecked: Boolean = false,
    val isFlashOn: Boolean = false,
    val isScanFrozen: Boolean = false,
) : ViewState
sealed class Event : ViewEvent {
    object GoBack : Event()
    data class InitializeScanner(
        val lifecycleOwner: LifecycleOwner,
        val previewView: PreviewView,
        val scanType: ScanType
    ) : Event()

    object StartScanning : Event()
    object StopScanning : Event()
    object RetryScanning : Event()

    object ToggleFlash : Event()

    data class OnPermissionStateChanged(val availability: CameraAvailability) : Event()

    data class OnScanResult(val document: MrzDocument) : Event()
    data class OnScanError(
        val failedChecks: List<AntiSpoofingCheck>? = null,
        val message: String
    ) : Event()


    object TriggerManualCapture : Event()
    object ShowHelp : Event()
    object ConfirmDocument : Event()
    object OpenAppSettings : Event()
    object CheckPermissions : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        object Pop : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Profile.screenRoute,
            val inclusive: Boolean = false,
        ) : Navigation()

        data object OnAppSettings : Navigation()
        data object OnSystemSettings : Navigation()

    }


    sealed class ShowDialog : Effect() {
        object Help : ShowDialog()
    }
}

@KoinViewModel
class IdentificationDocumentViewModel(
    private val uiSerializer: UiSerializer,
    private val scannerInteractor: ScannerInteractor,
) : MviViewModel<Event, State, Effect>() {

    private var lifecycleOwner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var scanningJob: kotlinx.coroutines.Job? = null

    override fun setInitialState(): State {
        return State(
            isLoading = false,
            isCameraAvailability = null,
            scanState = MrzScanState.Idle,
            isPermissionChecked = false,
        )
    }

    init {
        checkInitialPermissions()
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.InitializeScanner -> handleInitializeScanner(event)
            Event.StartScanning -> startScanning()
            Event.StopScanning -> stopScanning()
            Event.RetryScanning -> retryScanning()
            Event.GoBack -> setEffect { Effect.Navigation.Pop }

            is Event.OnPermissionStateChanged -> handlePermissionChanged(event)
            Event.CheckPermissions -> checkInitialPermissions()

            is Event.OnScanResult -> handleScanResult(event)
            is Event.OnScanError -> handleScanError(event)

            Event.TriggerManualCapture -> triggerManualCapture()
            Event.ShowHelp -> showHelpDialog()
            Event.ConfirmDocument -> confirmDocumentFromState()
            Event.OpenAppSettings -> setEffect { Effect.Navigation.OnAppSettings }
            is Event.ToggleFlash -> {
                val newState = !viewState.value.isFlashOn
                setState { copy(isFlashOn = newState) }
                scannerInteractor.enableFlash(newState)
            }
        }
    }

    // ============================================
    // Verificação de Permissões
    // ============================================

    private fun checkInitialPermissions() {
        viewModelScope.launch {
            // Marcar que já verificou
            setState { copy(isPermissionChecked = true) }

            when {
                !scannerInteractor.hasCameraPermission() -> {
                    setState { copy(isCameraAvailability = CameraAvailability.NO_PERMISSION) }
                }
                !scannerInteractor.isCameraAvailable() -> {
                    setState { copy(isCameraAvailability = CameraAvailability.DISABLED) }
                }
                else -> {
                    // MUDANÇA: Se tem permissão, marcar como AVAILABLE imediatamente
                    setState { copy(isCameraAvailability = CameraAvailability.AVAILABLE) }
                }
            }
        }
    }

    // ============================================
    // Inicialização do Scanner
    // ============================================

    private fun handleInitializeScanner(event: Event.InitializeScanner) {
        viewModelScope.launch {
            setState { copy(isLoading = true) }

            try {
                // Armazenar referências
                lifecycleOwner = event.lifecycleOwner
                previewView = event.previewView

                // Verificar disponibilidade
                when {
                    !scannerInteractor.hasCameraPermission() -> {
                        setState {
                            copy(
                                isCameraAvailability = CameraAvailability.NO_PERMISSION,
                                isLoading = false
                            )
                        }
                    }
                    !scannerInteractor.isCameraAvailable() -> {
                        setState {
                            copy(
                                isCameraAvailability = CameraAvailability.DISABLED,
                                isLoading = false
                            )
                        }
                    }
                    else -> {
                        setState {
                            copy(
                                isCameraAvailability = CameraAvailability.AVAILABLE,
                                isLoading = false
                            )
                        }
                        // IMPORTANTE: Iniciar scanning automático
                        startScanning()
                    }
                }
            } catch (e: Exception) {
                setState {
                    copy(
                        isLoading = false,
                        errorMessage = "Erro ao inicializar câmera: ${e.message}",
                        scanState = MrzScanState.Error("Falha na inicialização")
                    )
                }
            }
        }
    }

    private fun handlePermissionChanged(event: Event.OnPermissionStateChanged) {
        setState {
            copy(
                isCameraAvailability = event.availability,
                isPermissionChecked = true
            )
        }

        // Se permissão foi concedida e ainda não inicializou, esperar pela PreviewView
        // O InitializeScanner vai ser chamado pelo LaunchedEffect da UI
    }

    // ============================================
    // Controle de Scanning CONTÍNUO
    // ============================================

    private fun startScanning() {
        val owner = lifecycleOwner ?: run {
            setState { copy(scanState = MrzScanState.Error("Câmera não inicializada")) }
            return
        }
        val preview = previewView ?: run {
            setState { copy(scanState = MrzScanState.Error("PreviewView não disponível")) }
            return
        }

        scanningJob?.cancel()

        scanningJob = viewModelScope.launch {
            try {
                scannerInteractor.startScanning(owner, preview, ScanType.Document)
                    .collect { scanState ->

                        // ★ FIX ALERTA: se o scan está "frozen" (erro ou sucesso mostrado),
                        // ignorar todos os frames seguintes — o scanner continua a correr
                        // em background mas a UI não é afetada
                        if (viewState.value.isScanFrozen) return@collect

                        when (scanState) {
                            is MrzScanState.Success -> {
                                val doc = scanState.document
                                if (doc != null) {
                                    // ★ FIX CARD: congelar + guardar documento
                                    // NÃO chamar confirmDocument() aqui — o utilizador
                                    // clica "Confirm" no card para navegar
                                    setState {
                                        copy(
                                            scanState = scanState,
                                            scannedDocument = doc,
                                            errorMessage = null,
                                            isScanFrozen = true   // ← para o stream visual
                                        )
                                    }
                                    // ★ NÃO chamar confirmDocument() aqui
                                } else {
                                    setState {
                                        copy(
                                            scanState = MrzScanState.Error("Documento não reconhecido"),
                                            errorMessage = "Documento não reconhecido",
                                            isScanFrozen = true
                                        )
                                    }
                                }
                            }

                            is MrzScanState.SecurityCheckFailed -> {
                                // ★ FIX ALERTA: congelar por 3 segundos depois descongelar
                                // para o utilizador ver a mensagem antes do próximo frame
                                setState {
                                    copy(
                                        scanState = scanState,
                                        errorMessage = scanState.reason,
                                        isScanFrozen = true   // ← bloqueia frames seguintes
                                    )
                                }
                                // Descongelar após 3s para retomar scanning automático
                                viewModelScope.launch {
                                    kotlinx.coroutines.delay(3_000)
                                    setState { copy(isScanFrozen = false, errorMessage = null) }
                                }
                            }

                            is MrzScanState.Error -> {
                                setState {
                                    copy(
                                        scanState = scanState,
                                        errorMessage = scanState.message,
                                        isScanFrozen = true
                                    )
                                }
                                viewModelScope.launch {
                                    kotlinx.coroutines.delay(3_000)
                                    setState { copy(isScanFrozen = false, errorMessage = null) }
                                }
                            }

                            else -> setState { copy(scanState = scanState) }
                        }
                    }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                setState {
                    copy(
                        scanState = MrzScanState.Error("Erro ao iniciar scanning"),
                        errorMessage = e.message
                    )
                }
            }
        }
    }


    private fun retryScanning() {
        scanningJob?.cancel()
        scanningJob = null
        setState {
            copy(
                scannedDocument = null,
                scanState = MrzScanState.Scanning,
                errorMessage = null,
                isScanFrozen = false
            )
        }
        startScanning()
    }


    private fun stopScanning() {
        scanningJob?.cancel()
        scanningJob = null
        scannerInteractor.stopScanning()
        setState { copy(scanState = MrzScanState.Idle, scannedDocument = null) }
    }

    override fun onCleared() {
        super.onCleared()
        scanningJob?.cancel()
        scannerInteractor.stopScanning()
        lifecycleOwner = null
        previewView = null
    }

    // ============================================
    // Tratamento de Resultados
    // ============================================

    private fun handleScanResult(event: Event.OnScanResult) {
        setState {
            copy(
                scannedDocument = event.document,
                scanState = MrzScanState.Success(event.document),
                errorMessage = null
            )
        }
    }

    private fun handleScanError(event: Event.OnScanError) {
        setState {
            copy(
                errorMessage = event.message,
                scanState = MrzScanState.Error(event.message)
            )
        }
    }

    private fun triggerManualCapture() {
        viewModelScope.launch {
            setState { copy(scanState = MrzScanState.Processing()) }
        }
    }

    private fun showHelpDialog() {
        setEffect { Effect.ShowDialog.Help }
    }

    private fun confirmDocumentFromState() {
        val document = viewState.value.scannedDocument ?: return
        confirmDocument(document)
    }

    private fun confirmDocument(document: MrzDocument) {
        val jsonDoc = Gson().toJson(document)
        stopScanning()
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = DashboardScreens.FaceIdDetails,
                    arguments = generateComposableArguments(
                        mapOf("documentId" to jsonDoc)
                    )
                )
            )
        }
    }

}