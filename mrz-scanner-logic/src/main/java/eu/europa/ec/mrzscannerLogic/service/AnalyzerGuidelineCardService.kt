package eu.europa.ec.mrzscannerLogic.service

import android.graphics.Bitmap
import android.graphics.Color
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.mrzscannerLogic.model.AntiSpoofingCheck
import eu.europa.ec.mrzscannerLogic.model.AntiSpoofingReport
import eu.europa.ec.mrzscannerLogic.model.CheckResult
import eu.europa.ec.mrzscannerLogic.model.GuidelineAntiSpoofing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*


interface AnalyzerGuidelineCardService {
    suspend fun analyze(
        bitmap: Bitmap,
        config: GuidelineAntiSpoofing,
        gyroscopeMatrix: FloatArray? = null,
        accelerometerData: FloatArray? = null
    ): AntiSpoofingReport
    fun release()
}

class AnalyzerGuidelineCardServiceImpl(
    private val log: LogController
) : AnalyzerGuidelineCardService {

    // Estado inter-frames
    private var previousBitmapForOVI: Bitmap? = null
    private var previousRotationMatrix: FloatArray? = null
    private val accelerometerHistory = ArrayDeque<FloatArray>(15)
    private val frameHashHistory = ArrayDeque<Long>(8)

    private var zeroSpecularFrames = 0
    private var moireAlertFrames   = 0


    private val checkWeights = mapOf(
        AntiSpoofingCheck.SPECULAR_REFLECTION  to 0.25f,
        AntiSpoofingCheck.MOIRE_PATTERN        to 0.25f,
        AntiSpoofingCheck.IMAGE_QUALITY        to 0.05f,
        AntiSpoofingCheck.GYROSCOPE            to 0.10f,
        AntiSpoofingCheck.ACCELEROMETER        to 0.10f,
        AntiSpoofingCheck.ARCORE_DEPTH         to 0.00f,
        AntiSpoofingCheck.EDGE_SHARPNESS       to 0.10f,
        AntiSpoofingCheck.COLOR_CONSISTENCY    to 0.03f,
        AntiSpoofingCheck.TEMPORAL_CONSISTENCY to 0.10f,
        AntiSpoofingCheck.PRINT_ARTIFACT       to 0.02f,
    )

    override suspend fun analyze(
        bitmap: Bitmap,
        config: GuidelineAntiSpoofing,
        gyroscopeMatrix: FloatArray?,
        accelerometerData: FloatArray?
    ): AntiSpoofingReport = withContext(Dispatchers.Default) {

        val results = mutableListOf<CheckResult>()

        if (config.checkSpecularReflection)
            results += runCheck(AntiSpoofingCheck.SPECULAR_REFLECTION) { checkSpecularReflection(bitmap) }
        if (config.checkMoirePattern)
            results += runCheck(AntiSpoofingCheck.MOIRE_PATTERN) { checkMoirePattern(bitmap) }
        if (config.checkImageQuality)
            results += runCheck(AntiSpoofingCheck.IMAGE_QUALITY) { checkImageQuality(bitmap) }
        if (config.checkGyroscope && gyroscopeMatrix != null)
            results += runCheck(AntiSpoofingCheck.GYROSCOPE) { checkGyroscopeConsistency(bitmap, gyroscopeMatrix) }
        if (config.checkAccelerometer && accelerometerData != null)
            results += runCheck(AntiSpoofingCheck.ACCELEROMETER) { checkAccelerometerStability(accelerometerData) }
        if (config.checkEdgeSharpness)
            results += runCheck(AntiSpoofingCheck.EDGE_SHARPNESS) { checkEdgeSharpness(bitmap) }
        if (config.checkColorConsistency)
            results += runCheck(AntiSpoofingCheck.COLOR_CONSISTENCY) { checkColorConsistency(bitmap) }
        if (config.checkTemporalConsistency)
            results += runCheck(AntiSpoofingCheck.TEMPORAL_CONSISTENCY) { checkTemporalConsistency(bitmap) }
        if (config.checkPrintArtifact)
            results += runCheck(AntiSpoofingCheck.PRINT_ARTIFACT) { checkPrintArtifacts(bitmap) }

        val overallScore = computeWeightedScore(results)

        log.d("AntiSpoofing") {
            "[FINAL] score=${"%.3f".format(overallScore)} threshold=${config.threshold}\n" +
                    results.joinToString("\n") { r ->
                        "  ${r.check.name}: ${"%.2f".format(r.score)}${r.reason?.let { " ← $it" } ?: ""}"
                    }
        }

        AntiSpoofingReport(
            isReal       = overallScore >= config.threshold,
            overallScore = overallScore,
            checks       = results,
            verdict      = classifyAttackType(results, overallScore)
        )
    }

    // =========================================================================
    // SCORE PONDERADO
    // =========================================================================

    private fun computeWeightedScore(results: List<CheckResult>): Float {
        if (results.isEmpty()) return 1f
        var wSum = 0f; var wTotal = 0f
        for (r in results) {
            val w = checkWeights[r.check] ?: 0.05f
            wSum += r.score * w; wTotal += w
        }
        return if (wTotal > 0) wSum / wTotal else 1f
    }

    // =========================================================================
    // CLASSIFICADOR DE ATAQUE
    // =========================================================================

    private fun classifyAttackType(checks: List<CheckResult>, score: Float): AntiSpoofingReport.AttackType {
        if (score >= 0.65f) return AntiSpoofingReport.AttackType.REAL_CARD
        val moireScore = checks.scoreOf(AntiSpoofingCheck.MOIRE_PATTERN)
        val specScore  = checks.scoreOf(AntiSpoofingCheck.SPECULAR_REFLECTION)
        val printScore = checks.scoreOf(AntiSpoofingCheck.PRINT_ARTIFACT)
        val tempScore  = checks.scoreOf(AntiSpoofingCheck.TEMPORAL_CONSISTENCY)
        return when {
            moireScore < 0.35f || (tempScore < 0.30f && specScore < 0.40f) ->
                AntiSpoofingReport.AttackType.SCREEN_DISPLAY
            printScore < 0.35f || (specScore < 0.30f && moireScore > 0.6f) ->
                AntiSpoofingReport.AttackType.PRINTED_COPY
            else -> AntiSpoofingReport.AttackType.UNKNOWN
        }
    }

    private fun List<CheckResult>.scoreOf(check: AntiSpoofingCheck) =
        find { it.check == check }?.score ?: 0.5f

    // =========================================================================
    // HELPER
    // =========================================================================

    private fun runCheck(type: AntiSpoofingCheck, block: () -> Pair<Float, String?>): CheckResult =
        try {
            val (score, reason) = block()
            CheckResult(check = type, passed = score >= 0.5f, score = score,
                reason = if (score < 0.5f) reason else null)
        } catch (e: Exception) {
            log.e("AntiSpoofing") { "Erro $type: ${e.message}" }
            CheckResult(check = type, passed = true, score = 0.5f, reason = "Erro: ${e.message}")
        }

    // =========================================================================
    // TÉCNICA 1 — Reflexão Especular
    //
    // FIX v4: ratio=0.0 num frame isolado é normal (ângulo sem reflexo).
    // Só penaliza "papel fosco" se zeroSpecularFrames >= 3 consecutivos.
    // Repõe o contador quando aparece um frame com ratio > 0.
    // =========================================================================

    private fun checkSpecularReflection(bitmap: Bitmap): Pair<Float, String?> {
        val pixels = bitmap.toPixelArray()
        var specularCount = 0; var sumLum = 0L
        for (px in pixels) {
            val v = pixelToHsvV(px); sumLum += v
            if (v > 245) specularCount++
        }
        val n = pixels.size
        val ratio = specularCount.toFloat() / n
        val avgLum = sumLum.toFloat() / n

        if (ratio == 0f) zeroSpecularFrames++ else zeroSpecularFrames = 0

        log.d("AntiSpoofing") { "[SPECULAR] ratio=${"%.5f".format(ratio)} avgLum=${"%.1f".format(avgLum)} zeroFrames=$zeroSpecularFrames" }

        return when {
            // FIX: Se o rácio de saturação for gigante, é um ecrã luminoso colado à câmara
            ratio > 0.70f ->
                Pair(0.15f, "Saturação total — ecrã emissor de luz ou clarão extremo (ratio=${"%.2f".format(ratio)})")

            ratio in 0.0001f..0.06f ->
                Pair(0.90f, null)

            ratio > 0.06f && avgLum > 170f ->
                Pair(0.70f, null)

            ratio > 0.18f && avgLum <= 170f ->
                Pair(0.35f, "Brilho artificial excessivo — possível ecrã (ratio=${"%.3f".format(ratio)})")

            ratio == 0f && avgLum < 50f ->
                Pair(0.55f, null)

            ratio == 0f && zeroSpecularFrames >= 3 ->
                Pair(0.35f, "Ausência persistente de reflexo ($zeroSpecularFrames frames) — possível papel fosco")

            ratio == 0f -> Pair(0.60f, null)
            else -> Pair(0.65f, null)
        }
    }

    private fun pixelToHsvV(px: Int) = maxOf(Color.red(px), Color.green(px), Color.blue(px))

    // =========================================================================
    // TÉCNICA 2 — Moiré via FFT 2D
    //
    // FIX v4: limiar de picos aumentado de >20 para >25.
    // Adicionada janela temporal: só reporta fraude se moireAlertFrames >= 2
    // frames consecutivos ultrapassam o limiar (elimina spikes de textura).
    // =========================================================================

    private val FFT_SIZE = 256

    private fun checkMoirePattern(bitmap: Bitmap): Pair<Float, String?> {
        val gray = bitmapToGrayResized(bitmap, FFT_SIZE, FFT_SIZE)
        val real = Array(FFT_SIZE) { r -> DoubleArray(FFT_SIZE) { c -> gray[r * FFT_SIZE + c].toDouble() } }
        val imag = Array(FFT_SIZE) { DoubleArray(FFT_SIZE) }

        for (row in 0 until FFT_SIZE) fft1D(real[row], imag[row])
        for (col in 0 until FFT_SIZE) {
            val cr = DoubleArray(FFT_SIZE) { real[it][col] }; val ci = DoubleArray(FFT_SIZE) { imag[it][col] }
            fft1D(cr, ci)
            for (row in 0 until FFT_SIZE) { real[row][col] = cr[row]; imag[row][col] = ci[row] }
        }

        val mag = Array(FFT_SIZE) { r -> DoubleArray(FFT_SIZE) { c ->
            sqrt(real[r][c].pow(2) + imag[r][c].pow(2)) }
        }
        fftShift(mag)

        val cx = FFT_SIZE / 2; val cy = FFT_SIZE / 2

        var maxMagMoire = 0.0
        var maxMagMacro = 0.0; var sumMagMacro = 0.0; var countMacro = 0

        // 1. Extração de energia nas duas bandas espaciais
        for (r in 0 until FFT_SIZE) {
            for (c in 0 until FFT_SIZE) {
                val d = sqrt(((r - cy).toDouble().pow(2) + (c - cx).toDouble().pow(2)))
                val v = mag[r][c]

                if (d > 60.0) { // Banda Moiré Normal
                    if (v > maxMagMoire) maxMagMoire = v
                } else if (d in 22.0..60.0) { // Banda Ecrã Macro (Muito perto)
                    if (v > maxMagMacro) maxMagMacro = v
                    sumMagMacro += v
                    countMacro++
                }
            }
        }

        // Rácio de anomalia para ignorar texto (texto é difuso, grelha OLED é aguda)
        val meanMagMacro = if (countMacro > 0) sumMagMacro / countMacro else 1.0
        val macroPeakRatio = maxMagMacro / meanMagMacro

        val thrMoire = maxMagMoire * 0.95
        val thrMacro = maxMagMacro * 0.92
        var moirePeaks = 0
        var macroPeaks = 0

        // 2. Contagem de harmónicas anómalas
        for (r in 0 until FFT_SIZE) {
            for (c in 0 until FFT_SIZE) {
                val d = sqrt(((r - cy).toDouble().pow(2) + (c - cx).toDouble().pow(2)))
                val v = mag[r][c]
                if (d > 60.0 && v > thrMoire) moirePeaks++
                if (d in 22.0..60.0 && v > thrMacro) macroPeaks++
            }
        }

        // 3. Avaliação da Grelha Macro (Telefone colado ao ecrã)
        // Requer picos isolados (ratio > 10) e geometria de grelha regular (4 a 20 picos simétricos)
        val isMacroScreen = maxMagMacro > 40.0 && macroPeakRatio > 10.0 && macroPeaks in 4..20

        if (isMacroScreen || (maxMagMoire > 10.0 && moirePeaks > 25)) {
            moireAlertFrames++
        } else {
            moireAlertFrames = 0
        }

        log.d("AntiSpoofing") { "[FFT] moirePeaks=$moirePeaks macroPeaks=$macroPeaks macroRatio=${"%.1f".format(macroPeakRatio)} alerts=$moireAlertFrames" }

        return when {
            // Reprovação por Ecrã demasiado próximo (Macro)
            moireAlertFrames >= 2 && isMacroScreen ->
                Pair(0.10f, "Matriz de subpíxeis detetada — ecrã em aproximação extrema (Ratio: ${"%.1f".format(macroPeakRatio)})")

            // Reprovação por Moiré Clássico
            moireAlertFrames >= 2 ->
                Pair(0.15f, "Padrão Moiré persistente — possível ecrã ($moirePeaks picos)")

            // Frame isolada suspeita — amortece o score sem rejeitar imediatamente
            isMacroScreen || moirePeaks > 25 ->
                Pair(0.45f, "Anomalia periódica detetada — a aguardar confirmação temporal...")

            moirePeaks > 12 ->
                Pair(0.60f, null)

            else -> Pair(0.95f, null)
        }
    }

    // =========================================================================
    // TÉCNICA 3 — Qualidade da Imagem
    //
    // FIX v4: blur=4–9 é o valor real desta câmara com laminação do cartão.
    // Novos thresholds calibrados: fail < 3.0, aviso 3–12, ok > 12.
    // Exposição: só falha em extremos reais (< 25 ou > 230).
    // =========================================================================

    private fun checkImageQuality(bitmap: Bitmap): Pair<Float, String?> {
        val gray = bitmapToGray(bitmap); val w = bitmap.width; val h = bitmap.height
        val lap = FloatArray(gray.size)
        for (r in 1 until h-1) for (c in 1 until w-1) {
            val i = r*w+c
            lap[i] = (gray[i-w] + gray[i+w] + gray[i-1] + gray[i+1] - 4f * gray[i]).toFloat()
        }
        val mean = lap.average().toFloat()
        val blur = lap.map { (it - mean).pow(2) }.average()
        val bright = gray.average()

        log.d("AntiSpoofing") { "[QUALITY] blur=${"%.2f".format(blur)} bright=${"%.1f".format(bright)}" }

        return when {
            bright < 25.0   -> Pair(0.30f, "Imagem muito escura (lum=${"%.1f".format(bright)})")
            bright > 230.0  -> Pair(0.30f, "Sobre-exposta (lum=${"%.1f".format(bright)})")
            blur < 3.0      -> Pair(0.25f, "Imagem desfocada (var=${"%.2f".format(blur)}) — aguardar autofocus")
            blur < 12.0     -> Pair(0.60f, null) // laminação absorve gradiente — aceitável
            blur > 100.0    -> Pair(1.00f, null)
            else            -> Pair(0.80f, null)
        }
    }

    // =========================================================================
    // TÉCNICA 4 — Giroscópio + OVI
    //
    // FIX v4: zona OVI do CC português está aprox. no centro-direita inferior,
    // não no canto extremo (0.75, 0.75). Ajustada para (0.55–0.80, 0.60–0.85).
    // colorDelta mínimo para fraude baixado de 10 para 5.
    // Range neutro: colorDelta 5–20 com delta 15–25° → inconclusivo (0.55).
    // =========================================================================

    private fun checkGyroscopeConsistency(bitmap: Bitmap, rotMatrix: FloatArray): Pair<Float, String?> {
        val prev = previousBitmapForOVI; val prevRot = previousRotationMatrix
        previousBitmapForOVI = bitmap; previousRotationMatrix = rotMatrix
        if (prev == null || prevRot == null) return Pair(0.50f, null)

        val delta = calculateRotationDeltaDegrees(prevRot, rotMatrix)
        if (delta < 10f) return Pair(0.50f, null)

        val colorDelta = calculateMeanColorDelta(extractOviRegion(prev), extractOviRegion(bitmap))

        log.d("AntiSpoofing") { "[GYRO] delta=${"%.1f".format(delta)}° colorDelta=${"%.1f".format(colorDelta)}" }

        return when {
            delta > 20f && colorDelta < 5f  ->
                Pair(0.25f, "Sem variação OVI com rotação ${"%.1f".format(delta)}° — suspeito")
            delta > 15f && colorDelta > 20f ->
                Pair(0.95f, null) // OVI confirmado
            delta in 10f..25f && colorDelta in 5f..20f ->
                Pair(0.55f, null) // inconclusivo — rotação insuficiente ou OVI pequeno
            else ->
                Pair(0.60f, null)
        }
    }

    private fun calculateRotationDeltaDegrees(prev: FloatArray, curr: FloatArray): Float {
        val trace = (0..2).sumOf { i -> (0..2).sumOf { j -> (curr[i*3+j] * prev[i*3+j]).toDouble() } }
        return Math.toDegrees(acos(((trace - 1.0) / 2.0).coerceIn(-1.0, 1.0))).toFloat()
    }

    /**
     * FIX v4: Zona OVI do Cartão de Cidadão PT / Carta de Condução
     * localizada em aprox. 55–80% da largura e 60–85% da altura.
     * (antes estava em 75–100% × 75–100% — demasiado extremo)
     */
    private fun extractOviRegion(bmp: Bitmap): Bitmap {
        val x = (bmp.width  * 0.55f).toInt()
        val y = (bmp.height * 0.60f).toInt()
        val w = (bmp.width  * 0.25f).toInt().coerceAtLeast(1)
        val h = (bmp.height * 0.25f).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(bmp, x, y, w, h)
    }

    private fun calculateMeanColorDelta(b1: Bitmap, b2: Bitmap): Float {
        val (r1,g1,bc1) = b1.meanRgb(); val (r2,g2,bc2) = b2.meanRgb()
        return sqrt((r1-r2).pow(2) + (g1-g2).pow(2) + (bc1-bc2).pow(2))
    }

    // =========================================================================
    // TÉCNICA 5 — Acelerómetro
    // variance=1.4–2.3 nos logs → dentro dos limites, não estava a falhar.
    // Mantido igual à v3.
    // =========================================================================

    private fun checkAccelerometerStability(accel: FloatArray): Pair<Float, String?> {
        accelerometerHistory.addLast(accel.copyOf())
        if (accelerometerHistory.size > 15) accelerometerHistory.removeFirst()
        if (accelerometerHistory.size < 5) return Pair(0.50f, null)

        val variance = (0..2).sumOf { axis ->
            val v = accelerometerHistory.map { it[axis].toDouble() }
            val m = v.average()
            v.sumOf { (it - m).pow(2) } / v.size
        }.toFloat()

        log.d("AntiSpoofing") { "[ACCEL] variance=${"%.3f".format(variance)} samples=${accelerometerHistory.size}" }

        return when {
            variance > 5.0f  -> Pair(0.30f, "Agitação excessiva (σ²=${"%.2f".format(variance)}) — possível papel")
            variance > 2.0f  -> Pair(0.65f, null)
            variance < 0.3f  -> Pair(0.90f, null)
            else             -> Pair(0.80f, null)
        }
    }

    // =========================================================================
    // TÉCNICA 7 — Nitidez de Arestas
    //
    // FIX v4: nos logs innerSharp=13–18 > borderSharp=7–8 consistentemente.
    // Isto significa que o conteúdo do cartão (texto, foto, guilhoché) tem mais
    // gradiente do que a zona de borda da IMAGEM (que pode ser fundo liso).
    // Fix: se innerSharp > 8.0 → o cartão preenche o frame → score neutro 0.75.
    // Só aplica lógica de edge ratio quando o cartão NÃO preenche o frame.
    // =========================================================================

    private fun checkEdgeSharpness(bitmap: Bitmap): Pair<Float, String?> {
        val w = bitmap.width; val h = bitmap.height
        val gray = bitmapToGray(bitmap)
        val gx = FloatArray(gray.size); val gy = FloatArray(gray.size)

        for (r in 1 until h-1) for (c in 1 until w-1) {
            val i = r*w+c
            gx[i] = -gray[i-w-1] - 2*gray[i-1] - gray[i+w-1] + gray[i-w+1] + 2*gray[i+1] + gray[i+w+1]
            gy[i] = -gray[i-w-1] - 2*gray[i-w] - gray[i-w+1] + gray[i+w-1] + 2*gray[i+w] + gray[i+w+1]
        }

        val bf = 0.12f
        val bx1=(w*bf).toInt(); val bx2=w-bx1; val by1=(h*bf).toInt(); val by2=h-by1

        var bSum=0.0; var bN=0; var iSum=0.0; var iN=0
        for (r in 1 until h-1) for (c in 1 until w-1) {
            val i=r*w+c
            val mag=sqrt(gx[i].toDouble().pow(2)+gy[i].toDouble().pow(2))
            if (r !in by1..<by2 ||c<bx1||c>=bx2) { bSum+=mag; bN++ }
            else { iSum+=mag; iN++ }
        }

        val borderSharp = if (bN>0) bSum/bN else 0.0
        val innerSharp  = if (iN>0) iSum/iN  else 0.0
        val edgeRatio   = if (innerSharp>0.1) (borderSharp/innerSharp).toFloat() else 1f

        // FIX: innerSharp > 8.0 significa que o conteúdo do cartão domina o frame
        val cardFillsFrame = innerSharp > 8.0

        log.d("AntiSpoofing") { "[EDGE] borderSharp=${"%.2f".format(borderSharp)} innerSharp=${"%.2f".format(innerSharp)} ratio=${"%.2f".format(edgeRatio)} fillsFrame=$cardFillsFrame" }

        // Quando o cartão preenche o frame, innerSharp alto é sinal positivo de nitidez
        if (cardFillsFrame) {
            return when {
                innerSharp > 15.0 -> Pair(0.85f, null) // conteúdo muito nítido
                innerSharp > 8.0  -> Pair(0.75f, null) // conteúdo nítido normal
                else              -> Pair(0.60f, null)
            }
        }

        // Cartão com fundo visível — avalia aresta exterior
        return when {
            edgeRatio > 1.8f && borderSharp > 15.0 -> Pair(0.90f, null)
            borderSharp < 4.0 -> Pair(0.30f, "Arestas muito suaves — possível fotocópia (${"%.1f".format(borderSharp)})")
            edgeRatio < 0.7f  -> Pair(0.45f, "Gradiente uniforme — suspeito de impressão (ratio=${"%.2f".format(edgeRatio)})")
            else              -> Pair(0.70f, null)
        }
    }

    // ─── 2. COLOR_CONSISTENCY — threshold CV ajustado para cartões reais ──────────
//
// Problema: CV=0.78–0.82 num cartão real com foto + guilhoché.
// Causa:    O CV mede a variação total dos canais. Cartões de identidade
//           têm intencionalmente muita variação de cor (segurança visual).
// Fix:      Só falha se CV > 0.88 E correlação < 0.55 (ambas as condições
//           em simultâneo). Isso captura inkjet onde os canais separam E
//           há muita variação, mas não penaliza cartões ricos em cor.
//           Adicionada banda de segurança para corrRG > 0.95: se os canais
//           estão altamente correlacionados (como nos logs: 0.98–0.99),
//           o documento é genuíno independentemente do CV.
//
    private fun checkColorConsistency(bitmap: Bitmap): Pair<Float, String?> {
        val pixels = bitmap.toPixelArray(); val n = pixels.size.toFloat()
        var sR = 0.0; var sG = 0.0; var sB = 0.0
        for (p in pixels) { sR += Color.red(p); sG += Color.green(p); sB += Color.blue(p) }
        val mR = sR / n; val mG = sG / n; val mB = sB / n

        var vR = 0.0; var vG = 0.0; var vB = 0.0; var covRG = 0.0; var covRB = 0.0
        for (p in pixels) {
            val dr = Color.red(p) - mR; val dg = Color.green(p) - mG; val db = Color.blue(p) - mB
            vR += dr*dr; vG += dg*dg; vB += db*db; covRG += dr*dg; covRB += dr*db
        }
        vR /= n; vG /= n; vB /= n; covRG /= n; covRB /= n
        val sdR = sqrt(vR); val sdG = sqrt(vG); val sdB = sqrt(vB)

        val cvR  = if (mR > 1) sdR / mR else 0.0
        val cvG  = if (mG > 1) sdG / mG else 0.0
        val cvB  = if (mB > 1) sdB / mB else 0.0
        val avgCV   = (cvR + cvG + cvB) / 3.0
        val corrRG  = if (sdR > 0 && sdG > 0) (covRG / (sdR * sdG)).coerceIn(-1.0, 1.0) else 0.0
        val corrRB  = if (sdR > 0 && sdB > 0) (covRB / (sdR * sdB)).coerceIn(-1.0, 1.0) else 0.0
        val avgCorr = (corrRG + corrRB) / 2.0

        log.d("AntiSpoofing") { "[COLOR] cv=${"%.3f".format(avgCV)} corrRG=${"%.3f".format(corrRG)} corrRB=${"%.3f".format(corrRB)}" }

        return when {
            // ★ NOVO: correlação muito alta (corrRG > 0.95) → canais em sintonia → cartão real
            // Nos logs o cartão real tem corrRG=0.98–0.99 → este case resolve tudo
            avgCorr > 0.92 ->
                Pair(0.85f, null)

            // Canais bem correlacionados + CV dentro da gama de documentos ricos em cor
            avgCorr > 0.80 && avgCV in 0.10..0.85 ->
                Pair(0.80f, null)

            // ★ NOVO: só falha se CV excessivo E canais DESCORRELACIONADOS (inkjet verdadeiro)
            avgCV > 0.88 && avgCorr < 0.55 ->
                Pair(0.30f, "Variação de cor muito elevada + canais descorrelacionados — inkjet (CV=${"%.2f".format(avgCV)}, corr=${"%.2f".format(avgCorr)})")

            // Canais descorrelacionados independentemente do CV → pontos CMYK separados
            avgCorr < 0.35 ->
                Pair(0.40f, "Canais descorrelacionados — artefacto CMYK (corr=${"%.2f".format(avgCorr)})")

            else -> Pair(0.65f, null)
        }
    }
    // =========================================================================
    // TÉCNICA 9 — Consistência Temporal
    // avgHashDelta=11–15 nos logs → zona saudável (3–18). Não falha.
    // =========================================================================

    private fun checkTemporalConsistency(bitmap: Bitmap): Pair<Float, String?> {
        val hash = computePerceptualHash(bitmap)
        frameHashHistory.addLast(hash)
        if (frameHashHistory.size > 8) frameHashHistory.removeFirst()
        if (frameHashHistory.size < 4) return Pair(0.50f, null)
        var totalDelta = 0.0
        for (i in 1 until frameHashHistory.size)
            totalDelta += hammingDistance(frameHashHistory[i-1], frameHashHistory[i])
        val avgDelta = totalDelta / (frameHashHistory.size - 1)
        log.d("AntiSpoofing") { "[TEMPORAL] avgHashDelta=${"%.2f".format(avgDelta)} frames=${frameHashHistory.size}" }
        return when {
            avgDelta < 1.5  -> Pair(0.15f, "Frames estáticas — possível imagem estática (${"%.1f".format(avgDelta)})")
            avgDelta > 28.0 -> Pair(0.40f, "Movimento excessivo entre frames")
            avgDelta in 2.0..20.0 -> Pair(0.90f, null)
            else -> Pair(0.65f, null)
        }
    }

    private fun computePerceptualHash(bitmap: Bitmap): Long {
        val size = 32; val gray = bitmapToGrayResized(bitmap, size, size)
        val globalMean = gray.average(); var hash = 0L
        for (i in 0 until minOf(64, gray.size))
            if (gray[i] > globalMean) hash = hash or (1L shl i)
        return hash
    }

    private fun hammingDistance(a: Long, b: Long) = java.lang.Long.bitCount(a xor b)

    // =========================================================================
    // TÉCNICA 10 — Artefactos de Impressão
    // halftone=0.2 nos logs — este check ESTÁ a detetar corretamente algo.
    // Peso reduzido para 0.05 para não afetar score global em cartões reais.
    // =========================================================================

    private fun checkPrintArtifacts(bitmap: Bitmap): Pair<Float, String?> {
        val halfScore = detectHalftonePeaks(bitmap)
        val bandScore = detectBanding(bitmap)
        val quantScore = detectQuantizationNoise(bitmap)
        val combined = ((halfScore.first + bandScore.first + quantScore.first) / 3f)
        val reasons = listOfNotNull(halfScore.second, bandScore.second, quantScore.second)
        log.d("AntiSpoofing") { "[PRINT] halftone=${halfScore.first} band=${bandScore.first} quant=${quantScore.first} → combined=$combined" }
        return Pair(combined, reasons.joinToString(" | ").ifEmpty { null })
    }

    //
    // Problema: guilhoché e micro-impressão de segurança dos cartões reais PT
    //           geram picos no anel FFT 12–28 tal como o inkjet halftone.
    // Fix A:    Aumentar o anel para 18–32 (frequências MAIS altas que o guilhoché)
    //           O guilhoché tem frequências baixas–médias (raio 8–18 no espectro 64pt).
    //           O halftone inkjet tem frequências médias–altas (raio 20–35).
    // Fix B:    Threshold de picos sobe de >6 para >10 (e ringRatio > 0.55).
    // Fix C:    Correlação com COLOR: só falha como halftone se corrRG < 0.70
    //           (halftone inkjet tem canais menos correlacionados).
    //
    private fun detectHalftonePeaks(bitmap: Bitmap): Pair<Float, String?> {
        val sz = 64
        val gray = bitmapToGrayResized(bitmap, sz, sz)
        val real = Array(sz) { r -> DoubleArray(sz) { c -> gray[r * sz + c].toDouble() } }
        val imag = Array(sz) { DoubleArray(sz) }

        for (r in 0 until sz) fft1D(real[r], imag[r])
        for (c in 0 until sz) {
            val cr = DoubleArray(sz) { real[it][c] }; val ci = DoubleArray(sz) { imag[it][c] }
            fft1D(cr, ci)
            for (r in 0 until sz) { real[r][c] = cr[r]; imag[r][c] = ci[r] }
        }

        val mag = Array(sz) { r -> DoubleArray(sz) { c -> sqrt(real[r][c].pow(2) + imag[r][c].pow(2)) } }
        fftShift(mag)

        val cx = sz / 2; val cy = sz / 2
        var ringPeaks = 0; var totalE = 0.0; var ringE = 0.0

        for (r in 0 until sz) for (c in 0 until sz) {
            val d = sqrt(((r - cy).toDouble().pow(2) + (c - cx).toDouble().pow(2)))
            val e = mag[r][c]; totalE += e
            if (d in 18.0..32.0) { ringE += e; if (e > 600) ringPeaks++ }
        }

        val ringRatio = if (totalE > 0) ringE / totalE else 0.0

        log.d("AntiSpoofing") { "[HALFTONE] ringPeaks=$ringPeaks ringRatio=${"%.3f".format(ringRatio)}" }

        return when {
            // ★ Thresholds mais elevados para evitar falsos positivos em guilhoché
            ringPeaks > 10 || ringRatio > 0.55 ->
                Pair(0.25f, "Halftone inkjet detetado ($ringPeaks picos, ratio=${"%.2f".format(ringRatio)})")
            ringPeaks > 5 ->
                Pair(0.55f, null) // suspeito mas inconclusivo
            else ->
                Pair(0.90f, null)
        }
    }
    private fun detectBanding(bitmap: Bitmap): Pair<Float, String?> {
        val w=bitmap.width; val h=bitmap.height; val gray=bitmapToGray(bitmap)
        val means = DoubleArray(h) { r -> var s=0.0; for(c in 0 until w) s+=gray[r*w+c]; s/w }
        var bVar=0.0; for(i in 1 until h) bVar+=(means[i]-means[i-1]).pow(2); bVar/=(h-1)
        return when {
            bVar>40.0 -> Pair(0.30f,"Banding horizontal (var=${"%.1f".format(bVar)})")
            bVar<5.0  -> Pair(0.90f,null)
            else      -> Pair(0.70f,null)
        }
    }

    private fun detectQuantizationNoise(bitmap: Bitmap): Pair<Float, String?> {
        val w=bitmap.width; val h=bitmap.height; val gray=bitmapToGray(bitmap)
        var abrupt=0; var total=0
        for(r in 1 until h-1) for(c in 1 until w-1) {
            val i=r*w+c
            if(abs(gray[i]-gray[i+1])+abs(gray[i]-gray[i+w])>60f) abrupt++
            total++
        }
        val noiseR=abrupt.toFloat()/total
        return when {
            noiseR>0.12f -> Pair(0.35f,"Quantization noise — impressão (${"%.3f".format(noiseR)})")
            noiseR<0.03f -> Pair(0.85f,null)
            else         -> Pair(0.65f,null)
        }
    }

    // =========================================================================
    // FFT 1D Cooley-Tukey
    // =========================================================================

    private fun fft1D(real: DoubleArray, imag: DoubleArray) {
        val n=real.size
        require(n>0&&(n and(n-1))==0){"FFT: potência de 2"}
        var j=0
        for(i in 1 until n) {
            var bit=n shr 1
            while(j and bit!=0){j=j xor bit;bit=bit shr 1}
            j=j xor bit
            if(i<j){real[i]=real[j].also{real[j]=real[i]};imag[i]=imag[j].also{imag[j]=imag[i]}}
        }
        var len=2
        while(len<=n) {
            val half=len/2;val ang=-2.0*PI/len;val wBR=cos(ang);val wBI=sin(ang)
            var i=0
            while(i<n) {
                var wR=1.0;var wI=0.0
                for(k in 0 until half) {
                    val uR=real[i+k];val uI=imag[i+k]
                    val vR=real[i+k+half]*wR-imag[i+k+half]*wI
                    val vI=real[i+k+half]*wI+imag[i+k+half]*wR
                    real[i+k]=uR+vR;imag[i+k]=uI+vI
                    real[i+k+half]=uR-vR;imag[i+k+half]=uI-vI
                    val nw=wR*wBR-wI*wBI;wI=wR*wBI+wI*wBR;wR=nw
                }
                i+=len
            }
            len=len shl 1
        }
    }

    private fun fftShift(mag: Array<DoubleArray>) {
        val rows=mag.size;val cols=mag[0].size;val cx=cols/2;val cy=rows/2
        for(r in 0 until cy) for(c in 0 until cx) {
            mag[r][c]=mag[r+cy][c+cx].also{mag[r+cy][c+cx]=mag[r][c]}
            mag[r][c+cx]=mag[r+cy][c].also{mag[r+cy][c]=mag[r][c+cx]}
        }
    }

    private fun Bitmap.toPixelArray(): IntArray {
        val safe=if(config==Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888,false)
        return IntArray(safe.width*safe.height).also{safe.getPixels(it,0,safe.width,0,0,safe.width,safe.height)}
    }

    private fun bitmapToGray(bitmap: Bitmap): FloatArray {
        val px=bitmap.toPixelArray()
        return FloatArray(px.size){i->val p=px[i];0.299f*Color.red(p)+0.587f*Color.green(p)+0.114f*Color.blue(p)}
    }

    private fun bitmapToGrayResized(bitmap: Bitmap, tw: Int, th: Int): IntArray {
        val sw=bitmap.width;val sh=bitmap.height;val px=bitmap.toPixelArray()
        return IntArray(tw*th){idx->
            val r=(idx/tw*sh/th).coerceIn(0,sh-1);val c=(idx%tw*sw/tw).coerceIn(0,sw-1)
            val p=px[r*sw+c]
            (0.299*Color.red(p)+0.587*Color.green(p)+0.114*Color.blue(p)).toInt()
        }
    }

    private fun Bitmap.meanRgb(): Triple<Float,Float,Float> {
        val px=toPixelArray();var sR=0L;var sG=0L;var sB=0L
        for(p in px){sR+=Color.red(p);sG+=Color.green(p);sB+=Color.blue(p)}
        val n=px.size.toFloat()
        return Triple(sR/n,sG/n,sB/n)
    }


    override fun release() {
        accelerometerHistory.clear(); frameHashHistory.clear()
        previousBitmapForOVI = null; previousRotationMatrix = null
        zeroSpecularFrames = 0; moireAlertFrames = 0
    }
}