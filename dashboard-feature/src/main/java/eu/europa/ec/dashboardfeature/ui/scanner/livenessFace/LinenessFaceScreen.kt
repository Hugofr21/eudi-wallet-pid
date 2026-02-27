package eu.europa.ec.dashboardfeature.ui.scanner.livenessFace

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import android.graphics.PointF
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.europa.ec.mrzscannerLogic.controller.Challenge
import eu.europa.ec.mrzscannerLogic.controller.ChallengeState
import eu.europa.ec.mrzscannerLogic.controller.FaceFeatures
import eu.europa.ec.mrzscannerLogic.model.ScanType


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivenessFaceScreen(
    navHostController: NavController,
    viewModel: LivenessFaceViewModel,
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is Effect.Navigation.Pop -> navHostController.popBackStack()
                is Effect.Navigation.SwitchScreen -> navHostController.navigate(effect.screenRoute)
                is Effect.ShowDialog.Help -> {

                }
            }
        }
    }

    LaunchedEffect(previewViewRef, lifecycleOwner) {
        previewViewRef?.let { view ->
            viewModel.handleEvents(
                Event.InitializeScanner(lifecycleOwner, view, ScanType.Liveness)
            )
        }
    }

    // Gestão do Ciclo de Vida do Scanner
    DisposableEffect(lifecycleOwner) {
        onDispose {
            viewModel.handleEvents(Event.StopScanning)
            previewViewRef = null // Limpeza de referência
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Camada Base: Feed da Câmara
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewViewRef = this
                    // Injeção imediata da view instanciada no pipeline de eventos
                    viewModel.handleEvents(
                        Event.InitializeScanner(lifecycleOwner, this, ScanType.Liveness)
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Camada Intermédia: Renderização Vetorial da Assimetria (Face Mesh)
        FaceAsymmetryOverlay(
            features = state.features,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Camada Superior: Interface de Instrução
        ChallengeOverlay(
            challengeState = state.currentChallengeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        )
    }
}


@Composable
private fun FaceAsymmetryOverlay(
    features: FaceFeatures,
    modifier: Modifier = Modifier
) {
    if (features.faceOval.isEmpty()) return

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val sourceWidth = features.imageWidth.toFloat()
        val sourceHeight = features.imageHeight.toFloat()

        if (sourceWidth <= 0 || sourceHeight <= 0) return@Canvas

        val scale = maxOf(canvasWidth / sourceWidth, canvasHeight / sourceHeight)
        val offsetX = (canvasWidth - sourceWidth * scale) / 2f
        val offsetY = (canvasHeight - sourceHeight * scale) / 2f

        fun transform(x: Float, y: Float): Offset {
            return Offset(canvasWidth - (x * scale + offsetX), y * scale + offsetY)
        }

        fun transform(point: PointF): Offset = transform(point.x, point.y)

        val bbox = features.boundingBox
        val topLeft = transform(bbox.right.toFloat(), bbox.top.toFloat())
        val bottomRight = transform(bbox.left.toFloat(), bbox.bottom.toFloat())
        drawRect(
            color = Color.Red,
            topLeft = topLeft,
            size = androidx.compose.ui.geometry.Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
            style = Stroke(width = 8f)
        )

        fun drawFeature(points: List<PointF>, color: Color, isClosed: Boolean = true) {
            if (points.isEmpty()) return

            val path = Path().apply {
                points.forEachIndexed { index, point ->
                    val mappedOffset = transform(point)
                    if (index == 0) moveTo(mappedOffset.x, mappedOffset.y)
                    else lineTo(mappedOffset.x, mappedOffset.y)
                }
                if (isClosed) close()
            }
            drawPath(path = path, color = color, style = Stroke(width = 5f))

            points.forEach { point ->
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = transform(point)
                )
            }
        }

        drawFeature(features.faceOval, Color(0xFF1976D2), isClosed = true)


        drawFeature(features.rightEyebrowTop, Color(0xFFD32F2F), isClosed = false)
        drawFeature(features.rightEyebrowBottom, Color(0xFFF57C00), isClosed = false)
        drawFeature(features.leftEyebrowTop, Color(0xFF388E3C), isClosed = false)
        drawFeature(features.leftEyebrowBottom, Color(0xFF7B1FA2), isClosed = false)

        drawFeature(features.rightEye, Color(0xFF1976D2), isClosed = true)
        drawFeature(features.leftEye, Color(0xFF1976D2), isClosed = true)

        drawFeature(features.noseBridge, Color(0xFF7B1FA2), isClosed = false)
        drawFeature(features.noseBottom, Color(0xFF0097A7), isClosed = false)

        drawFeature(features.upperLipTop, Color(0xFFC2185B), isClosed = false)
        drawFeature(features.upperLipBottom, Color(0xFFAFB42B), isClosed = false)
        drawFeature(features.lowerLipTop, Color(0xFFAFB42B), isClosed = false)
        drawFeature(features.lowerLipBottom, Color(0xFF1976D2), isClosed = false)
    }
}

@Composable
private fun ChallengeOverlay(
    challengeState: ChallengeState,
    modifier: Modifier = Modifier
) {
    val (instructionText, bgColor) = when (challengeState) {
        is ChallengeState.Idle -> "A preparar..." to Color.Black.copy(alpha = 0.7f)

        is ChallengeState.Pending -> {
            val text = when (challengeState.challenge) {
                Challenge.LOOK_LEFT -> "Vire o rosto para a Esquerda"
                Challenge.LOOK_RIGHT -> "Vire o rosto para a Direita"
                Challenge.BLINK -> "Pisque os Olhos"
                Challenge.SMILE -> "Sorria"
                Challenge.OPEN_MOUTH -> "Abra a Boca"
                Challenge.NOD -> "Acene a Cabeça (Cima/Baixo)"
            }
            text to Color.Black.copy(alpha = 0.7f)
        }

        is ChallengeState.Passed -> {

            "✓ Excelente!" to Color(0xFF2E7D32).copy(alpha = 0.9f)
        }

        is ChallengeState.Failed -> {
            "Falhou: ${challengeState.reason}" to Color(0xFFC62828).copy(alpha = 0.9f)
        }
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = instructionText,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}