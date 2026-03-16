package eu.europa.ec.dashboardfeature.ui.scanner.identificationDocument

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
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
import eu.europa.ec.mrzscannerLogic.controller.MrzScanState
import eu.europa.ec.mrzscannerLogic.model.AntiSpoofingCheck
import eu.europa.ec.mrzscannerLogic.model.MrzDocument
import eu.europa.ec.mrzscannerLogic.model.ScanType
import eu.europa.ec.uilogic.extension.openAppSettings

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
        containerColor = Color.White
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

        ScannerMaskOverlay(scanState = state.scanState)

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            TopBar(
                isFlashOn = state.isFlashOn,
                onClose = { viewModel.setEvent(Event.GoBack) },
                onToggleFlash = { viewModel.setEvent(Event.ToggleFlash) }
            )
            Spacer(modifier = Modifier.height(40.dp))
            InstructionText()
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
private fun ScannerMaskOverlay(scanState: MrzScanState) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val boxWidth = size.width - 48.dp.toPx()
            val boxHeight = 130.dp.toPx()
            val boxTopLeft = Offset(
                x = (size.width - boxWidth) / 2f,
                y = (size.height - boxHeight) / 2f
            )
            val cornerRadius = CornerRadius(8.dp.toPx())

            val maskPath = Path().apply {
                addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        rect = androidx.compose.ui.geometry.Rect(boxTopLeft, androidx.compose.ui.geometry.Size(boxWidth, boxHeight)),
                        cornerRadius = cornerRadius
                    )
                )
                fillType = PathFillType.EvenOdd
            }

            drawPath(path = maskPath, color = Color.White)

            val strokeWidth = 1.5.dp.toPx()
            val dashColor = Color(0xFF9E9E9E)

            drawRoundRect(
                color = dashColor,
                topLeft = boxTopLeft,
                size = androidx.compose.ui.geometry.Size(boxWidth, boxHeight),
                cornerRadius = cornerRadius,
                style = Stroke(
                    width = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f))
                )
            )

            val cornerLen = 20.dp.toPx()
            val cornerStroke = 4.dp.toPx()
            val blue = Color(0xFF1976D2)

            drawLine(blue, boxTopLeft, boxTopLeft.copy(x = boxTopLeft.x + cornerLen), cornerStroke)
            drawLine(blue, boxTopLeft, boxTopLeft.copy(y = boxTopLeft.y + cornerLen), cornerStroke)

            val topRight = boxTopLeft.copy(x = boxTopLeft.x + boxWidth)
            drawLine(blue, topRight, topRight.copy(x = topRight.x - cornerLen), cornerStroke)
            drawLine(blue, topRight, topRight.copy(y = topRight.y + cornerLen), cornerStroke)

            val bottomLeft = boxTopLeft.copy(y = boxTopLeft.y + boxHeight)
            drawLine(blue, bottomLeft, bottomLeft.copy(x = bottomLeft.x + cornerLen), cornerStroke)
            drawLine(blue, bottomLeft, bottomLeft.copy(y = bottomLeft.y - cornerLen), cornerStroke)

            val bottomRight = Offset(boxTopLeft.x + boxWidth, boxTopLeft.y + boxHeight)
            drawLine(blue, bottomRight, bottomRight.copy(x = bottomRight.x - cornerLen), cornerStroke)
            drawLine(blue, bottomRight, bottomRight.copy(y = bottomRight.y - cornerLen), cornerStroke)
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(70.dp))
            Icon(
                imageVector = Icons.Default.DocumentScanner,
                contentDescription = null,
                tint = Color(0xFF757575),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (scanState) {
                    is MrzScanState.Processing -> "Reading document… ${(scanState.confidence * 100).toInt()}%"
                    else -> "Aguardando leitura da MRZ"
                },
                color = Color(0xFF757575),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
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
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.Black)
        }
        Text(
            text = "Leitor de Passaporte",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.Black
        )
        IconButton(onClick = onToggleFlash) {
            Icon(
                imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = if (isFlashOn) "Flash On" else "Flash Off",
                tint = Color.Black
            )
        }
    }
}

@Composable
private fun InstructionText() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Posicione a Zona Legível por Máquina (MRZ) dentro da área",
            color = Color(0xFF333333),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Certifique-se de que o documento esteja bem iluminado e todo o texto da zona MRZ esteja visível para uma leitura precisa.",
            color = Color(0xFF666666),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun AutomaticResultCard(
    document: MrzDocument,
    onConfirm: () -> Unit,
    onScanAnother: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = Color.White,
        shadowElevation = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 32.dp)
        ) {
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
                            is MrzDocument.Passport       -> "Detected automatically"
                            is MrzDocument.IdCard         -> "Detected automatically"
                            is MrzDocument.DrivingLicense -> "Detected automatically"
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

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                color = Color(0xFFE0E0E0),
                thickness = 1.dp
            )

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
        else -> "Por favor, tente novamente."
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF39C12),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
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
                    text = "Erro na leitura",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
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
            CircularProgressIndicator(color = Color.Black)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = Color.Black)
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
                Text("Permission denied", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("Access to the camera is required.", textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Settings")
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
        LoadingScreen("Requesting permission...")
    }
}