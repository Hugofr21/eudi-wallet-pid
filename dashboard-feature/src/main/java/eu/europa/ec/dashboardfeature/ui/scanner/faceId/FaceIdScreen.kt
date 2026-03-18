package eu.europa.ec.dashboardfeature.ui.scanner.faceId

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.europa.ec.mrzscannerLogic.model.ScanType
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import eu.europa.ec.mrzscannerLogic.controller.MrzScanState

@Composable
fun FaceIdScreen(
    navHostController: NavController,
    viewModel: FaceIdScreenViewModel,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.viewState.collectAsStateWithLifecycle()

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(Unit) {
        if (state.scanState !is MrzScanState.Success) {
            viewModel.setEvent(
                Event.InitializeScanner(
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    scanType = ScanType.Face
                )
            )
        }
    }

    // Fundo base branco
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {

        // ==========================================================
        // CENÁRIO A: SUCESSO (FOTO CAPTURADA)
        // ==========================================================
        if (state.scanState is MrzScanState.Success) {
            val successState = state.scanState as MrzScanState.Success
            val capturedBitmap = successState.capturedImage

            if (capturedBitmap != null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Foto Capturada",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )

                    // Mostrar a imagem recortada num quadrado/retângulo
                    Card(
                        elevation = CardDefaults.cardElevation(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(300.dp) // Tamanho fixo ou dinâmico
                    ) {
                        Image(
                            bitmap = capturedBitmap.asImageBitmap(),
                            contentDescription = "Rosto Capturado",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // Botões de Ação
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { viewModel.setEvent(Event.RetryScanning) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                        ) {
                            Text("Repetir")
                        }

                        Button(
                            onClick = { viewModel.setEvent(Event.ConfirmDocument) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Confirmar")
                        }
                    }
                }
            }
        }

        // ==========================================================
        // CENÁRIO B: SCANNING (CÂMARA COM MÁSCARA BRANCA)
        // ==========================================================
        else {
            // 1. Câmara (fica por trás de tudo)
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // 2. Máscara Branca com Buraco Quadrado
            WhiteSquareOverlay(squareSizePercent = 0.75f)

            // 3. Instruções (Agora texto PRETO porque o fundo é branco)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp), // Afastar do topo
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Enquadre o rosto no quadrado",
                    color = Color.Black, // MUDANÇA IMPORTANTE: Texto Preto
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (state.scanState is MrzScanState.Processing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "A processar...",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Loading Global
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.8f)), // Fundo branco translúcido
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun WhiteSquareOverlay(
    modifier: Modifier = Modifier,
    squareSizePercent: Float = 0.7f // O quadrado ocupa 70% da largura do ecrã
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // 1. Calcular dimensões do quadrado
        val sideLength = size.width * squareSizePercent
        val left = (size.width - sideLength) / 2
        val top = (size.height - sideLength) / 2 // Centrado verticalmente

        // Ou se quiser mais para cima (tipo foto passe):
        // val top = size.height * 0.2f

        val squareRect = Rect(left, top, left + sideLength, top + sideLength)

        // 2. Criar o caminho com o "buraco" (EvenOdd rule)
        val path = Path().apply {
            // Retângulo do ecrã inteiro
            addRect(Rect(0f, 0f, size.width, size.height))
            // Retângulo do quadrado (o buraco)
            addRect(squareRect)
            // Esta regra faz com que o segundo retângulo subtraia ao primeiro
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        }

        // 3. Desenhar o fundo Branco Sólido (cobre a câmara à volta)
        drawPath(
            path = path,
            color = Color.White // Fundo Branco opaco
        )

        // 4. Desenhar a Borda do Quadrado (para o utilizador saber o limite)
        drawRect(
            color = Color.Black, // Ou azul/verde
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(sideLength, sideLength),
            style = Stroke(width = 4.dp.toPx())
        )

        // Opcional: Cantos da mira
        // (Pode adicionar aqui lógica de cantos se quiser algo mais sofisticado)
    }
}