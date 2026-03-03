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
import eu.europa.ec.uilogic.extension.openAppSettings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import eu.europa.ec.mrzscannerLogic.model.ScanType
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.android.material.color.utilities.Score.score
import eu.europa.ec.mrzscannerLogic.model.AntiSpoofingCheck
import eu.europa.ec.mrzscannerLogic.model.MrzDocument
import eu.europa.ec.uilogic.component.utils.VSpacer


@Composable
fun IdentificationDocumentScreen(
    navHostController: NavController,
    viewModel: IdentificationDocumentViewModel,
) {
    val context = LocalContext.current
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val effects = viewModel.effect
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
                is Effect.Navigation.Pop -> navHostController.popBackStack()
                is Effect.Navigation.OnAppSettings -> context.openAppSettings()
                is Effect.Navigation.SwitchScreen -> navHostController.navigate(effect.screenRoute) {
                    popUpTo(effect.popUpToScreenRoute) { inclusive = effect.inclusive }
                }
                else -> {}
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { paddingValues ->
        when (state.isCameraAvailability) {
            CameraAvailability.NO_PERMISSION -> RequiredPermissionsAsk { viewModel.setEvent(it) }
            CameraAvailability.AVAILABLE -> AutomaticScannerContent(state, viewModel, paddingValues)
            else -> LoadingScreen()
        }
    }

}

@Composable
private fun AutomaticScannerContent(
    state: State,
    viewModel: IdentificationDocumentViewModel,
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
        viewModel.setEvent(
            Event.InitializeScanner(
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                scanType = ScanType.Document
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {

        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        TopBar(
            isFlashOn = state.isFlashOn,
            onClose = { viewModel.setEvent(Event.GoBack) },
            onToggleFlash = { viewModel.setEvent(Event.ToggleFlash) },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        AnimatedVisibility(
            visible = state.scannedDocument == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            MrzGuideOverlay(state.scanState)
        }



        AnimatedVisibility(
            visible = !state.isScanFrozen.not() &&
                    state.scannedDocument == null &&
                    (state.scanState is MrzScanState.Error ||
                            state.scanState is MrzScanState.SecurityCheckFailed),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit  = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            FloatingErrorAlert(
                scanState = state.scanState,
                onDismiss = { viewModel.setEvent(Event.RetryScanning) }
            )
        }

        AnimatedVisibility(
            visible = state.scannedDocument != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            state.scannedDocument?.let { doc ->
                AutomaticResultCard(
                    document = doc,
                    onConfirm = { viewModel.setEvent(Event.ConfirmDocument) },
                    onScanAnother = { viewModel.setEvent(Event.RetryScanning) }
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    isFlashOn: Boolean,
    onClose: () -> Unit,
    onToggleFlash: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                )
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
        }
        Text(
            text = "Document Scanner",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White
        )
        IconButton(onClick = onToggleFlash) {
            Icon(
                imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = if (isFlashOn) "Flash On" else "Flash Off",
                tint = if (isFlashOn) Color(0xFFFFC107) else Color.White
            )
        }
    }
}


@Composable
private fun MrzGuideOverlay(scanState: MrzScanState) {
    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val cornerLen = 28.dp.toPx()
            val cornerStroke = 4.dp.toPx()
            val blue = Color(0xFF1976D2)

            drawRoundRect(
                color = Color.White.copy(alpha = 0.4f),
                size = size,
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(width = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f)))
            )
            // Cantos azuis
            listOf(
                Offset(0f, cornerLen) to Offset(0f, 0f),
                Offset(0f, 0f) to Offset(cornerLen, 0f),
                Offset(size.width - cornerLen, 0f) to Offset(size.width, 0f),
                Offset(size.width, 0f) to Offset(size.width, cornerLen),
                Offset(0f, size.height - cornerLen) to Offset(0f, size.height),
                Offset(0f, size.height) to Offset(cornerLen, size.height),
                Offset(size.width - cornerLen, size.height) to Offset(size.width, size.height),
                Offset(size.width, size.height) to Offset(size.width, size.height - cornerLen),
            ).forEach { (start, end) -> drawLine(blue, start, end, cornerStroke) }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.DocumentScanner, null, tint = Color.White,
                modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                text = when (scanState) {
                    is MrzScanState.Processing -> "Reading document… ${(scanState.confidence * 100).toInt()}%"
                    is MrzScanState.Scanning   -> "Align MRZ zone here"
                    else                       -> "Align MRZ zone here"
                },
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

// =============================================================================
// ★ AutomaticResultCard — sem shape/radius, cola ao fundo do ecrã
// =============================================================================

@Composable
private fun AutomaticResultCard(
    document: MrzDocument,
    onConfirm: () -> Unit,
    onScanAnother: () -> Unit
) {
    // ★ Surface sem shape → sem border radius → cola nas bordas laterais e inferior
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,          // sem radius
        color = Color.White,
        shadowElevation = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 32.dp)  // bottom padding extra para navbar
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when (document) {
                            is MrzDocument.Passport       -> "Passport"
                            is MrzDocument.IdCard         -> "Identity Card"
                            is MrzDocument.DrivingLicense -> "Driving Licence"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = when (document) {
                            is MrzDocument.Passport       -> "📘 Detected automatically"
                            is MrzDocument.IdCard         -> "🪪 Detected automatically"
                            is MrzDocument.DrivingLicense -> "🚗 Detected automatically"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
                Icon(
                    Icons.Default.CheckCircle, null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(32.dp)
                )
            }

            // ★ Divider sem padding lateral → vai de borda a borda
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                color = Color(0xFFE0E0E0),
                thickness = 1.dp
            )

            // ── Campos do documento ─────────────────────────────────────────
            when (document) {
                is MrzDocument.Passport -> {
                    DocumentField("Full Name", "${document.givenNames} ${document.surname}")
                    DocumentField("Document №", document.documentNumber)
                    DocumentField("Country", document.issuingCountry)
                    DocumentField("Nationality", document.nationality)
                    if (document.personalNumber.isNotBlank())
                        DocumentField("Personal №", document.personalNumber)
                    DocumentField("Date of Birth", document.dateOfBirth)
                    document.expiryDate?.let { DocumentField("Expiry", it) }
                    DocumentField("Sex", document.sex)
                }
                is MrzDocument.IdCard -> {
                    DocumentField("Full Name", "${document.givenNames} ${document.surname}")
                    DocumentField("Document №", document.documentNumber)
                    DocumentField("Nationality", document.nationality)
                    DocumentField("Date of Birth", document.dateOfBirth)
                    document.expiryDate?.let { DocumentField("Expiry", it) }
                    DocumentField("Sex", document.sex)
                }
                is MrzDocument.DrivingLicense -> {
                    DocumentField("Full Name", "${document.givenNames} ${document.surname}")
                    DocumentField("Licence №", document.documentNumber)
                    if (document.licenseCategories.isNotBlank())
                        DocumentField("Categories", document.licenseCategories)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Botões ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onScanAnother,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan Again")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Confirm")
                }
            }
        }
    }
}


@Composable
private fun DocumentField(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun FloatingErrorAlert(scanState: MrzScanState, onDismiss: () -> Unit) {
    val message = when (scanState) {
        is MrzScanState.SecurityCheckFailed -> translateSecurityToEnglish(scanState)
        is MrzScanState.Error -> scanState.message
        else -> "Please try again."
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF39C12),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Scan Error",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
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

private fun translateSecurityToEnglish(state: MrzScanState.SecurityCheckFailed): String {
    val scoreMatch = Regex("Score: ([0-9.]+)").find(state.reason)
    val scoreText = scoreMatch?.groups?.get(1)?.value ?: "0.0"
    if (state.failedChecks.isEmpty()) return "Security validation failed. Score: $scoreText"
    val specificReason = when (state.failedChecks.first()) {
        AntiSpoofingCheck.MOIRE_PATTERN        -> "Screen detected. Use the original physical document."
        AntiSpoofingCheck.SPECULAR_REFLECTION  -> "Paper detected. Avoid photocopies or reflections."
        AntiSpoofingCheck.GYROSCOPE            -> "Tilt the phone slightly to validate the hologram."
        AntiSpoofingCheck.ACCELEROMETER        -> "Hold the document steady. Avoid bending."
        AntiSpoofingCheck.IMAGE_QUALITY        -> "Improve environmental lighting."
        AntiSpoofingCheck.ARCORE_DEPTH         -> "Move camera closer to validate spatial depth."
        AntiSpoofingCheck.PRINT_ARTIFACT       -> "Printed copy detected. Use the original document."
        AntiSpoofingCheck.TEMPORAL_CONSISTENCY -> "Keep the document still while scanning."
        AntiSpoofingCheck.COLOR_CONSISTENCY    -> "Unusual color pattern detected. Use the original."
        AntiSpoofingCheck.EDGE_SHARPNESS       -> "Document edges not clear. Improve focus."
    }
    return "$specificReason\nSecurity Score: $scoreText"
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