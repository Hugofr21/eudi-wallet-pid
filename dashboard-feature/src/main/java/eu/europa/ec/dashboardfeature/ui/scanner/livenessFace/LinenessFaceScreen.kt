package eu.europa.ec.dashboardfeature.ui.scanner.livenessFace

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        when {
            !state.isInstructionAcknowledged -> {
                FaceIdInstructionsPanel(
                    onProceed = { viewModel.setEvent(Event.AcknowledgeInstructions) }
                )
            }

            state.isSessionComplete -> {
                if (state.capturedBitmap != null) {
                    FaceIdConfirmationPanel(
                        capturedBitmap = state.capturedBitmap!!,
                        onRetry   = { viewModel.setEvent(Event.RetryScanning) },
                        onConfirm = { viewModel.setEvent(Event.ConfirmDocument) }
                    )
                } else {
                    FaceIdCaptureErrorPanel(
                        onRetry = { viewModel.setEvent(Event.RetryScanning) }
                    )
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
                modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }

        state.errorMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color(0xFFB00020).copy(alpha = 0.9f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = msg, color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
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

    Column(modifier = Modifier.fillMaxSize()) {

        Box(modifier = Modifier.weight(0.65f)) {
            AndroidView(
                factory = { context ->
                    PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
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

            state.faceFeatures?.let { features ->
                FaceContoursOverlay(features = features)
            }

            FaceCircleOverlay()

            state.countdownSeconds?.let { sec ->
                CountdownOverlay(seconds = sec)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(0.35f)
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            LiveFacePreview(bitmap = state.previewBitmap)
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Current challenge",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
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
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.DarkGray,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


/**
 * Draws facial landmark contours directly on the camera preview.
 *
 * ML Kit returns points in image-space. We map them to view-space using the
 * same FILL_CENTER (scale-to-fill, centered) logic the PreviewView uses.
 * The front camera mirrors the image horizontally, so we flip the X axis.
 *
 * Groups drawn:
 *   faceOval         — outer face boundary       (blue,   2 dp)
 *   eyes             — left + right eye contours  (cyan,   1.5 dp)
 *   eyebrows         — top + bottom per eye       (white,  1.5 dp)
 *   noseBridge       — nose bridge                (white,  1.5 dp)
 *   noseBottom       — nose bottom arc            (white,  1.5 dp)
 *   upperLip top+bot — upper lip shape            (coral,  1.5 dp)
 *   lowerLip top+bot — lower lip shape            (coral,  1.5 dp)
 */
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
fun FaceCircleOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val diameter = size.width * 0.76f
        val cx = size.width  / 2f
        val cy = size.height / 2f
        val r  = diameter    / 2f

        // White mask with circular cutout
        val path = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
            addOval(Rect(cx - r, cy - r, cx + r, cy + r))
            fillType = PathFillType.EvenOdd
        }
        drawPath(path = path, color = Color.White)

        // Soft halo + crisp ring
        drawCircle(
            color  = Color.White.copy(alpha = 0.2f),
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
            modifier = Modifier
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
                    text = "$sec",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun LiveFacePreview(bitmap: Bitmap?) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(Color(0xFFE8E8E8), CircleShape),
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
                tint = Color.Gray,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun FaceIdInstructionsPanel(onProceed: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Biometric Verification",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "To confirm your identity, perform a few simple gestures in front of the front camera.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 40.dp)
        )
        InstructionRow(Icons.Default.LightMode,    "Ensure good lighting, avoid bright light behind you.")
        Spacer(modifier = Modifier.height(16.dp))
        InstructionRow(Icons.Default.Face,         "Remove sunglasses or hats.")
        Spacer(modifier = Modifier.height(16.dp))
        InstructionRow(Icons.Default.RemoveRedEye, "Follow these instructions: blink, smile, turn your head…")
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onProceed, modifier = Modifier.fillMaxWidth()) {
            Text("Start Scan")
        }
    }
}

@Composable
private fun InstructionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp).padding(top = 2.dp)
        )
        Text(
            text = text, style = MaterialTheme.typography.bodyMedium,
            color = Color.DarkGray, modifier = Modifier.padding(start = 12.dp)
        )
    }
}


@Composable
private fun FaceIdCaptureErrorPanel(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = Icons.Default.Face, contentDescription = null,
            tint = Color(0xFFB00020),
            modifier = Modifier.size(64.dp).padding(bottom = 16.dp))
        Text(text = "The image could not be captured.",
            style = MaterialTheme.typography.titleLarge, color = Color.Black,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp))
        Text(text = "Make sure your face is well lit and within the circle, then try again.",
            style = MaterialTheme.typography.bodyMedium, color = Color.Gray,
            textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 40.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("Try again")
        }
    }
}


@Composable
private fun FaceIdConfirmationPanel(
    capturedBitmap: Bitmap,
    onRetry: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(0.70f)) {
            Image(
                bitmap = capturedBitmap.asImageBitmap(),
                contentDescription = "Face captured",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth().height(64.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null,
                        tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = "Liveness confirm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth().weight(0.30f)
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Face captured",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Is your face clearly visible? If not, repeat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray, textAlign = TextAlign.Center)
            }
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF0F0F0), contentColor = Color.DarkGray),
                    shape = RoundedCornerShape(12.dp)) {
                    Text("Repeat", fontWeight = FontWeight.Medium)
                }
                Button(onClick = onConfirm,
                    modifier = Modifier.weight(2f).height(50.dp),
                    shape = RoundedCornerShape(12.dp)) {
                    Text("Confirm identity", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}