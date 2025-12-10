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

        // Fundo semi-transparente para focar a atenção no centro
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Dimensões de uma Carta de Condução (Proporção ID-1 ~ 1.58)
            // Aumentado para 95% da largura do ecrã para facilitar leitura
            val cardWidth = canvasWidth * 0.95f
            val cardHeight = cardWidth / 1.58f

            val left = (canvasWidth - cardWidth) / 2
            val top = (canvasHeight - cardHeight) / 2

            // Desenhar escurecimento à volta
            // (Na prática desenhamos 4 retângulos pretos à volta do centro)
            val maskColor = Color.Black.copy(alpha = 0.6f)

            // Topo
            drawRect(maskColor, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(canvasWidth, top))
            // Baixo
            drawRect(maskColor, topLeft = Offset(0f, top + cardHeight), size = androidx.compose.ui.geometry.Size(canvasWidth, canvasHeight - (top + cardHeight)))
            // Esquerda
            drawRect(maskColor, topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(left, cardHeight))
            // Direita
            drawRect(maskColor, topLeft = Offset(left + cardWidth, top), size = androidx.compose.ui.geometry.Size(left, cardHeight))
        }

        // A Caixa de Enquadramento (Borda)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.95f) // 95% da largura
                .aspectRatio(1.58f) // Formato Cartão
                .border(
                    width = 3.dp,
                    color = when (scanState) {
                        is MrzScanState.Success -> Color.Green
                        is MrzScanState.Processing -> Color.Yellow
                        else -> Color.White
                    },
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            // Cantos decorativos
            CornerMarkers()

            // Texto guia centralizado
            if (scanState !is MrzScanState.Success) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "ENQUADRE A CARTA",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // Indicador de progresso de leitura
        if (scanState is MrzScanState.Processing) {
            LinearProgressIndicator(
                progress = scanState.confidence,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.8f)
                    .padding(top = 180.dp) // Abaixo do cartão
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color.Green,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        elevation = CardDefaults.cardElevation(16.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {

            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Carta Detetada", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // Exibir dados mapeados da EuDrivingLicense (convertidos no MrzDocument)
            // Assumindo que o ViewModel mapeou os dados visuais para estes campos
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

            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) { DLField("1. APELIDO", surnames) }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) { DLField("2. NOME", givenNames) }
            }

            // Se for MRZDocument.DrivingLicense, tentamos mostrar categorias se existirem
            if (document is MrzDocument.DrivingLicense && document.licenseCategories.isNotEmpty()) {
                DLField("9. CATEGORIAS", document.licenseCategories)
            }

            // Se tiver datas
            Row(Modifier.fillMaxWidth()) {
                if (document.dateOfBirth.isNotEmpty()) {
                    Box(Modifier.weight(1f)) { DLField("3. NASCIMENTO", document.dateOfBirth) }
                }
                document.expiryDate?.let { expiry ->
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) { DLField("4b. VALIDADE", expiry) }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botões
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onScanAnother, modifier = Modifier.weight(1f)) { Text("Repetir") }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("Confirmar") }
            }
        }
    }
}

@Composable
private fun DLField(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
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