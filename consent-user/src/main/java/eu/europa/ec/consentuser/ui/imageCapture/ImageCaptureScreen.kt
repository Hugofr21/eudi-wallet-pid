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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
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


private val OverlayScrim  = Color(0xCC0A0F1E)
private val ContourFace   = Color(0xFF1565C0)
private val ContourEye    = Color(0xFF00BCD4)
private val ContourBrow   = Color(0xD9FFFFFF)
private val ContourNose   = Color(0xBFFFFFFF)
private val ContourLip    = Color(0xFFFF7043)

@Composable
fun ImageCaptureScreen(navController: NavController, viewModel: ImageCaptureViewModel) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
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
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f   to MaterialTheme.colorScheme.background,
                    0.5f to MaterialTheme.colorScheme.surface,
                    1f   to MaterialTheme.colorScheme.background
                )
            )
    ) {
        when {
            !hasCameraPermission -> PermissionRequestPanel(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )
            !state.isInstructionAcknowledged -> CaptureInstructionsPanel(
                onProceed = { viewModel.setEvent(Event.AcknowledgeInstructions) }
            )
            state.isSessionComplete -> {
                if (state.capturedBitmap != null)
                    CaptureConfirmationPanel(
                        capturedBitmap = state.capturedBitmap!!,
                        onRetry        = { viewModel.setEvent(Event.RetryCapture) },
                        onConfirm      = { viewModel.setEvent(Event.GoNext) }
                    )
                else
                    CaptureErrorPanel(onRetry = { viewModel.setEvent(Event.RetryCapture) })
            }
            else -> LivenessCapturePanel(viewModel = viewModel, state = state)
        }


        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OverlayScrim),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
        }


        state.errorMessage?.let { msg ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.errorContainer,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
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
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TopStepBar(currentStep = 4)
        Spacer(modifier = Modifier.weight(1f))

        // Icon badge
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "To complete identity verification, camera access is strictly required.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(40.dp))

        PrimaryActionButton(
            text = "Grant Permission",
            onClick = onRequestPermission
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CaptureInstructionsPanel(onProceed: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopStepBar(currentStep = 4)
        Spacer(modifier = Modifier.height(36.dp))

        // Hero icon with layered glow rings
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VideoCall,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.consent_screen_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.user_consent_step_2_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(32.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InstructionRow(Icons.Default.LightMode, stringResource(R.string.capture_instruction_light))
                InstructionRow(Icons.Default.Face,       stringResource(R.string.capture_instruction_face))
                InstructionRow(Icons.Default.RemoveRedEye, stringResource(R.string.capture_instruction_follow))
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        PrimaryActionButton(
            text = stringResource(R.string.consent_screen_confirm_button),
            onClick = onProceed
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun InstructionRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
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
                factory = { ctx ->
                    PreviewView(ctx).apply {
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

            // Progress bar pinned to top inside the camera box
            ChallengeProgressBar(
                completed = state.completedChallenges.size,
                total     = state.totalChallenges,
                modifier  = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

        Surface(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 0.dp,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                LiveFacePreview(bitmap = state.previewBitmap)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.capture_current_challenge_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.6.sp
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
}


@Composable
fun FaceCircleOverlay(modifier: Modifier = Modifier) {
    val scrimColor   = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.fillMaxSize()) {
        val diameter = size.width * 0.76f
        val cx = size.width  / 2f
        val cy = size.height / 2f
        val r  = diameter    / 2f

        // Semi-transparent scrim with hole
        val path = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
            addOval(Rect(cx - r, cy - r, cx + r, cy + r))
            fillType = PathFillType.EvenOdd
        }
        drawPath(path = path, color = scrimColor.copy(alpha = 0.72f))

        // Outer soft halo
        drawCircle(
            color  = primaryColor.copy(alpha = 0.15f),
            radius = r + 14.dp.toPx(),
            center = Offset(cx, cy),
            style  = Stroke(width = 14.dp.toPx())
        )
        // Inner ring
        drawCircle(
            color  = primaryColor.copy(alpha = 0.6f),
            radius = r + 4.dp.toPx(),
            center = Offset(cx, cy),
            style  = Stroke(width = 1.5f.dp.toPx())
        )
        // Crisp primary border
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
            progress      = { animatedProgress },
            modifier      = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color         = MaterialTheme.colorScheme.primary,
            trackColor    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text     = "$completed / $total",
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            modifier = Modifier.align(Alignment.End)
        )
    }
}


@Composable
private fun LiveFacePreview(bitmap: Bitmap?) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                ),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap           = bitmap.asImageBitmap(),
                contentDescription = "Face preview",
                contentScale     = ContentScale.Crop,
                modifier         = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
            )
        } else {
            Icon(
                imageVector    = Icons.Default.Face,
                contentDescription = null,
                tint           = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier       = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun CountdownOverlay(seconds: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            OverlayScrim,
                            OverlayScrim.copy(alpha = 0.85f)
                        )
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = seconds,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 1.4f))
                        .togetherWith(fadeOut(tween(100)))
                },
                label = "countdown"
            ) { sec ->
                Text(
                    text       = "$sec",
                    fontSize   = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
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
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopStepBar(currentStep = 4)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f)
        ) {
            Image(
                bitmap             = capturedBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
            // Confirmation chip
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                tonalElevation = 0.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector    = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint           = MaterialTheme.colorScheme.tertiary,
                        modifier       = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text       = stringResource(R.string.capture_liveness_confirmed),
                        style      = MaterialTheme.typography.labelMedium,
                        color      = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.35f)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = stringResource(R.string.capture_photo_captured_title),
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text       = stringResource(R.string.capture_photo_captured_subtitle),
                    style      = MaterialTheme.typography.bodySmall,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign  = TextAlign.Center
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick  = onRetry,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor   = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        stringResource(R.string.capture_retry_button),
                        fontWeight = FontWeight.Medium
                    )
                }
                Button(
                    onClick  = onConfirm,
                    modifier = Modifier.weight(2f).height(50.dp),
                    shape    = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        stringResource(R.string.consent_screen_confirm_button),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureErrorPanel(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector    = Icons.Default.Face,
                contentDescription = null,
                tint           = MaterialTheme.colorScheme.error,
                modifier       = Modifier.size(44.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text       = stringResource(R.string.capture_error_title),
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
            color      = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text       = stringResource(R.string.capture_error_subtitle),
            style      = MaterialTheme.typography.bodyMedium,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign  = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(40.dp))
        PrimaryActionButton(
            text    = stringResource(R.string.capture_retry_button),
            onClick = onRetry
        )
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
            drawPath(path = path, color = color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        drawContour(features.faceOval,          ContourFace, 3.dp.toPx(),   close = true)
        drawContour(features.leftEye,            ContourEye,  2.5f.dp.toPx(), close = true)
        drawContour(features.rightEye,           ContourEye,  2.5f.dp.toPx(), close = true)
        drawContour(features.leftEyebrowTop,     ContourBrow, 2.dp.toPx())
        drawContour(features.leftEyebrowBottom,  ContourBrow, 2.dp.toPx())
        drawContour(features.rightEyebrowTop,    ContourBrow, 2.dp.toPx())
        drawContour(features.rightEyebrowBottom, ContourBrow, 2.dp.toPx())
        drawContour(features.noseBridge,         ContourNose, 2.dp.toPx())
        drawContour(features.noseBottom,         ContourNose, 2.dp.toPx())
        drawContour(features.upperLipTop,        ContourLip,  2.dp.toPx())
        drawContour(features.upperLipBottom,     ContourLip,  2.dp.toPx())
        drawContour(features.lowerLipTop,        ContourLip,  2.dp.toPx())
        drawContour(features.lowerLipBottom,     ContourLip,  2.dp.toPx())
    }
}

@Composable
private fun PrimaryActionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick  = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape  = RoundedCornerShape(14.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}