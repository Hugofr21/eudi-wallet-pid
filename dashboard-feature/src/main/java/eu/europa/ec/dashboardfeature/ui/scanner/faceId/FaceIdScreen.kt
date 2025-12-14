package eu.europa.ec.dashboardfeature.ui.scanner.faceId

import android.graphics.Canvas
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.europa.ec.mrzscannerLogic.model.ScanType

@Composable
fun FaceScanScreen(
    navHostController: NavController,
    viewModel: FaceIdScreenViewModel,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Preview da câmara (Frontal)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(Unit) {
        // Pede ao ViewModel para iniciar o Scan de Rosto (ScanType.Face)
        viewModel.setEvent(
            Event.InitializeScanner(
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                scanType = ScanType.Face // NOVO TIPO
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Preview
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Overlay Circular (Máscara)
        FaceOverlay()

        // Instruções
        Text(
            text = "Posicione o rosto no círculo",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp)
        )

        // Se detetar face válida, navega ou mostra sucesso
        // (A lógica de sucesso é gerida pelo ViewModel)
    }
}

@Composable
fun FaceOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Desenha um círculo transparente no meio de um fundo semi-transparente
        val circleRadius = size.width * 0.35f
        val center = Offset(size.width / 2, size.height / 2.5f) // Um pouco acima do centro

        // Fundo escuro
        drawRect(Color.Black.copy(alpha = 0.6f))

        // Buraco (usando blend mode ou desenhando arco)
        // Simplificado: Desenhamos um círculo branco stroke
        drawCircle(
            color = Color.White,
            center = center,
            radius = circleRadius,
            style = Stroke(width = 4.dp.toPx())
        )
    }
}