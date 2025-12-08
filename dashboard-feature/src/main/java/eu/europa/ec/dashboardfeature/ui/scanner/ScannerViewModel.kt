package eu.europa.ec.dashboardfeature.ui.scanner

import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import eu.europa.ec.dashboardfeature.interactor.ScannerInteractor
import eu.europa.ec.mrzscannerLogic.controller.MrzScanState
import eu.europa.ec.mrzscannerLogic.model.MrzDocument
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
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
    val errorMessage: String? = null
) : ViewState

sealed class Event : ViewEvent {
    object GoBack : Event()

    // Inicialização
    data class InitializeScanner(
        val lifecycleOwner: LifecycleOwner,
        val previewView: PreviewView
    ) : Event()

    // Controle de scanning
    object StartScanning : Event()
    object StopScanning : Event()
    object RetryScanning : Event()

    // Permissões
    data class OnPermissionStateChanged(val availability: CameraAvailability) : Event()

    // Resultados
    data class OnScanResult(val document: MrzDocument) : Event()
    data class OnScanError(val message: String) : Event()

    // Ações do usuário
    object TriggerManualCapture : Event()
    data class OnImageSelected(val uri: Uri) : Event()
    object ShowHelp : Event()
    object ConfirmDocument : Event()
    object OpenAppSettings : Event()
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
class ScannerViewModel(
    private val uiSerializer: UiSerializer,
    private val scannerInteractor: ScannerInteractor,
) : MviViewModel<Event, State, Effect>() {


    override fun setInitialState(): State {
        return State(
            isLoading = false,
            isCameraAvailability = null,
            scanState = MrzScanState.Idle
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.InitializeScanner -> handleInitializeScanner(event)
            Event.StartScanning -> startScanning()
            Event.StopScanning -> stopScanning()
            Event.RetryScanning -> retryScanning()
            Event.GoBack -> setEffect { Effect.Navigation.Pop }

            is Event.OnPermissionStateChanged -> handlePermissionChanged(event)
            is Event.OnScanResult -> handleScanResult(event)
            is Event.OnScanError -> handleScanError(event)

            Event.TriggerManualCapture -> triggerManualCapture()
            is Event.OnImageSelected -> handleImageSelected(event)
            Event.ShowHelp -> showHelpDialog()
            Event.ConfirmDocument -> confirmDocument()
            Event.OpenAppSettings -> setEffect {
                Effect.Navigation.OnAppSettings
            }
        }

    }

        private fun checkInitialPermissions() {
            viewModelScope.launch {
                when {
                    !scannerInteractor.hasCameraPermission() -> {
                        setState { copy(isCameraAvailability = CameraAvailability.NO_PERMISSION) }
                    }
                    !scannerInteractor.isCameraAvailable() -> {
                        setState { copy(isCameraAvailability = CameraAvailability.DISABLED) }
                    }
                    else -> {
                        setState { copy(isCameraAvailability = CameraAvailability.UNKNOWN) }
                    }
                }
            }
        }

        private fun handleInitializeScanner(event: Event.InitializeScanner) {
            viewModelScope.launch {
                setState { copy(isLoading = true) }

                try {
                    // Inicializa o controller com os componentes da UI
//                    scannerInteractor.initializeController(
//                        lifecycleOwner = event.lifecycleOwner,
//                        previewView = event.previewView
//                    )

                    // Verifica disponibilidade final
                    when {
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
                            // Inicia o scanning automaticamente
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
            setState { copy(isCameraAvailability = event.availability) }

            if (event.availability == CameraAvailability.AVAILABLE) {
                // Permissão concedida, mas ainda precisa inicializar o controller
                // Será feito quando a PreviewView estiver pronta
            }
        }

        // ============================================
        // Controle de Scanning
        // ============================================

        private fun startScanning() {
            viewModelScope.launch {
                try {
                    scannerInteractor.startScanning().collect { scanState ->
                        setState { copy(scanState = scanState) }

                        when (scanState) {
                            is MrzScanState.Success -> {
                                setEvent(Event.OnScanResult(scanState.document))
                            }
                            is MrzScanState.Error -> {
                                setEvent(Event.OnScanError(scanState.message))
                            }
                            else -> { /* Atualizar UI state apenas */ }
                        }
                    }
                } catch (e: Exception) {
                    setState {
                        copy(
                            scanState = MrzScanState.Error("Erro ao iniciar scanning"),
                            errorMessage = e.message
                        )
                    }
                }
            }
        }

        private fun stopScanning() {
            scannerInteractor.stopScanning()
            setState { copy(scanState = MrzScanState.Idle) }
        }

        private fun retryScanning() {
            setState {
                copy(
                    scannedDocument = null,
                    scanState = MrzScanState.Idle,
                    errorMessage = null
                )
            }
            startScanning()
        }

        // ============================================
        // Tratamento de Resultados
        // ============================================

        private fun handleScanResult(event: Event.OnScanResult) {
            setState {
                copy(
                    scannedDocument = event.document,
                    scanState = MrzScanState.Success(event.document)
                )
            }

            // Para o scanning quando obtém resultado
            stopScanning()

            // Processa o documento baseado no tipo
            handleDocumentByType(event.document)
        }

        private fun handleScanError(event: Event.OnScanError) {
            setState {
                copy(
                    errorMessage = event.message,
                    scanState = MrzScanState.Error(event.message)
                )
            }

            // Pode tentar reiniciar automaticamente após um erro
            viewModelScope.launch {
                kotlinx.coroutines.delay(2000)
                if (viewState.value.scanState is MrzScanState.Error) {
                    retryScanning()
                }
            }
        }

        private fun handleDocumentByType(document: MrzDocument) {
            when (document) {
                is MrzDocument.Passport -> handlePassport(document)
                is MrzDocument.IdCard -> handleIdCard(document)
                is MrzDocument.DrivingLicense -> handleDrivingLicense(document)
            }
        }

        // ============================================
        // Processamento por Tipo de Documento
        // ============================================

        private fun handlePassport(passport: MrzDocument.Passport) {
            // Validações específicas de passaporte
            viewModelScope.launch {
                // Aqui você pode:
                // - Validar dados do passaporte
                // - Salvar no banco de dados
                // - Fazer chamadas à API
                // - Navegar para próxima tela

                // Exemplo: validar checksum
                val isValid = validatePassportChecksum(passport)
                if (!isValid) {
                    setState {
                        copy(
                            scanState = MrzScanState.Error("Passaporte inválido"),
                            errorMessage = "Checksum inválido"
                        )
                    }
                }
            }
        }

        private fun handleIdCard(idCard: MrzDocument.IdCard) {
            // Validações específicas de cartão de cidadão
            viewModelScope.launch {
                // Validar número de documento português
                val isValid = validatePortugueseIdNumber(idCard.documentNumber)
                if (!isValid) {
                    setState {
                        copy(
                            scanState = MrzScanState.Error("Cartão inválido"),
                            errorMessage = "Número de documento inválido"
                        )
                    }
                }
            }
        }

        private fun handleDrivingLicense(license: MrzDocument.DrivingLicense) {
            // Validações específicas de carta de condução
            viewModelScope.launch {
                // Processar categorias de habilitação
            }
        }

        // ============================================
        // Ações do Usuário
        // ============================================

        private fun triggerManualCapture() {
            // Pode pausar o scanning contínuo e capturar um frame específico
            viewModelScope.launch {
                setState { copy(scanState = MrzScanState.Processing()) }
                // Implementar lógica de captura manual
            }
        }

        private fun handleImageSelected(event: Event.OnImageSelected) {
            // Processar imagem da galeria
            viewModelScope.launch {
                setState { copy(scanState = MrzScanState.Processing()) }

                try {
                    // Aqui você processaria a imagem do URI
                    // scannerInteractor.processImage(event.uri)
                } catch (e: Exception) {
                    setState {
                        copy(
                            scanState = MrzScanState.Error("Erro ao processar imagem"),
                            errorMessage = e.message
                        )
                    }
                }
            }
        }

        private fun showHelpDialog() {
            setEffect { Effect.ShowDialog.Help }
        }

        private fun confirmDocument() {
            val document = viewState.value.scannedDocument
            if (document != null) {
                // Navegar para próxima tela com o documento
                setEffect {
                    Effect.Navigation.SwitchScreen(
                        screenRoute = "document_details/${document.documentNumber}",
                        popUpToScreenRoute = "scanner",
                        inclusive = true
                    )
                }
            }
        }

        // ============================================
        // Validações
        // ============================================

        private fun validatePassportChecksum(passport: MrzDocument.Passport): Boolean {
            // Implementar validação de checksum do passaporte
            return true // Placeholder
        }

        private fun validatePortugueseIdNumber(documentNumber: String): Boolean {
            // Validar formato do número de cartão de cidadão português
            // Formato: 00000000 0 ZZ0 (8 dígitos + dígito de controlo + 3 caracteres)
            val regex = Regex("^[0-9]{8}\\s?[0-9]\\s?[A-Z0-9]{3}$")
            return documentNumber.matches(regex)
        }

        // ============================================
        // Limpeza
        // ============================================

        override fun onCleared() {
            super.onCleared()
            scannerInteractor.stopScanning()
        }


}