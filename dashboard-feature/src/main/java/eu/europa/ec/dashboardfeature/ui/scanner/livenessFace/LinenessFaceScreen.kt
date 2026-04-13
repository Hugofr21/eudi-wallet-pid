package eu.europa.ec.dashboardfeature.ui.scanner.livenessFace

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.mrzscannerLogic.controller.FaceFeatures
import eu.europa.ec.mrzscannerLogic.model.ScanType
import eu.europa.ec.mrzscannerLogic.service.FaceVerificationResult
import eu.europa.ec.uilogic.component.utils.VSpacer

private val ColorSuccess = Color(0xFF10B981)
private val ColorWarning = Color(0xFFF59E0B)
private val ColorError   = Color(0xFFEF4444)

@Composable
fun LivenessFaceScreen(
    navHostController: NavController,
    viewModel: LivenessFaceViewModel,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.viewState.collectAsStateWithLifecycle()

    val entryReset = remember { mutableStateOf(false) }
    LaunchedEffect(entryReset.value) {
        if (!entryReset.value) {
            viewModel.setEvent(Event.ResetScreenState)
            entryReset.value = true
        }
    }

    Scaffold(
        modifier       = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !state.isInstructionAcknowledged -> {
                    FaceIdInstructionsPanel(
                        onProceed = { viewModel.setEvent(Event.AcknowledgeInstructions) }
                    )
                }

                state.isSessionComplete -> {
                    val vr = state.showVerificationLabel

                    if (state.capturedBitmap != null) {
                        FaceIdConfirmationPanel(
                            capturedBitmap     = state.capturedBitmap!!,
                            verificationResult = vr,
                            verifiedPersonName = state.verifiedPersonName,
                            onRetry            = { viewModel.setEvent(Event.RetryScanning) },
                            onConfirm          = { viewModel.setEvent(Event.ConfirmDocument) }
                        )
                    } else {
                        FaceIdCaptureErrorPanel(
                            onRetry = { viewModel.setEvent(Event.RetryScanning) }
                        )
                    }

                    AnimatedVisibility(
                        visible  = vr != null && !vr.isVerified,
                        enter    = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec  = tween(350, easing = FastOutSlowInEasing)
                        ) + fadeIn(tween(200)),
                        exit     = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        vr?.let {
                            FaceIdVerificationFailedCard(
                                score   = (it.similarityScore * 100).toInt(),
                                onRetry = { viewModel.setEvent(Event.RetryScanning) }
                            )
                        }
                    }
                }

                else -> {
                    FaceIdScanningPanel(
                        viewModel      = viewModel,
                        lifecycleOwner = lifecycleOwner,
                        state          = state
                    )
                }
            }

            if (state.isLoading) {
                Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color       = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Processing…",
                            fontSize = 14.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            state.errorMessage?.let { msg ->
                AnimatedVisibility(
                    visible  = true,
                    enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit     = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = RectangleShape,
                        colors    = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        elevation = CardDefaults.cardElevation(6.dp)
                    ) {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint     = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            VSpacer.Small()
                            Text(
                                text     = msg,
                                color    = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaceIdScanningPanel(
    viewModel: LivenessFaceViewModel,
    lifecycleOwner: LifecycleOwner,
    state: State
) {
    DisposableEffect(lifecycleOwner) {
        onDispose { viewModel.setEvent(Event.StopScanning) }
    }

    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FaceTopBar(onClose = { viewModel.setEvent(Event.StopScanning) })

        VSpacer.Small()

        FaceScanStatusBadge(
            completedCount = state.completedChallenges.size,
            isCountingDown = state.countdownSeconds != null
        )

        Spacer(Modifier.weight(1f))

        FaceCameraWindow(
            viewModel      = viewModel,
            lifecycleOwner = lifecycleOwner,
            state          = state
        )

        Spacer(Modifier.weight(1f))

        FaceBottomHint(
            challengeMessage    = state.currentChallengeMessage,
            completedChallenges = state.completedChallenges
        )

        Spacer(Modifier.height(20.dp))
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
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = "Biometric Verification",
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp,
            )
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint     = ColorSuccess,
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    "Liveness check",
                    fontSize   = 10.sp,
                    color      = ColorSuccess,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun FaceScanStatusBadge(completedCount: Int, isCountingDown: Boolean) {
    val (text, color) = when {
        isCountingDown     -> "Capturing…"            to MaterialTheme.colorScheme.primary
        completedCount > 0 -> "Challenge $completedCount done" to ColorSuccess
        else               -> "Ready to scan"         to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier              = Modifier
            .clip(RoundedCornerShape(20.dp))
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
private fun FaceCameraWindow(
    viewModel: LivenessFaceViewModel,
    lifecycleOwner: LifecycleOwner,
    state: State
) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier              = Modifier
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.Face,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                "Frontal Camera",
                fontSize   = 11.sp,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
        ) {
            AndroidView(
                factory = { context ->
                    PreviewView(context).apply {
                        scaleType          = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }.also { pv ->
                        viewModel.setEvent(
                            Event.InitializeScanner(
                                lifecycleOwner = lifecycleOwner,
                                previewView    = pv,
                                scanType       = ScanType.Face
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            state.faceFeatures?.let { FaceContoursOverlay(features = it) }

            FaceCircleOverlay()

            state.countdownSeconds?.let { CountdownOverlay(seconds = it) }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text          = "ISO/IEC 30107-3 · Liveness · PAD Level 1",
            fontSize      = 10.sp,
            color         = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontWeight    = FontWeight.Medium,
            textAlign     = TextAlign.Center,
            letterSpacing = 0.4.sp
        )
    }
}

@Composable
private fun FaceBottomHint(challengeMessage: String, completedChallenges: List<String>) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.RemoveRedEye,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text       = challengeMessage,
                fontSize   = 13.sp,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                textAlign  = TextAlign.Center
            )
        }

        if (completedChallenges.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            completedChallenges.forEach { label ->
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint     = ColorSuccess,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FaceIdInstructionsPanel(onProceed: () -> Unit) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier         = Modifier
                .size(72.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Face,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text       = "Biometric Verification",
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = "Perform a few simple gestures in front of the front camera to confirm your identity.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(bottom = 32.dp)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(24.dp))

        InstructionRow(Icons.Default.LightMode,    "Good lighting — avoid bright light behind you.")
        Spacer(Modifier.height(16.dp))
        InstructionRow(Icons.Default.Face,         "Remove sunglasses or hats.")
        Spacer(Modifier.height(16.dp))
        InstructionRow(Icons.Default.RemoveRedEye, "Follow on-screen instructions: blink, smile, turn…")

        Spacer(Modifier.height(40.dp))

        Button(
            onClick  = onProceed,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RectangleShape
        ) {
            Text("Start Scan", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InstructionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier          = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint     = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(24.dp)
                .padding(top = 2.dp)
        )
        Text(
            text     = text,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun FaceIdCaptureErrorPanel(onRetry: () -> Unit) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier         = Modifier
                .size(80.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint     = ColorError,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text       = "Image could not be captured",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = "Ensure your face is well lit and centred inside the circle, then try again.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(bottom = 40.dp)
        )
        Button(
            onClick  = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColorError),
            shape  = RectangleShape
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Try Again", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FaceIdConfirmationPanel(
    capturedBitmap: Bitmap,
    verificationResult: FaceVerificationResult?,
    verifiedPersonName: String?,
    onRetry: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.70f)
        ) {
            Image(
                bitmap             = capturedBitmap.asImageBitmap(),
                contentDescription = "Captured face",
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )

            verificationResult?.let { vr ->
                val isMatch    = vr.isVerified
                val pct        = (vr.similarityScore * 100).toInt()
                val badgeColor = if (isMatch) ColorSuccess else ColorError
                val mainLabel  = when {
                    isMatch && !verifiedPersonName.isNullOrBlank() -> verifiedPersonName
                    isMatch -> "Identity confirmed"
                    else    -> "Unrecognised"
                }
                val subLabel = if (isMatch) "Similarity · $pct%" else "$pct% similarity"

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(badgeColor.copy(alpha = 0.88f))
                        .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Canvas(Modifier.size(7.dp)) { drawCircle(color = Color.White) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(mainLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(subLabel, color = Color.White.copy(alpha = 0.80f), fontSize = 11.sp)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint     = ColorSuccess,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Liveness confirmed",
                        style      = MaterialTheme.typography.bodyMedium,
                        color      = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .weight(0.30f),
            shape     = RectangleShape,
            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
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
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint     = ColorSuccess,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            "Face captured",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                        Text("Automatically detected", fontSize = 12.sp, color = ColorSuccess)
                    }
                }

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick  = onRetry,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RectangleShape
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Repeat", fontSize = 14.sp)
                    }
                    Button(
                        onClick  = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RectangleShape
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Confirm", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FaceIdVerificationFailedCard(score: Int, onRetry: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RectangleShape,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier         = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(ColorError.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$score%",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color      = ColorError
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Identity not confirmed",
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize   = 14.sp
                    )
                    Text(
                        "Match score $score% — minimum required is 85%. Ensure the same person is present and retry.",
                        color      = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize   = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick  = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorError),
                shape  = RectangleShape
            ) {
                Text("Repeat check", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FaceContoursOverlay(
    modifier: Modifier = Modifier,
    features: FaceFeatures
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val vw = size.width
        val vh = size.height
        val iw = features.imageWidth.toFloat()
        val ih = features.imageHeight.toFloat()
        if (iw <= 0 || ih <= 0) return@Canvas

        val scale   = maxOf(vw / iw, vh / ih)
        val scaledW = iw * scale
        val scaledH = ih * scale
        val ox      = (vw - scaledW) / 2f
        val oy      = (vh - scaledH) / 2f

        fun mapX(x: Float) = vw - (x * scale + ox)
        fun mapY(y: Float) = y * scale + oy

        fun drawContour(pts: List<android.graphics.PointF>, color: Color, width: Float, close: Boolean = false) {
            if (pts.size < 2) return
            val path = Path().apply {
                moveTo(mapX(pts[0].x), mapY(pts[0].y))
                for (i in 1 until pts.size) lineTo(mapX(pts[i].x), mapY(pts[i].y))
                if (close) close()
            }
            drawPath(
                path  = path,
                color = color,
                style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        drawContour(features.faceOval,          Color(0xFF1565C0),                3.dp.toPx(),    close = true)
        drawContour(features.leftEye,            ColorSuccess.copy(alpha = 0.9f), 2.5f.dp.toPx(), close = true)
        drawContour(features.rightEye,           ColorSuccess.copy(alpha = 0.9f), 2.5f.dp.toPx(), close = true)
        drawContour(features.leftEyebrowTop,     Color.White.copy(alpha = 0.85f), 2.dp.toPx())
        drawContour(features.leftEyebrowBottom,  Color.White.copy(alpha = 0.85f), 2.dp.toPx())
        drawContour(features.rightEyebrowTop,    Color.White.copy(alpha = 0.85f), 2.dp.toPx())
        drawContour(features.rightEyebrowBottom, Color.White.copy(alpha = 0.85f), 2.dp.toPx())
        drawContour(features.noseBridge,         Color.White.copy(alpha = 0.75f), 2.dp.toPx())
        drawContour(features.noseBottom,         Color.White.copy(alpha = 0.75f), 2.dp.toPx())
        drawContour(features.upperLipTop,        ColorWarning.copy(alpha = 0.9f), 2.dp.toPx())
        drawContour(features.upperLipBottom,     ColorWarning.copy(alpha = 0.9f), 2.dp.toPx())
        drawContour(features.lowerLipTop,        ColorWarning.copy(alpha = 0.9f), 2.dp.toPx())
        drawContour(features.lowerLipBottom,     ColorWarning.copy(alpha = 0.9f), 2.dp.toPx())
    }
}

@Composable
fun FaceCircleOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val diameter = size.width * 0.76f
        val cx = size.width  / 2f
        val cy = size.height / 2f
        val r  = diameter    / 2f

        val path = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
            addOval(Rect(cx - r, cy - r, cx + r, cy + r))
            fillType = PathFillType.EvenOdd
        }
        drawPath(path = path, color = Color.Black.copy(alpha = 0.55f))

        drawCircle(
            color  = Color.White.copy(alpha = 0.15f),
            radius = r + 7.dp.toPx(),
            center = Offset(cx, cy),
            style  = Stroke(width = 7.dp.toPx())
        )
        drawCircle(
            color  = Color(0xFF1565C0),
            radius = r,
            center = Offset(cx, cy),
            style  = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
private fun CountdownOverlay(seconds: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier         = Modifier
                .size(96.dp)
                .background(Color.Black.copy(alpha = 0.60f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = seconds,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 1.5f))
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "countdown"
            ) { sec ->
                Text(
                    text       = "$sec",
                    fontSize   = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}