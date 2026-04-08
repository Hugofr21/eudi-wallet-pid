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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import eu.europa.ec.mrzscannerLogic.model.ScanType
import eu.europa.ec.uilogic.extension.openAppSettings

private val ColorSuccess = Color(0xFF10B981)
private val ColorWarning = Color(0xFFF59E0B)
private val ColorError   = Color(0xFFEF4444)

@Composable
fun IdentificationDocumentScreen(
    navHostController: NavController,
    viewModel: IdentificationDocumentViewModel,
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
                is Effect.Navigation.SwitchScreen  -> navHostController.navigate(effect.screenRoute) {
                    popUpTo(effect.popUpToScreenRoute) { inclusive = effect.inclusive }
                }
                else -> {}
            }
        }
    }

    Scaffold(
        modifier       = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when (state.isCameraAvailability) {
            CameraAvailability.NO_PERMISSION -> RequiredPermissionsAsk { viewModel.setEvent(it) }
            CameraAvailability.AVAILABLE     -> AutomaticScannerContent(state, viewModel, padding)
            else                             -> LoadingScreen()
        }
    }
}

// ─── Conteúdo principal ───────────────────────────────────────────────────────

@Composable
private fun AutomaticScannerContent(
    state        : State,
    viewModel    : IdentificationDocumentViewModel,
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
        viewModel.setEvent(
            Event.InitializeScanner(
                lifecycleOwner = lifecycleOwner,
                previewView    = previewView,
                scanType       = ScanType.Document
            )
        )
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
            TopBar(
                isFlashOn     = state.isFlashOn,
                onClose       = { viewModel.setEvent(Event.GoBack) },
                onToggleFlash = { viewModel.setEvent(Event.ToggleFlash) }
            )

            Spacer(Modifier.height(12.dp))

            ScanStatusBadge(state.scanState)

            Spacer(Modifier.weight(1f))

            PassportScannerWindow(previewView = previewView, scanState = state.scanState)

            Spacer(Modifier.weight(1f))

            BottomHint(state.scanState)

            Spacer(Modifier.height(20.dp))
        }

        // Alerta de erro / segurança
        AnimatedVisibility(
            visible  = !state.isScanFrozen.not() &&
                    state.scannedDocument == null &&
                    (state.scanState is MrzScanState.Error || state.scanState is MrzScanState.SecurityCheckFailed),
            enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            FloatingErrorAlert(
                scanState = state.scanState,
                onDismiss = { viewModel.setEvent(Event.RetryScanning) }
            )
        }

        // Cartão de resultado
        AnimatedVisibility(
            visible  = state.scannedDocument != null,
            enter    = slideInVertically(
                initialOffsetY = { it },
                animationSpec  = tween(350, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(200)),
            exit     = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            state.scannedDocument?.let { doc ->
                AutomaticResultCard(
                    document      = doc,
                    onConfirm     = { viewModel.setEvent(Event.ConfirmDocument) },
                    onScanAnother = { viewModel.setEvent(Event.RetryScanning) }
                )
            }
        }
    }
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    isFlashOn    : Boolean,
    onClose      : () -> Unit,
    onToggleFlash: () -> Unit,
) {
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
            Text(
                text       = "Leitor de Passaporte",
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp,
                color      = MaterialTheme.colorScheme.onBackground
            )
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(Icons.Default.Lock, null, tint = ColorSuccess, modifier = Modifier.size(10.dp))
                Text("Leitura Segura", fontSize = 10.sp, color = ColorSuccess, fontWeight = FontWeight.Medium)
            }
        }
        IconButton(onClick = onToggleFlash) {
            Icon(
                imageVector        = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = if (isFlashOn) "Flash On" else "Flash Off",
                tint               = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Badge de estado ─────────────────────────────────────────────────────────

@Composable
private fun ScanStatusBadge(scanState: MrzScanState) {
    val (text, color) = when (scanState) {
        is MrzScanState.Processing          -> "A extrair dados…"        to MaterialTheme.colorScheme.primary
        is MrzScanState.Success             -> "Documento lido!"         to ColorSuccess
        is MrzScanState.Error               -> "Erro de leitura"         to ColorError
        is MrzScanState.SecurityCheckFailed -> "Verificação falhou"      to ColorWarning
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

// ─── Janela da câmara (proporção Passaporte) ──────────────────────────────────

@Composable
private fun PassportScannerWindow(previewView: PreviewView, scanState: MrzScanState) {
    val borderColor = when (scanState) {
        is MrzScanState.Success             -> ColorSuccess
        is MrzScanState.Processing          -> MaterialTheme.colorScheme.primary
        is MrzScanState.Error               -> ColorError
        is MrzScanState.SecurityCheckFailed -> ColorWarning
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

        // Viewport da câmara — proporção passaporte (ID-3: 125×88mm ≈ 1.42)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .aspectRatio(1.42f)
                .clip(RoundedCornerShape(14.dp))
                .border(2.dp, borderColor, RoundedCornerShape(14.dp))
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // Cantos de mira
            CornerMarkers(borderColor)

            // Overlay de sucesso
            if (scanState is MrzScanState.Success) {
                Box(
                    modifier         = Modifier
                        .fillMaxSize()
                        .background(ColorSuccess.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = ColorSuccess, modifier = Modifier.size(52.dp))
                }
            }

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

        // Etiqueta inferior
        Spacer(Modifier.height(8.dp))
        Text(
            text          = "Passaporte  •  ID-3  •  ICAO Doc 9303",
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
private fun AutomaticResultCard(
    document     : MrzDocument,
    onConfirm    : () -> Unit,
    onScanAnother: () -> Unit,
) {
    val docTypeLabel = when (document) {
        is MrzDocument.Passport       -> "Passaporte"
        is MrzDocument.IdCard         -> "Cartão de Identidade"
        is MrzDocument.DrivingLicense -> "Carta de Condução"
    }

    Card(
        modifier  = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape     = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Cabeçalho
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier         = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(ColorSuccess.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = ColorSuccess, modifier = Modifier.size(24.dp))
                }
                Column {
                    Text(docTypeLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Detetado automaticamente", fontSize = 12.sp, color = ColorSuccess)
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))

            // Campos do documento
            when (document) {
                is MrzDocument.Passport -> {
                    DocField("NOME COMPLETO",  "${document.givenNames} ${document.surname}")
                    DocField("Nº DOCUMENTO",   document.documentNumber)
                    DocField("PAÍS EMISSOR",   document.issuingCountry)
                    DocField("NACIONALIDADE",  document.nationality)
                    if (document.personalNumber.isNotBlank())
                        DocField("Nº PESSOAL", document.personalNumber)
                    DocField("DATA NASC.",     document.dateOfBirth)
                    document.expiryDate?.let { DocField("VALIDADE", it) }
                    DocField("SEXO",           document.sex)
                }
                is MrzDocument.IdCard -> {
                    DocField("NOME COMPLETO",  "${document.givenNames} ${document.surname}")
                    DocField("Nº DOCUMENTO",   document.documentNumber)
                    DocField("NACIONALIDADE",  document.nationality)
                    DocField("DATA NASC.",     document.dateOfBirth)
                    document.expiryDate?.let { DocField("VALIDADE", it) }
                    DocField("SEXO",           document.sex)
                }
                is MrzDocument.DrivingLicense -> {
                    DocField("NOME COMPLETO",  "${document.givenNames} ${document.surname}")
                    DocField("Nº CARTA",       document.documentNumber)
                    if (document.licenseCategories.isNotBlank())
                        DocField("CATEGORIAS", document.licenseCategories)
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick  = onScanAnother,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape    = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Repetir", fontSize = 14.sp)
                }
                Button(
                    onClick  = onConfirm,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape    = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Confirmar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun DocField(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text      = label,
            fontSize  = 9.sp,
            color     = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier  = Modifier.weight(0.4f)
        )
        Text(
            text       = value,
            fontSize   = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurface,
            modifier   = Modifier.weight(0.6f),
            textAlign  = TextAlign.End
        )
    }
}

@Composable
private fun FloatingErrorAlert(scanState: MrzScanState, onDismiss: () -> Unit) {
    val message = when (scanState) {
        is MrzScanState.SecurityCheckFailed -> translateSecurity(scanState)
        is MrzScanState.Error               -> scanState.message
        else                                -> "Por favor, tente novamente."
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
                Text("Erro na leitura", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 14.sp)
                Text(message, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp, lineHeight = 18.sp)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun LoadingScreen(message: String = "A carregar…") {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
            Spacer(Modifier.height(14.dp))
            Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Permissões ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RequiredPermissionsAsk(onEventSend: (Event) -> Unit) {
    val showDenied    = remember { mutableStateOf(false) }
    val permsState    = rememberMultiplePermissionsState(listOf(Manifest.permission.CAMERA)) { results ->
        if (results.values.all { it }) onEventSend(Event.OnPermissionStateChanged(CameraAvailability.AVAILABLE))
        else showDenied.value = true
    }

    LaunchedEffect(Unit) {
        if (!permsState.allPermissionsGranted) permsState.launchMultiplePermissionRequest()
        else onEventSend(Event.OnPermissionStateChanged(CameraAvailability.AVAILABLE))
    }

    if (showDenied.value) {
        PermissionDeniedMessage { onEventSend(Event.OpenAppSettings) }
    } else {
        LoadingScreen("A solicitar permissão…")
    }
}

@Composable
private fun PermissionDeniedMessage(onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(24.dp)) {
            Column(
                modifier            = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.NoPhotography, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Text("Permissão negada", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("O acesso à câmara é necessário para digitalizar documentos.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir Definições")
                }
            }
        }
    }
}