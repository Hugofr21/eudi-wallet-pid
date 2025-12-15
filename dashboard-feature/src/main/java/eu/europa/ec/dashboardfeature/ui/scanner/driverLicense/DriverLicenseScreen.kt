package eu.europa.ec.dashboardfeature.ui.scanner.driverLicense


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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverLicenseScreen(
    navHostController: NavController,
    viewModel: DriverLicenseScreenViewModel,
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
    viewModel: DriverLicenseScreenViewModel? = null
) {
    when (state.isCameraAvailability) {
        CameraAvailability.DISABLED -> CameraDisabledMessage(paddingValues)
        CameraAvailability.NO_PERMISSION -> RequiredPermissionsAsk { event -> viewModel?.setEvent(event) }
        CameraAvailability.AVAILABLE -> AutomaticScannerContent(state, viewModel, paddingValues)
        else -> LoadingScreen()
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RequiredPermissionsAsk(onEventSend: (Event) -> Unit) {
    val permissions = listOf(Manifest.permission.CAMERA)
    val showDenied = remember { mutableStateOf(false) }
    val permissionsState = rememberMultiplePermissionsState(permissions) { results ->
        if (results.values.all { it }) onEventSend(Event.OnPermissionStateChanged(CameraAvailability.AVAILABLE))
        else showDenied.value = true
    }

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) permissionsState.launchMultiplePermissionRequest()
        else onEventSend(Event.OnPermissionStateChanged(CameraAvailability.AVAILABLE))
    }

    if (showDenied.value) PermissionDeniedMessage { onEventSend(Event.OpenAppSettings) }
    else LoadingScreen("Solicitando permissão...")
}

// ============================================
// SCANNER AUTOMÁTICO - CARTA DE CONDUÇÃO
// ============================================

@Composable
private fun AutomaticScannerContent(
    state: State,
    viewModel: DriverLicenseScreenViewModel?,
    paddingValues: PaddingValues
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(Unit) {
        viewModel?.setEvent(Event.InitializeScanner(lifecycleOwner, previewView))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(Color.Black)
    ) {
        // Preview da câmera
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Overlay com guia de posicionamento (MUITO MAIOR AGORA)
        if (state.scannedDocument == null) {
            DrivingLicenseOverlay(state.scanState)
        }

        // Instruções
        AnimatedVisibility(
            visible = state.scannedDocument == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
        ) {
            ScanInstructions()
        }

        // Resultado AUTOMÁTICO
        AnimatedVisibility(
            visible = state.scannedDocument != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            state.scannedDocument?.let { document ->
                DrivingLicenseResultCard(
                    document = document,
                    onConfirm = { viewModel?.setEvent(Event.ConfirmDocument) },
                    onScanAnother = { viewModel?.setEvent(Event.RetryScanning) }
                )
            }
        }
    }
}

// ============================================
// OVERLAY ESPECÍFICO PARA CARTA DE CONDUÇÃO
// ============================================
@Composable
private fun DrivingLicenseOverlay(scanState: MrzScanState) {
    Box(modifier = Modifier.fillMaxSize()) {

        // Fundo semi-transparente (Máscara)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // AUMENTAR A ÁREA: Usar 96% da largura em vez de 95%
            val cardWidth = canvasWidth * 0.96f
            // Proporção ID-1 (Cartão de Crédito/Carta): 85.60 × 53.98 mm (~1.58)
            val cardHeight = cardWidth / 1.58f

            val left = (canvasWidth - cardWidth) / 2
            val top = (canvasHeight - cardHeight) / 2

            // Diminuí a opacidade de 0.6f para 0.5f para ver melhor "à volta"
            val maskColor = Color.Black.copy(alpha = 0.5f)

            // Desenhar máscara (4 retângulos à volta do buraco)
            // Topo
            drawRect(maskColor, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(canvasWidth, top))
            // Baixo
            drawRect(maskColor, topLeft = Offset(0f, top + cardHeight), size = androidx.compose.ui.geometry.Size(canvasWidth, canvasHeight - (top + cardHeight)))
            // Esquerda
            drawRect(maskColor, topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(left, cardHeight))
            // Direita
            drawRect(maskColor, topLeft = Offset(left + cardWidth, top), size = androidx.compose.ui.geometry.Size(left, cardHeight))
        }

        // A Borda de Enquadramento (O "Retângulo")
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.96f) // Coincide com o Canvas
                .aspectRatio(1.58f)
                .border(
                    width = 4.dp, // Borda mais grossa para ser bem visível
                    color = when (scanState) {
                        is MrzScanState.Success -> Color(0xFF00FF00) // Verde Neon
                        is MrzScanState.Processing -> Color(0xFFFFEB3B) // Amarelo
                        else -> Color.White.copy(alpha = 0.8f)
                    },
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            // Cantos decorativos (Mira)
            CornerMarkers()
        }

        // Feedback de Texto (Instrução central)
        if (scanState !is MrzScanState.Success) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 16.dp) // Ligeiramente desfasado do centro exato
            ) {
                // Barra de Progresso se estiver a ler
                if (scanState is MrzScanState.Processing) {
                    LinearProgressIndicator(
                        progress = scanState.confidence,
                        modifier = Modifier
                            .width(200.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color.Green,
                        trackColor = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// ============================================
// RESULTADO: CAMPOS ESPECÍFICOS CARTA
// ============================================
@Composable
private fun DrivingLicenseResultCard(
    document: MrzDocument,
    onConfirm: () -> Unit,
    onScanAnother: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp), // Margem inferior
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
            // Permitir scroll se houver muitos dados (opcional, mas recomendado)
            // .verticalScroll(rememberScrollState())
        ) {

            // --- CABEÇALHO ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Carta Detetada", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color.LightGray.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // --- CAMPOS PRINCIPAIS (1, 2, 5) ---

            // Campo 5: Número da Carta
            DLField("5. NÚMERO DA CARTA", document.documentNumber)

            val surnames = when (document) {
                is MrzDocument.Passport -> document.surname
                is MrzDocument.IdCard -> document.surname
                is MrzDocument.DrivingLicense -> document.surname
            }

            val givenNames = when (document) {
                is MrzDocument.Passport -> document.givenNames
                is MrzDocument.IdCard -> document.givenNames
                is MrzDocument.DrivingLicense -> document.givenNames
            }

            // Campos 1 e 2: Nomes
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) { DLField("1. APELIDO", surnames) }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) { DLField("2. NOME", givenNames) }
            }

            // --- CAMPOS ESPECÍFICOS DA CARTA (Smart Cast) ---
            if (document is MrzDocument.DrivingLicense) {

                // Campo 3: Data e Local
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(0.4f)) {
                        DLField("3. NASCIMENTO", document.dateOfBirth)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(0.6f)) {
                        // O 'placeOfBirth' vem do split do campo 3
                        DLField("LOCAL", document.placeOfBirth)
                    }
                }

                // Campos 4a, 4b, 4c
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) {
                        DLField("4a. EMISSÃO", document.dateOfIssue)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        DLField("4b. VALIDADE", document.dateOfExpiry)
                    }
                }

                DLField("4c. ENTIDADE EMISSORA", document.issuingAuthority)

                // Campos 4d, 9 e 8
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) {
                        DLField("4d. N. CONTROLO", document.auditNumber)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        DLField("9. CATEGORIAS", document.licenseCategories)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Campo 8: Morada (Importante!)
                // Usamos um fundo ligeiro para destacar a morada
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(8.dp)) {
                        DLField("8. MORADA", document.address)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- BOTÕES ---
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onScanAnother,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Repetir")
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Confirmar")
                }
            }
        }
    }
}

// Pequeno ajuste no componente de campo para lidar com nulos de forma elegante
@Composable
private fun DLField(label: String, value: String?) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
        Text(
            text = if (value.isNullOrBlank()) "-" else value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ============================================
// COMPONENTES VISUAIS
// ============================================

@Composable
private fun ScanInstructions() {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(24.dp)
    ) {
        Text(
            text = "Posicione a carta dentro do retângulo",
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun CornerMarkers() {

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val color = Color.White
        val length = 32.dp.toPx()
        val stroke = 4.dp.toPx()
        // Top Left
        drawRect(color, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(length, stroke))
        drawRect(color, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(stroke, length))
        // Top Right
        drawRect(color, topLeft = Offset(w - length, 0f), size = androidx.compose.ui.geometry.Size(length, stroke))
        drawRect(color, topLeft = Offset(w - stroke, 0f), size = androidx.compose.ui.geometry.Size(stroke, length))
        // Bottom Left
        drawRect(color, topLeft = Offset(0f, h - stroke), size = androidx.compose.ui.geometry.Size(length, stroke))
        drawRect(color, topLeft = Offset(0f, h - length), size = androidx.compose.ui.geometry.Size(stroke, length))
        // Bottom Right
        drawRect(color, topLeft = Offset(w - length, h - stroke), size = androidx.compose.ui.geometry.Size(length, stroke))
        drawRect(color, topLeft = Offset(w - stroke, h - length), size = androidx.compose.ui.geometry.Size(stroke, length))
    }
}

// Componentes básicos de erro/permissão mantidos iguais
@Composable
private fun LoadingScreen(msg: String = "Iniciando câmara...") {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Text(msg, color = Color.White, modifier = Modifier.padding(top=8.dp))
        }
    }
}

@Composable
private fun CameraDisabledMessage(paddingValues: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) { Text("Câmara indisponível") }
}

@Composable
private fun PermissionDeniedMessage(onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onOpenSettings) { Text("Permitir Câmara") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@ThemeModePreviews
@Composable
private fun DriverLicenseScreenContentPreview() {
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