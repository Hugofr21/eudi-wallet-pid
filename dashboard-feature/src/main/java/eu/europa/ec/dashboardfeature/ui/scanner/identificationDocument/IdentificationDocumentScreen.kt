package eu.europa.ec.dashboardfeature.ui.scanner.identificationDocument

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
import eu.europa.ec.uilogic.extension.openAppSettings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentificationDocumentScreen(
    navHostController: NavController,
    viewModel: IdentificationDocumentViewModel,
) {
    val context = LocalContext.current
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val effects = viewModel.effect
    val lifecycleOwner = LocalLifecycleOwner.current

    // Verificar permissões ao retornar
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.setEvent(Event.CheckPermissions)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(Event.GoBack) },
        stickyBottom = { }
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
    viewModel: IdentificationDocumentViewModel? = null
) {
    when (state.isCameraAvailability) {
        CameraAvailability.DISABLED -> {
            CameraDisabledMessage(paddingValues)
        }
        CameraAvailability.NO_PERMISSION -> {
            RequiredPermissionsAsk { event ->
                viewModel?.setEvent(event)
            }
        }
        CameraAvailability.AVAILABLE -> {
            AutomaticScannerContent(
                state = state,
                viewModel = viewModel,
                paddingValues = paddingValues
            )
        }
        else -> {
            LoadingScreen()
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RequiredPermissionsAsk(
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
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        } else {
            onEventSend(Event.OnPermissionStateChanged(CameraAvailability.AVAILABLE))
        }
    }

    if (showDenied.value) {
        PermissionDeniedMessage {
            onEventSend(Event.OpenAppSettings)
        }
    } else {
        LoadingScreen("Solicitando permissão...")
    }
}

// ============================================
// SCANNER AUTOMÁTICO - SEM BOTÕES
// ============================================

@Composable
private fun AutomaticScannerContent(
    state: State,
    viewModel: IdentificationDocumentViewModel?,
    paddingValues: PaddingValues
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // PreviewView criada com remember
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Inicializar AUTOMATICAMENTE ao montar
    LaunchedEffect(Unit) {
        viewModel?.setEvent(
            Event.InitializeScanner(
                lifecycleOwner = lifecycleOwner,
                previewView = previewView
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(Color.Black)
    ) {
        // Preview da câmera - Full screen
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay com guia de posicionamento
        if (state.scannedDocument == null) {
            ScanningOverlay(state.scanState)
        }

        // Instruções animadas
        AnimatedVisibility(
            visible = state.scannedDocument == null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        ) {
            ScanInstructions(state.scanState)
        }

        // Resultado AUTOMÁTICO
        AnimatedVisibility(
            visible = state.scannedDocument != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            state.scannedDocument?.let { document ->
                AutomaticResultCard(
                    document = document,
                    onConfirm = { viewModel?.setEvent(Event.ConfirmDocument) },
                    onScanAnother = { viewModel?.setEvent(Event.RetryScanning) }
                )
            }
        }

        // Indicador de confiança (durante Processing)
        if (state.scanState is MrzScanState.Processing) {
            ConfidenceIndicator(
                confidence = state.scanState.confidence,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// ============================================
// OVERLAY DE SCANNING
// ============================================

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ScanningOverlay(scanState: MrzScanState) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Frame guia para posicionar documento
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(340.dp)
                .height(240.dp)
                .border(
                    width = 4.dp,
                    color = when (scanState) {
                        is MrzScanState.Scanning -> Color.White
                        is MrzScanState.Processing -> {
                            // Cor animada baseada na confiança
                            val confidence = scanState.confidence
                            Color.White.copy(
                                red = 1f - confidence,
                                green = confidence
                            )
                        }
                        is MrzScanState.Success -> Color.Green
                        is MrzScanState.Error -> Color.Red
                        else -> Color.White
                    },
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            // Cantos do frame
            CornerMarkers()

            // Zona MRZ destacada
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.92f)
                    .height(80.dp)
                    .padding(8.dp)
                    .background(
                        when (scanState) {
                            is MrzScanState.Processing ->
                                Color.Blue.copy(alpha = 0.2f + scanState.confidence * 0.3f)
                            else -> Color.Blue.copy(alpha = 0.25f)
                        },
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ZONA MRZ",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (scanState is MrzScanState.Processing && scanState.confidence > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${(scanState.confidence * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Status badge animado
        AnimatedContent(
            targetState = scanState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 140.dp),
            transitionSpec = {
                fadeIn() + scaleIn() with fadeOut() + scaleOut()
            }
        ) { state ->
            StatusBadge(state)
        }
    }
}

@Composable
private fun StatusBadge(scanState: MrzScanState) {
    Surface(
        color = when (scanState) {
            is MrzScanState.Processing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
            is MrzScanState.Success -> Color(0xFF4CAF50).copy(alpha = 0.9f)
            is MrzScanState.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        },
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (scanState) {
                is MrzScanState.Initializing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Iniciando...", color = Color.White, fontWeight = FontWeight.Medium)
                }

                is MrzScanState.Scanning -> {
                    Icon(Icons.Default.DocumentScanner, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Procurando documento...", color = Color.White, fontWeight = FontWeight.Medium)
                }

                is MrzScanState.Processing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                        progress = scanState.confidence
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Lendo... ${(scanState.confidence * 100).toInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }

                is MrzScanState.Success -> {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("✓ Documento detectado!", color = Color.White, fontWeight = FontWeight.Bold)
                }

                is MrzScanState.Error -> {
                    Icon(Icons.Default.Error, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(scanState.message, color = Color.White, fontWeight = FontWeight.Medium)
                }

                else -> {
                    Text("Scanner Automático", color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ============================================
// INSTRUÇÕES DE USO
// ============================================

@Composable
private fun ScanInstructions(scanState: MrzScanState) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = when (scanState) {
                    is MrzScanState.Scanning -> Icons.Default.DocumentScanner
                    is MrzScanState.Processing -> Icons.Default.Search
                    else -> Icons.Default.CameraAlt
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when (scanState) {
                    is MrzScanState.Scanning -> "Posicione o documento no quadro"
                    is MrzScanState.Processing -> "Analisando dados..."
                    else -> "Scanner Automático Ativo"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "• Documento plano e centralizado\n" +
                        "• Boa iluminação, sem reflexos\n" +
                        "• Zona MRZ visível (parte inferior)\n" +
                        "• Detecção é automática",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================
// INDICADOR DE CONFIANÇA
// ============================================

@Composable
private fun ConfidenceIndicator(
    confidence: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Column {
            Text(
                "Analisando documento...",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LinearProgressIndicator(
                progress = confidence,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color.Green,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

// ============================================
// CARD DE RESULTADO AUTOMÁTICO
// ============================================

@Composable
private fun AutomaticResultCard(
    document: MrzDocument,
    onConfirm: () -> Unit,
    onScanAnother: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(16.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Header com tipo de documento
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when (document) {
                            is MrzDocument.Passport -> "Passaporte"
                            is MrzDocument.IdCard -> "Cartão de Cidadão"
                            is MrzDocument.DrivingLicense -> "Carta de Condução"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = when (document) {
                            is MrzDocument.Passport -> "📘"
                            is MrzDocument.IdCard -> "🪪"
                            is MrzDocument.DrivingLicense -> "🚗"
                        } + " Detectado Automaticamente",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(36.dp)
                )
            }

            Divider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )

            // Dados do documento
            when (document) {
                is MrzDocument.Passport -> {
                    DocumentField("Nome", "${document.givenNames} ${document.surname}")
                    DocumentField("Número", document.documentNumber)
                    DocumentField("País", document.issuingCountry)
                    DocumentField("Nacionalidade", document.nationality)
                    DocumentField("Nacionalidade", document.personalNumber)
                    DocumentField("Data de Nascimento", document.dateOfBirth)
                    document.expiryDate?.let { DocumentField("Validade", it) }
                    DocumentField("Sexo", document.sex)
                    DocumentField("Categoria", document.dateOfExpiry)
                }
                is MrzDocument.IdCard -> {
                    DocumentField("Nome", "${document.givenNames} ${document.surname}")
                    DocumentField("Número", document.documentNumber)
                    DocumentField("Nacionalidade", document.nationality)
                    DocumentField("Data de Nascimento", document.dateOfBirth)
                    document.expiryDate?.let { DocumentField("Validade", it) }
                    DocumentField("Sexo", document.sex)
                }
                is MrzDocument.DrivingLicense -> {
                    DocumentField("Nome", "${document.givenNames} ${document.surname}")
                    DocumentField("Número", document.documentNumber)
                    DocumentField("Categorias", document.licenseCategories)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Ações
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onScanAnother,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Outro")
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Confirmar")
                }
            }
        }
    }
}

// ============================================
// COMPONENTES AUXILIARES
// ============================================

@Composable
private fun CornerMarkers() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val color = Color.White
        val length = 32.dp.toPx()
        val width = 5.dp.toPx()

        // 4 cantos
        listOf(
            Pair(Offset(0f, 0f), listOf(Offset(length, 0f), Offset(0f, length))),
            Pair(Offset(size.width, 0f), listOf(Offset(size.width - length, 0f), Offset(size.width, length))),
            Pair(Offset(0f, size.height), listOf(Offset(length, size.height), Offset(0f, size.height - length))),
            Pair(Offset(size.width, size.height), listOf(Offset(size.width - length, size.height), Offset(size.width, size.height - length)))
        ).forEach { (corner, ends) ->
            ends.forEach { end ->
                drawLine(color, corner, end, width)
            }
        }
    }
}

@Composable
private fun DocumentField(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(120.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LoadingScreen(message: String = "Iniciando scanner...") {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(message)
        }
    }
}

@Composable
private fun PermissionDeniedMessage(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.padding(24.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.NoPhotography, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Text("Permissão Negada", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("É necessário acesso à câmera.", textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir Configurações")
                }
            }
        }
    }
}

@Composable
private fun CameraDisabledMessage(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.padding(24.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.CameraAlt, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Text("Câmera Indisponível", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("Este dispositivo não possui câmera funcional.", textAlign = TextAlign.Center)
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@ThemeModePreviews
@Composable
private fun IdentificationDocumentScreenContentPreview() {
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