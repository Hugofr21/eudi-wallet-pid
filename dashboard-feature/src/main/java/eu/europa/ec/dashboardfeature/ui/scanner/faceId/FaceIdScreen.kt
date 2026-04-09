package eu.europa.ec.dashboardfeature.ui.scanner.faceId

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.mrzscannerLogic.controller.MrzScanState
import eu.europa.ec.mrzscannerLogic.model.ScanType
import eu.europa.ec.resourceslogic.theme.values.success

@Composable
fun FaceIdScreen(
    navHostController: NavController,
    viewModel: FaceIdScreenViewModel,
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state          by viewModel.viewState.collectAsStateWithLifecycle()

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType          = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(Unit) {
        if (state.scanState !is MrzScanState.Success) {
            viewModel.setEvent(
                Event.InitializeScanner(
                    lifecycleOwner = lifecycleOwner,
                    previewView    = previewView,
                    scanType       = ScanType.Face
                )
            )
        }
    }

    Scaffold(
        modifier       = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        FaceIdContent(
            state        = state,
            previewView  = previewView,
            padding      = padding,
            onClose      = { viewModel.setEvent(Event.GoBack) },
            onRetry      = { viewModel.setEvent(Event.RetryScanning) },
            onConfirm    = { viewModel.setEvent(Event.ConfirmDocument) },
        )
    }
}

@Composable
private fun FaceIdContent(
    state      : State,
    previewView: PreviewView,
    padding    : PaddingValues,
    onClose    : () -> Unit,
    onRetry    : () -> Unit,
    onConfirm  : () -> Unit,
) {
    val capturedBitmap = (state.scanState as? MrzScanState.Success)?.capturedImage

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            FaceTopBar(onClose = onClose)

            Spacer(Modifier.height(12.dp))


            FaceScanStatusBadge(scanState = state.scanState)

            Spacer(Modifier.weight(1f))


            FaceScannerWindow(
                previewView    = previewView,
                scanState      = state.scanState,
                capturedBitmap = capturedBitmap,
            )

            Spacer(Modifier.weight(1f))


            FaceBottomHint(scanState = state.scanState)

            Spacer(Modifier.height(20.dp))
        }


        AnimatedVisibility(
            visible  = capturedBitmap != null,
            enter    = slideInVertically(
                initialOffsetY = { it },
                animationSpec  = tween(350, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(200)),
            exit     = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            capturedBitmap?.let { bmp ->
                FaceResultCard(
                    bitmap    = bmp,
                    onConfirm = onConfirm,
                    onRetry   = onRetry,
                )
            }
        }

        if (state.isLoading) {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color       = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}


@Composable
private fun FaceTopBar(onClose: () -> Unit) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onBackground)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = "Face Verification",
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp,
                color      = MaterialTheme.colorScheme.onBackground
            )
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.success, modifier = Modifier.size(10.dp))
                Text("Secure Biometrics", fontSize = 10.sp, color = MaterialTheme.colorScheme.success, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun FaceScanStatusBadge(scanState: MrzScanState) {
    val (text, color) = when (scanState) {
        is MrzScanState.Processing -> "Detecting face…" to MaterialTheme.colorScheme.primary
        is MrzScanState.Success -> "Face captured!" to MaterialTheme.colorScheme.success
        is MrzScanState.Error -> "Capture error" to MaterialTheme.colorScheme.error
        else -> "Ready to scan" to MaterialTheme.colorScheme.onSurfaceVariant

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


@Composable
private fun FaceScannerWindow(
    previewView   : PreviewView,
    scanState     : MrzScanState,
    capturedBitmap: Bitmap?,
) {
    val borderColor = when (scanState) {
        is MrzScanState.Success    -> MaterialTheme.colorScheme.success
        is MrzScanState.Processing -> MaterialTheme.colorScheme.primary
        is MrzScanState.Error      -> MaterialTheme.colorScheme.error
        else                       -> MaterialTheme.colorScheme.outline
    }

    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row(
            modifier          = Modifier
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = null,
                tint        = MaterialTheme.colorScheme.primary,
                modifier    = Modifier.size(14.dp)
            )
            Text(
                "Zona Facial",
                fontSize   = 11.sp,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 60.dp)
                .aspectRatio(35f / 45f)
                .clip(RoundedCornerShape(14.dp))
                .border(2.dp, borderColor, RoundedCornerShape(14.dp))
        ) {
            if (capturedBitmap != null) {
                Image(
                    bitmap             = capturedBitmap.asImageBitmap(),
                    contentDescription = "Face captured",
                    modifier           = Modifier.fillMaxSize(),
                    contentScale       = ContentScale.Crop
                )
            } else {

                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

                if (scanState is MrzScanState.Scanning || scanState is MrzScanState.Processing) {
                    ScanLine()
                }
            }

            FaceCornerMarkers(borderColor)

            if (scanState is MrzScanState.Success && capturedBitmap == null) {
                Box(
                    modifier         = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.success.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint     = MaterialTheme.colorScheme.success,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }

            if (scanState is MrzScanState.Processing) {
                LinearProgressIndicator(
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
            text          = "Passport Photo • ICAO • 35×45 mm",
            fontSize      = 10.sp,
            color         = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontWeight    = FontWeight.Medium,
            textAlign     = TextAlign.Center,
            letterSpacing = 0.4.sp
        )
    }
}

@Composable
private fun ScanLine() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanLine")
    val yFraction by infiniteTransition.animateFloat(
        initialValue   = 0.08f,
        targetValue    = 0.92f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLineFraction"
    )

    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val y = size.height * yFraction
        drawLine(
            color       = lineColor.copy(alpha = 0.65f),
            start       = Offset(12f, y),
            end         = Offset(size.width - 12f, y),
            strokeWidth = 2f
        )

        drawLine(
            color       = lineColor.copy(alpha = 0.15f),
            start       = Offset(12f, y),
            end         = Offset(size.width - 12f, y),
            strokeWidth = 8f
        )
    }
}


@Composable
private fun FaceCornerMarkers(color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val len    = 20.dp.toPx()
        val stroke = 3.dp.toPx()
        val pad    = 8.dp.toPx()
        val w = size.width
        val h = size.height

        // Canto sup-esq
        drawLine(color, Offset(pad, pad),           Offset(pad + len, pad),     stroke)
        drawLine(color, Offset(pad, pad),           Offset(pad, pad + len),     stroke)
        // Canto sup-dir
        drawLine(color, Offset(w - pad - len, pad), Offset(w - pad, pad),       stroke)
        drawLine(color, Offset(w - pad, pad),       Offset(w - pad, pad + len), stroke)
        // Canto inf-esq
        drawLine(color, Offset(pad, h - pad),       Offset(pad + len, h - pad), stroke)
        drawLine(color, Offset(pad, h - pad - len), Offset(pad, h - pad),       stroke)
        // Canto inf-dir
        drawLine(color, Offset(w - pad - len, h - pad), Offset(w - pad, h - pad),       stroke)
        drawLine(color, Offset(w - pad, h - pad - len), Offset(w - pad, h - pad),       stroke)
    }
}


@Composable
private fun FaceBottomHint(scanState: MrzScanState) {
    val hint = when (scanState) {
        is MrzScanState.Processing -> "Processing… please remain still"
        is MrzScanState.Success -> "Face captured successfully"
        is MrzScanState.Error -> "Adjust the position and try again"
        else -> "Center the face in the frame above"

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
        Icon(
            Icons.Default.Info,
            null,
            tint     = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp)
        )
        Text(
            hint,
            fontSize  = 13.sp,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FaceResultCard(
    bitmap   : Bitmap,
    onConfirm: () -> Unit,
    onRetry  : () -> Unit,
) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape     = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.success.copy(alpha = 0.4f), CircleShape)
                ) {
                    Image(
                        bitmap             = bitmap.asImageBitmap(),
                        contentDescription = "Selfie captured",
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop
                    )
                }

                Column {
                    Text(
                        "Face Verification",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint     = MaterialTheme.colorScheme.success,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            "Face detected automatically",
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.success
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))

            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.PhotoCamera,
                    null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        "Pass photo generated",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Back cover removed • ICAO format • 35×45 mm",
                        fontSize = 11.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick  = onRetry,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape    = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Repeat", fontSize = 14.sp)
                }
                Button(
                    onClick  = onConfirm,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape    = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Confirm", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}