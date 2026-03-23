package eu.europa.ec.dashboardfeature.ui.scanner.driverLicense

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import eu.europa.ec.dashboardfeature.ui.scanner.utils.Translate.translateSecurity
import eu.europa.ec.mrzscannerLogic.controller.MrzScanState
import eu.europa.ec.mrzscannerLogic.model.AntiSpoofingCheck
import eu.europa.ec.mrzscannerLogic.model.MrzDocument
import eu.europa.ec.resourceslogic.theme.values.success
import eu.europa.ec.resourceslogic.theme.values.warning
import eu.europa.ec.uilogic.extension.openAppSettings

@Composable
fun DriverLicenseScreen(
    navHostController: NavController,
    viewModel: DriverLicenseScreenViewModel,
) {
    val context        = LocalContext.current
    val state          by viewModel.viewState.collectAsStateWithLifecycle()
    val effects        = viewModel.effect
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.setEvent(Event.CheckPermissions)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is Effect.Navigation.Pop           -> navHostController.popBackStack()
                is Effect.Navigation.OnAppSettings -> context.openAppSettings()
                else -> {}
            }
        }
    }

    Scaffold(
        modifier       = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            state.isCameraAvailability == CameraAvailability.NO_PERMISSION ->
                RequiredPermissionsAsk { viewModel.setEvent(it) }
            state.isCameraAvailability == CameraAvailability.AVAILABLE ->
                ContentManager(state, viewModel, padding)
            else -> LoadingScreen()
        }
    }
}

// ─── Permissões ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RequiredPermissionsAsk(onEventSend: (Event) -> Unit) {
    val perms = rememberMultiplePermissionsState(listOf(Manifest.permission.CAMERA)) {
        onEventSend(
            Event.OnPermissionStateChanged(
                if (it.values.all { g -> g }) CameraAvailability.AVAILABLE
                else CameraAvailability.NO_PERMISSION
            )
        )
    }
    LaunchedEffect(Unit) { perms.launchMultiplePermissionRequest() }
    LoadingScreen()
}

// ─── Router ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ContentManager(
    state        : State,
    viewModel    : DriverLicenseScreenViewModel,
    paddingValues: PaddingValues,
) {
    AnimatedContent(
        targetState    = state.isInstructionsAcknowledged,
        transitionSpec = { fadeIn() with fadeOut() },
        label          = "InstructionsTransition"
    ) { acknowledged ->
        if (!acknowledged) {
            InstructionsScreen(
                paddingValues = paddingValues,
                onClose       = { viewModel.setEvent(Event.GoBack) },
                onAcknowledge = { viewModel.setEvent(Event.AcknowledgeInstructions) }
            )
        } else {
            AutomaticScannerContent(state, viewModel, paddingValues)
        }
    }
}


@Composable
private fun InstructionsScreen(
    paddingValues: PaddingValues,
    onClose      : () -> Unit,
    onAcknowledge: () -> Unit,
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopBar(title = "Leitor de Carta", onClose = onClose)

        Spacer(Modifier.height(48.dp))

        Box(
            modifier         = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Default.DocumentScanner,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(36.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text       = "Posicione a Zona MRZ\ndentro da área de leitura",
            fontSize   = 20.sp,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground,
            textAlign  = TextAlign.Center,
            lineHeight = 28.sp,
            modifier   = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text      = "Certifique-se de que o documento está bem iluminado, sem reflexos e que a zona MRZ está completamente visível.",
            fontSize  = 14.sp,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier  = Modifier.padding(horizontal = 36.dp)
        )

        Spacer(Modifier.height(32.dp))

        InstructionTips()

        Spacer(Modifier.weight(1f))

        Button(
            onClick  = onAcknowledge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .height(52.dp),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text("Iniciar Leitura", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun InstructionTips() {
    val tips = listOf(
        Triple(Icons.Default.LightMode, "Iluminação",    "Ambiente bem iluminado, sem sombras"),
        Triple(Icons.Default.CropFree,  "Enquadramento", "MRZ completamente dentro da área"),
        Triple(Icons.Default.BlurOff,   "Nitidez",       "Documento parado e câmara estável"),
    )
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tips.forEach { (icon, title, desc) ->
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier         = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(desc,  fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun AutomaticScannerContent(
    state        : State,
    viewModel    : DriverLicenseScreenViewModel,
    paddingValues: PaddingValues,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context        = LocalContext.current

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType          = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(Unit) {
        viewModel.setEvent(Event.InitializeScanner(lifecycleOwner, previewView))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopBar(title = "Digitalizar Carta", onClose = { viewModel.setEvent(Event.GoBack) })

            Spacer(Modifier.height(12.dp))

            ScanStatusBadge(state.scanState)

            Spacer(Modifier.weight(1f))

            ScannerWindow(previewView = previewView, scanState = state.scanState)

            Spacer(Modifier.weight(1f))

            BottomHint(state.scanState)

            Spacer(Modifier.height(20.dp))
        }

        // Cartão de resultado
        AnimatedVisibility(
            visible  = state.scannedDocument != null,
            enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        ) {
            state.scannedDocument?.let { doc ->
                DrivingLicenseResultCard(
                    document      = doc,
                    onConfirm     = { viewModel.setEvent(Event.ConfirmDocument) },
                    onScanAnother = { viewModel.setEvent(Event.RetryScanning) }
                )
            }
        }

        // Alerta de erro / segurança
        AnimatedVisibility(
            visible  = state.scanState is MrzScanState.Error || state.scanState is MrzScanState.SecurityCheckFailed,
            enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        ) {
            FloatingErrorAlert(
                scanState = state.scanState,
                onDismiss = { viewModel.setEvent(Event.RetryScanning) }
            )
        }
    }
}


@Composable
private fun ScanStatusBadge(scanState: MrzScanState) {
    val (text, color) = when (scanState) {
        is MrzScanState.Processing          -> "A extrair dados…"        to MaterialTheme.colorScheme.primary
        is MrzScanState.Success             -> "Documento lido!"         to MaterialTheme.colorScheme.success
        is MrzScanState.Error               -> "Erro de leitura"         to MaterialTheme.colorScheme.error
        is MrzScanState.SecurityCheckFailed -> "Verificação falhou"      to MaterialTheme.colorScheme.warning
        else                                -> "Pronto para digitalizar" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier              = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(modifier = Modifier.size(7.dp)) { drawCircle(color = color) }
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

// ─── Janela da câmara ────────────────────────────────────────────────────────

@Composable
private fun ScannerWindow(previewView: PreviewView, scanState: MrzScanState) {
    val borderColor = when (scanState) {
        is MrzScanState.Success             -> MaterialTheme.colorScheme.success
        is MrzScanState.Processing          -> MaterialTheme.colorScheme.primary
        is MrzScanState.Error               -> MaterialTheme.colorScheme.error
        is MrzScanState.SecurityCheckFailed -> MaterialTheme.colorScheme.warning
        else                                -> MaterialTheme.colorScheme.outline
    }

    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Etiqueta superior
        Row(
            modifier          = Modifier
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.CropFree, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Text("Zona MRZ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        }

        // Viewport da câmara — proporção ID-1 (85.60 × 53.98 mm)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .aspectRatio(1.58f)
                .clip(RoundedCornerShape(14.dp))
                .border(2.dp, borderColor, RoundedCornerShape(14.dp))
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // Vinheta lateral subtil
            Canvas(modifier = Modifier.fillMaxSize()) {
                val v = Color.Black.copy(alpha = 0.15f)
                drawRect(v, topLeft = Offset(0f, 0f),                  size = Size(size.width * 0.05f, size.height))
                drawRect(v, topLeft = Offset(size.width * 0.95f, 0f),  size = Size(size.width * 0.05f, size.height))
            }

            // Cantos de mira
            CornerMarkers(borderColor)

            // Overlay de sucesso
            if (scanState is MrzScanState.Success) {
                Box(
                    modifier         = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.success.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.success, modifier = Modifier.size(52.dp))
                }
            }

            // Barra de progresso
            if (scanState is MrzScanState.Processing) {
                LinearProgressIndicator(
                    progress   = scanState.confidence,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color      = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }


        Spacer(Modifier.height(8.dp))
        Text(
            text          = "Carta de Condução  •  ID-1  •  ISO/IEC 7810",
            fontSize      = 10.sp,
            color         = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontWeight    = FontWeight.Medium,
            textAlign     = TextAlign.Center,
            letterSpacing = 0.4.sp
        )
    }
}

@Composable
private fun CornerMarkers(color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val len = 20.dp.toPx(); val stroke = 3.dp.toPx(); val pad = 8.dp.toPx()
        val w = size.width;     val h = size.height
        drawLine(color, Offset(pad, pad),           Offset(pad + len, pad),     stroke)
        drawLine(color, Offset(pad, pad),           Offset(pad, pad + len),     stroke)
        drawLine(color, Offset(w - pad - len, pad), Offset(w - pad, pad),       stroke)
        drawLine(color, Offset(w - pad, pad),       Offset(w - pad, pad + len), stroke)
        drawLine(color, Offset(pad, h - pad),       Offset(pad + len, h - pad), stroke)
        drawLine(color, Offset(pad, h - pad - len), Offset(pad, h - pad),       stroke)
        drawLine(color, Offset(w - pad - len, h - pad), Offset(w - pad, h - pad),       stroke)
        drawLine(color, Offset(w - pad, h - pad - len), Offset(w - pad, h - pad),       stroke)
    }
}


@Composable
private fun BottomHint(scanState: MrzScanState) {
    val hint = when (scanState) {
        is MrzScanState.Processing          -> "A processar… mantenha o documento firme"
        is MrzScanState.Success             -> "Leitura concluída com sucesso"
        is MrzScanState.Error               -> "Ajuste o documento e tente novamente"
        is MrzScanState.SecurityCheckFailed -> "Utilize o documento físico original"
        else                                -> "Alinhe a zona MRZ com a moldura acima"
    }
    Row(
        modifier              = Modifier
            .padding(horizontal = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
        Text(hint, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}


@Composable
private fun TopBar(title: String, onClose: () -> Unit) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, "Fechar", tint = MaterialTheme.colorScheme.onBackground)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.success, modifier = Modifier.size(10.dp))
                Text("Leitura Segura", fontSize = 10.sp, color = MaterialTheme.colorScheme.success, fontWeight = FontWeight.Medium)
            }
        }
        IconButton(onClick = { /* flash */ }) {
            Icon(Icons.Default.FlashOn, "Flash", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DrivingLicenseResultCard(
    document     : MrzDocument,
    onConfirm    : () -> Unit,
    onScanAnother: () -> Unit,
) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape     = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier         = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.success.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.success, modifier = Modifier.size(24.dp))
                }
                Column {
                    Text("Carta Detetada", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("MRZ lida com sucesso", fontSize = 12.sp, color = MaterialTheme.colorScheme.success)
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))

            DLField("NÚMERO DA CARTA", document.documentNumber)
            Spacer(Modifier.height(8.dp))

            if (document is MrzDocument.DrivingLicense) {
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { DLField("APELIDO", document.surname) }
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) { DLField("NOME", document.givenNames) }
                }
                Spacer(Modifier.height(8.dp))
                DLField("CATEGORIAS", document.licenseCategories)
            }

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick  = onScanAnother,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape    = RoundedCornerShape(8.dp)
                ) {
                    Text("Repetir", fontSize = 14.sp)
                }
                Button(
                    onClick  = onConfirm,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape    = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirmar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun DLField(label: String, value: String?) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text          = label,
            fontSize      = 9.sp,
            color         = MaterialTheme.colorScheme.primary,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text       = if (value.isNullOrBlank()) "—" else value,
            fontSize   = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FloatingErrorAlert(scanState: MrzScanState, onDismiss: () -> Unit) {
    val message = when (scanState) {
        is MrzScanState.SecurityCheckFailed -> translateSecurity(scanState)
        is MrzScanState.Error               -> scanState.message
        else                                -> "Please try again."
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Aviso de Leitura", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 14.sp)
                Text(message, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp, lineHeight = 18.sp)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
            Spacer(Modifier.height(14.dp))
            Text("Loading…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
