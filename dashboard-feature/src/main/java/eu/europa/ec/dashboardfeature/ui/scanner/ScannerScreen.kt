package eu.europa.ec.dashboardfeature.ui.scanner

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import eu.europa.ec.mrzscannerLogic.controller.MrzScanState
import eu.europa.ec.mrzscannerLogic.model.MrzDocument
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.extension.openAppSettings
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Refresh

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    navHostController: NavController,
    viewModel: ScannerViewModel,
) {
    val context = LocalContext.current
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val effects = viewModel.effect

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(Event.GoBack) },
        stickyBottom = { paddingValues -> }
    ) { paddingValues ->
        Content(
            state = state,
            paddingValues = paddingValues,
            viewModel = viewModel
        )
    }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is Effect.Navigation.Pop -> navHostController.popBackStack()
                is Effect.Navigation.SwitchScreen -> {
                    navHostController.navigate(effect.screenRoute) {
                        popUpTo(effect.popUpToScreenRoute) { inclusive = effect.inclusive }
                    }
                }
                is Effect.Navigation.OnAppSettings -> context.openAppSettings()
                else -> {}
            }
        }
    }
}

@Composable
private fun Content(
    state: State,
    paddingValues: PaddingValues,
    viewModel: ScannerViewModel? = null
) {
    when (state.isCameraAvailability) {
        CameraAvailability.DISABLED -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CameraDisabledMessage()
            }
        }
        CameraAvailability.NO_PERMISSION -> {
            RequiredPermissionsAsk(state) { event ->
                viewModel?.setEvent(event)
            }
        }
        CameraAvailability.AVAILABLE -> {
            CameraScannerContent(
                state = state,
                viewModel = viewModel,
                paddingValues = paddingValues
            )
        }
        else -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}


@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RequiredPermissionsAsk(
    state: State,
    onEventSend: (Event) -> Unit
) {
    val permissions = listOf(Manifest.permission.CAMERA)
    val showDenied = remember { mutableStateOf(false) }
    val permissionsState = rememberMultiplePermissionsState(permissions) { results ->
        if (results.values.all { it }) {
            onEventSend(Event.OnPermissionStateChanged(CameraAvailability.AVAILABLE))
        } else {
            showDenied.value = true
        }
    }

    LaunchedEffect(Unit) {
        permissionsState.launchMultiplePermissionRequest()
    }

    if (showDenied.value) {
        CameraPermissionDenied {
            onEventSend(Event.OpenAppSettings)
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun CameraPermissionDenied(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(0.8f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.NoPhotography,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                VSpacer.Medium()
                Text(
                    text = "Acesso à Câmera Negado",
                    style = MaterialTheme.typography.headlineSmall
                )
                VSpacer.Small()
                Text(
                    text = "Não foi possível acessar a câmera. Por favor, conceda permissão nas configurações do seu dispositivo para escanear documentos.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                VSpacer.Medium()
                Button(onClick = onOpenSettings) {
                    Text("Abrir Configurações")
                }
            }
        }
    }
}
@Composable
private fun CameraScannerContent(
    state: State,
    viewModel: ScannerViewModel?,
    paddingValues: PaddingValues
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewViewState = remember { mutableStateOf<PreviewView?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel?.setEvent(Event.OnImageSelected(uri))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }.also {
                    previewViewState.value = it
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Inicializa o controller quando a PreviewView estiver pronta
        LaunchedEffect(previewViewState.value) {
            val pv = previewViewState.value
            if (pv != null) {
                viewModel?.setEvent(
                    Event.InitializeScanner(
                        lifecycleOwner = lifecycleOwner,
                        previewView = pv
                    )
                )
            }
        }

        // Overlay de scanning
        MrzScanOverlay(
            scanState = state.scanState,
            onStopScanning = { viewModel?.setEvent(Event.StopScanning) }
        )

        // Instruções no topo
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
        ) {
            ScanInstructions(state.scanState)
        }

        // Botões de controle
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = "Galeria",
                    tint = Color.White
                )
            }
            IconButton(onClick = { viewModel?.setEvent(Event.TriggerManualCapture) }) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "Capturar",
                    tint = Color.White
                )
            }
            IconButton(onClick = { viewModel?.setEvent(Event.ShowHelp) }) {
                Icon(
                    Icons.Default.HelpOutline,
                    contentDescription = "Ajuda",
                    tint = Color.White
                )
            }
        }

        // Resultado do scan
        state.scannedDocument?.let { document ->
            DocumentResultCard(
                document = document,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                onConfirm = { viewModel?.setEvent(Event.ConfirmDocument) },
                onRetry = { viewModel?.setEvent(Event.RetryScanning) }
            )
        }
    }
}



@Composable
private fun MrzScanOverlay(
    scanState: MrzScanState,
    onStopScanning: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Guia de posicionamento do documento
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(300.dp)
                .height(200.dp)
                .border(
                    width = 3.dp,
                    color = when (scanState) {
                        is MrzScanState.Scanning -> Color.White
                        is MrzScanState.Processing -> Color.Yellow
                        is MrzScanState.Success -> Color.Green
                        is MrzScanState.Error -> Color.Red
                        else -> Color.White
                    },
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            // Cantos do frame
            CornerMarkers()
        }

        // Badge de status
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 120.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (scanState) {
                    is MrzScanState.Processing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    else -> {}
                }

                Text(
                    text = when (scanState) {
                        is MrzScanState.Initializing -> "Inicializando..."
                        is MrzScanState.Scanning -> "Posicione o documento"
                        is MrzScanState.Processing -> "Processando..."
                        is MrzScanState.Success -> "✓ Leitura bem-sucedida"
                        is MrzScanState.Error -> "✗ ${scanState.message}"
                        else -> "Pronto"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CornerMarkers() {
    val cornerSize = 24.dp
    val strokeWidth = 3.dp

    Canvas(modifier = Modifier.fillMaxSize()) {
        val color = Color.White
        val length = cornerSize.toPx()
        val width = strokeWidth.toPx()

        // Canto superior esquerdo
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(length, 0f),
            strokeWidth = width
        )
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(0f, length),
            strokeWidth = width
        )

        // Canto superior direito
        drawLine(
            color = color,
            start = Offset(size.width, 0f),
            end = Offset(size.width - length, 0f),
            strokeWidth = width
        )
        drawLine(
            color = color,
            start = Offset(size.width, 0f),
            end = Offset(size.width, length),
            strokeWidth = width
        )

        // Canto inferior esquerdo
        drawLine(
            color = color,
            start = Offset(0f, size.height),
            end = Offset(length, size.height),
            strokeWidth = width
        )
        drawLine(
            color = color,
            start = Offset(0f, size.height),
            end = Offset(0f, size.height - length),
            strokeWidth = width
        )

        // Canto inferior direito
        drawLine(
            color = color,
            start = Offset(size.width, size.height),
            end = Offset(size.width - length, size.height),
            strokeWidth = width
        )
        drawLine(
            color = color,
            start = Offset(size.width, size.height),
            end = Offset(size.width, size.height - length),
            strokeWidth = width
        )
    }
}

@Composable
private fun DocumentResultCard(
    document: MrzDocument,
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (document) {
                        is MrzDocument.Passport -> "📘 PASSAPORTE"
                        is MrzDocument.IdCard -> "🪪 CARTÃO DE CIDADÃO"
                        is MrzDocument.DrivingLicense -> "🚗 CARTA DE CONDUÇÃO"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            VSpacer.Medium()

            when (document) {
                is MrzDocument.Passport -> {
                    DocumentField("Nome", "${document.givenNames} ${document.surname}")
                    DocumentField("Número", document.documentNumber)
                    DocumentField("País", document.issuingCountry)
//                    DocumentField("Validade", document?.expiryDate)
                }
                is MrzDocument.IdCard -> {
                    DocumentField("Nome", "${document.givenNames} ${document.surname}")
                    DocumentField("Número", document.documentNumber)
                    DocumentField("Nacionalidade", document.nationality)
//                    DocumentField("Validade", document.expiryDate)
                }
                is MrzDocument.DrivingLicense -> {
                    DocumentField("Nome", "${document.givenNames} ${document.surname}")
                    DocumentField("Número", document.documentNumber)
                    DocumentField("Categorias", document.licenseCategories)
                }
            }

            VSpacer.Medium()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reler")
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Confirmar")
                }
            }
        }
    }
}

@Composable
private fun ScanInstructions(scanState: MrzScanState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (scanState) {
                    is MrzScanState.Scanning -> "Alinhe a zona MRZ no quadro"
                    is MrzScanState.Processing -> "Analisando documento..."
                    is MrzScanState.Success -> "Documento lido com sucesso!"
                    else -> "Prepare o documento"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (scanState is MrzScanState.Scanning) {
                VSpacer.Small()
                Text(
                    text = "• Mantenha o documento plano\n" +
                            "• Evite reflexos de luz\n" +
                            "• A zona MRZ fica na parte inferior",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}


@Composable
private fun DocumentField(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun CameraDisabledMessage() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            VSpacer.Medium()
            Text(
                text = "Câmera não disponível",
                style = MaterialTheme.typography.headlineSmall
            )
            VSpacer.Medium()
            Text(
                text = "Este dispositivo não possui câmera ou a câmera não está funcionando.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@ThemeModePreviews
@Composable
private fun ScannScreenContentPreview() {
    PreviewTheme {
        ContentScreen(
            isLoading = false,
            navigatableAction = ScreenNavigateAction.BACKABLE,
            onBack = {
            },
            stickyBottom = {
            }
        ) { paddingValues ->
            Content(
                state = State(),
                paddingValues = PaddingValues(0.dp)
            )
        }
    }
}