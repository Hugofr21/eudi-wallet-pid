package eu.europa.ec.dashboardfeature.ui.scanner.identificationDocument

import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.nimbusds.jose.shaded.gson.Gson
import eu.europa.ec.commonfeature.config.IssuanceFlowType
import eu.europa.ec.commonfeature.config.IssuanceUiConfig
import eu.europa.ec.dashboardfeature.interactor.ScannerInteractor
import eu.europa.ec.dashboardfeature.ui.dashboard.DashboardScreen
import eu.europa.ec.mrzscannerLogic.controller.MrzScanState
import eu.europa.ec.mrzscannerLogic.model.MrzDocument
import eu.europa.ec.mrzscannerLogic.model.ScanType
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
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
    val isPermissionChecked: Boolean = false
) : ViewState

sealed class Event : ViewEvent {
    object GoBack : Event()

    // Inicialização
    data class InitializeScanner(
        val lifecycleOwner: LifecycleOwner,
        val previewView: PreviewView,
        val scanType: ScanType
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

    override fun setInitialState(): State {
        return State(
            isLoading = false,
            isCameraAvailability = null,
            scanState = MrzScanState.Idle,
            isPermissionChecked = false
        )
    }

    init {
        // Verificar permissões ao inicializar
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
            is Event.OnImageSelected -> handleImageSelected(event)
            Event.ShowHelp -> showHelpDialog()
            Event.ConfirmDocument -> confirmDocument()
            Event.OpenAppSettings -> setEffect { Effect.Navigation.OnAppSettings }
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
        val owner = lifecycleOwner
        val preview = previewView

        if (owner == null || preview == null) {
            setState {
                copy(
                    scanState = MrzScanState.Error("Câmera não inicializada"),
                    errorMessage = "LifecycleOwner ou PreviewView não disponível"
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                // SCANNING CONTÍNUO: Vai continuar lendo documentos indefinidamente
                scannerInteractor.startScanning(owner, preview, ScanType.Document).collect { scanState ->
                    setState { copy(scanState = scanState) }

                    when (scanState) {
                        is MrzScanState.Success -> {
                            val doc = scanState.document
                            // Documento detectado com sucesso
                            if (doc != null)
                                setEvent(Event.OnScanResult(doc))
                            else
                                setEvent(Event.OnScanError("Documento não reconhecido"))

                            // MUDANÇA: NÃO para o scanning automaticamente
                            // Apenas mostra o resultado e continua escaneando
                            // O usuário decide se quer parar ou continuar
                        }
                        is MrzScanState.Error -> {
                            setEvent(Event.OnScanError(scanState.message))
                            // Em caso de erro, continua tentando
                        }
                        else -> {
                            // Estados intermediários: Idle, Initializing, Scanning, Processing
                            // Apenas atualiza a UI
                        }
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
        setState {
            copy(
                scanState = MrzScanState.Idle,
                scannedDocument = null // Limpar documento ao parar
            )
        }
    }

    private fun retryScanning() {
        setState {
            copy(
                scannedDocument = null,
                scanState = MrzScanState.Idle,
                errorMessage = null
            )
        }
        // Reiniciar o scanning
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

        // MUDANÇA: NÃO para o scanning
        // Agora o scanner continua rodando em background
        // O usuário pode confirmar ou reler outro documento

        // Validar documento automaticamente
        handleDocumentByType(event.document)
    }

    private fun handleScanError(event: Event.OnScanError) {
        setState {
            copy(
                errorMessage = event.message,
                scanState = MrzScanState.Error(event.message)
            )
        }

        // MUDANÇA: NÃO tenta reiniciar automaticamente
        // O scanner já está em loop contínuo
        // Apenas mostra o erro e continua escaneando
    }

    private fun handleDocumentByType(document: MrzDocument) {
        println("Document type: ${document.javaClass.simpleName}")
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
        viewModelScope.launch {
            val isValid = validatePassportChecksum(passport)
            if (!isValid) {
                setState {
                    copy(
                        errorMessage = "Passaporte com checksum inválido"
                    )
                }
            }
        }
    }

    private fun handleIdCard(idCard: MrzDocument.IdCard) {
        viewModelScope.launch {
            val isValid = validatePortugueseIdNumber(idCard.documentNumber)
            if (!isValid) {
                setState {
                    copy(
                        errorMessage = "Número de cartão de cidadão inválido"
                    )
                }
            }
        }
    }

    private fun handleDrivingLicense(license: MrzDocument.DrivingLicense) {
        viewModelScope.launch {
            // Validações de carta de condução
        }
    }

    // ============================================
    // Ações do Usuário
    // ============================================

    private fun triggerManualCapture() {
        viewModelScope.launch {
            setState { copy(scanState = MrzScanState.Processing()) }
            // Implementar captura manual se necessário
        }
    }

    private fun handleImageSelected(event: Event.OnImageSelected) {
        viewModelScope.launch {
            setState { copy(scanState = MrzScanState.Processing()) }

            try {
                // Processar imagem da galeria
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
        val jsonDoc = Gson().toJson(document)
        if (document != null) {
            // Parar scanning ao confirmar
            stopScanning()

            setEffect {
                Effect.Navigation.SwitchScreen(
                    screenRoute = generateComposableNavigationLink(
                        screen = DashboardScreens.FaceIdDetails,
                        arguments = generateComposableArguments(
                            mapOf(
                                "documentId" to jsonDoc
                            )
                        )
                    )
                )
            }
        }
    }

    // ============================================
    // Validações
    // ============================================

    private fun validatePassportChecksum(passport: MrzDocument.Passport): Boolean {
        // TODO: Implementar validação real
        return true
    }

    private fun validatePortugueseIdNumber(documentNumber: String): Boolean {
        // Formato: 00000000 0 ZZ0
        val regex = Regex("^[0-9]{8}\\s?[0-9]\\s?[A-Z0-9]{3}$")
        return documentNumber.matches(regex)
    }

    // ============================================
    // Limpeza
    // ============================================

    override fun onCleared() {
        super.onCleared()
        stopScanning()
        lifecycleOwner = null
        previewView = null
    }
}