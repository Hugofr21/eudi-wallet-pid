package eu.europa.ec.consentuser.ui.imageCapture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.mrzscannerLogic.controller.FaceFeatures
import eu.europa.ec.mrzscannerLogic.model.ScanType
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.TopStepBar
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@Composable
fun ImageCaptureScreen(navController: NavController, viewModel: ImageCaptureViewModel) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            when (effect) {
                is Effect.Navigation.SwitchScreen -> navController.navigate(effect.screenRoute)
                is Effect.Navigation.Pop          -> navController.popBackStack()
            }
        }.collect()
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!hasCameraPermission) {
            PermissionRequestPanel(onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        } else {
            when {
                !state.isInstructionAcknowledged -> {
                    CaptureInstructionsPanel(
                        onProceed = { viewModel.setEvent(Event.AcknowledgeInstructions) }
                    )
                }
                state.isSessionComplete -> {
                    if (state.capturedBitmap != null) {
                        CaptureConfirmationPanel(
                            capturedBitmap = state.capturedBitmap!!,
                            onRetry   = { viewModel.setEvent(Event.RetryCapture) },
                            onConfirm = { viewModel.setEvent(Event.GoNext) }
                        )
                    } else {
                        CaptureErrorPanel(
                            onRetry = { viewModel.setEvent(Event.RetryCapture) }
                        )
                    }
                }
                else -> {
                    LivenessCapturePanel(
                        viewModel = viewModel,
                        state     = state
                    )
                }
            }
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        }

        state.errorMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PermissionRequestPanel(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TopStepBar(currentStep = 4)
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Permission Required",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "To complete identity verification, it is strictly necessary to grant access to the device's camera.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(36.dp))
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "Grant Permission", fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CaptureInstructionsPanel(onProceed: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TopStepBar(currentStep = 4)
        Spacer(modifier = Modifier.height(32.dp))
        Icon(
            imageVector = Icons.Default.VideoCall,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.consent_screen_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.user_consent_step_2_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(36.dp))
        InstructionRow(Icons.Default.LightMode, stringResource(R.string.capture_instruction_light))
        Spacer(modifier = Modifier.height(16.dp))
        InstructionRow(Icons.Default.Face, stringResource(R.string.capture_instruction_face))
        Spacer(modifier = Modifier.height(16.dp))
        InstructionRow(Icons.Default.RemoveRedEye, stringResource(R.string.capture_instruction_follow))
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onProceed,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(R.string.consent_screen_confirm_button),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun InstructionRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp).padding(top = 2.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun LivenessCapturePanel(
    viewModel: ImageCaptureViewModel,
    state: State
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        onDispose { viewModel.setEvent(Event.StopCapture) }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TopStepBar(currentStep = 4)
        Box(modifier = Modifier.weight(0.65f)) {
            AndroidView(
                factory = { context ->
                    PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }.also { pv ->
                        viewModel.setEvent(
                            Event.InitializeCapture(
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
            ChallengeProgressBar(
                completed = state.completedChallenges.size,
                total     = state.totalChallenges,
                modifier  = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(0.35f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            LiveFacePreview(bitmap = state.previewBitmap)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.capture_current_challenge_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.currentChallengeMessage,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                if (state.completedChallenges.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    state.completedChallenges.forEach { label ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FaceCircleOverlay(modifier: Modifier = Modifier) {
    val scrimColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
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
        drawPath(path = path, color = scrimColor)
        drawCircle(
            color  = scrimColor.copy(alpha = 0.2f),
            radius = r + 7.dp.toPx(),
            center = Offset(cx, cy),
            style  = Stroke(width = 7.dp.toPx())
        )
        drawCircle(
            color  = primaryColor,
            radius = r,
            center = Offset(cx, cy),
            style  = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
private fun ChallengeProgressBar(
    completed: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val progress = if (total > 0) completed.toFloat() / total else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400),
        label = "challenge_progress"
    )
    Column(modifier = modifier) {
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$completed / $total",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@Composable
private fun LiveFacePreview(bitmap: Bitmap?) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Face preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(CircleShape)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun CountdownOverlay(seconds: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.60f), CircleShape),
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
                    text = "$sec",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun CaptureConfirmationPanel(
    capturedBitmap: Bitmap,
    onRetry: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopStepBar(currentStep = 4)
        Box(modifier = Modifier.fillMaxWidth().weight(0.70f)) {
            Image(
                bitmap = capturedBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.capture_liveness_confirmed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.30f)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.capture_photo_captured_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.capture_photo_captured_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor   = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.capture_retry_button), fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(2f).height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.consent_screen_confirm_button), fontWeight = FontWeight.Medium)
                }
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
        val scale = maxOf(vw / iw, vh / ih)
        val scaledW = iw * scale
        val scaledH = ih * scale
        val ox = (vw - scaledW) / 2f
        val oy = (vh - scaledH) / 2f
        fun mapX(x: Float) = vw - (x * scale + ox)
        fun mapY(y: Float) = y * scale + oy
        fun drawContour(
            pts: List<android.graphics.PointF>,
            color: Color,
            width: Float,
            close: Boolean = false
        ) {
            if (pts.size < 2) return
            val path = Path()
            path.moveTo(mapX(pts[0].x), mapY(pts[0].y))
            for (i in 1 until pts.size) path.lineTo(mapX(pts[i].x), mapY(pts[i].y))
            if (close) path.close()
            drawPath(
                path  = path,
                color = color,
                style = Stroke(
                    width     = width,
                    cap       = StrokeCap.Round,
                    join      = StrokeJoin.Round
                )
            )
        }
        val faceColor  = Color(0xFF1565C0)
        val eyeColor   = Color(0xFF00BCD4)
        val browColor  = Color.White.copy(alpha = 0.85f)
        val noseColor  = Color.White.copy(alpha = 0.75f)
        val lipColor   = Color(0xFFFF7043)

        drawContour(features.faceOval,          faceColor, 3.dp.toPx(), close = true)
        drawContour(features.leftEye,            eyeColor,  2.5f.dp.toPx(), close = true)
        drawContour(features.rightEye,           eyeColor,  2.5f.dp.toPx(), close = true)
        drawContour(features.leftEyebrowTop,     browColor, 2.dp.toPx())
        drawContour(features.leftEyebrowBottom,  browColor, 2.dp.toPx())
        drawContour(features.rightEyebrowTop,    browColor, 2.dp.toPx())
        drawContour(features.rightEyebrowBottom, browColor, 2.dp.toPx())
        drawContour(features.noseBridge,         noseColor, 2.dp.toPx())
        drawContour(features.noseBottom,         noseColor, 2.dp.toPx())
        drawContour(features.upperLipTop,        lipColor,  2.dp.toPx())
        drawContour(features.upperLipBottom,     lipColor,  2.dp.toPx())
        drawContour(features.lowerLipTop,        lipColor,  2.dp.toPx())
        drawContour(features.lowerLipBottom,     lipColor,  2.dp.toPx())
    }
}

@Composable
private fun CaptureErrorPanel(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Face,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.capture_error_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.capture_error_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.capture_retry_button))
        }
    }
}