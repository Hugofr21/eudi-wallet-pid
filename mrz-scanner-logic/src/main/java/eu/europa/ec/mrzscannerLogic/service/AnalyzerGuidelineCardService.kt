package eu.europa.ec.mrzscannerLogic.service

import android.graphics.Bitmap
import android.graphics.Color
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.mrzscannerLogic.model.AntiSpoofingCheck
import eu.europa.ec.mrzscannerLogic.model.AntiSpoofingReport
import eu.europa.ec.mrzscannerLogic.model.CheckResult
import eu.europa.ec.mrzscannerLogic.model.GuidelineAntiSpoofing
import eu.europa.ec.mrzscannerLogic.utils.ImageUtils.bitmapToGray
import eu.europa.ec.mrzscannerLogic.utils.ImageUtils.bitmapToGrayResized
import eu.europa.ec.mrzscannerLogic.utils.ImageUtils.toPixelArray
import eu.europa.ec.mrzscannerLogic.utils.ImageUtils.meanRgb
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

    private var previousBitmapForOVI: Bitmap? = null
    private var previousRotationMatrix: FloatArray? = null
    private val accelerometerHistory = ArrayDeque<FloatArray>(15)
    private val frameHashHistory = ArrayDeque<Long>(8)

    private var zeroSpecularFrames = 0

    // Janela deslizante de energia Moiré (5 frames)
    private val moireEnergyWindow = ArrayDeque<Float>(5)

    // Centróides de gradiente por quadrante para homografia
    // [cx0,cy0, cx1,cy1, cx2,cy2, cx3,cy3] — rastreia conteúdo real da imagem
    private var previousGradientCentroids: FloatArray? = null


    private val checkWeights = mapOf(
        AntiSpoofingCheck.SPECULAR_REFLECTION    to 0.25f,
        AntiSpoofingCheck.MOIRE_PATTERN          to 0.25f,
        AntiSpoofingCheck.IMAGE_QUALITY          to 0.05f,
        AntiSpoofingCheck.GYROSCOPE              to 0.10f,
        AntiSpoofingCheck.ACCELEROMETER          to 0.10f,
        AntiSpoofingCheck.ARCORE_DEPTH           to 0.00f,
        AntiSpoofingCheck.EDGE_SHARPNESS         to 0.08f,
        AntiSpoofingCheck.COLOR_CONSISTENCY      to 0.02f,
        AntiSpoofingCheck.TEMPORAL_CONSISTENCY   to 0.10f,
        AntiSpoofingCheck.PRINT_ARTIFACT         to 0.03f,
        AntiSpoofingCheck.HOMOGRAPHY_PLANARITY   to 0.12f,
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
        if (config.checkHomographyPlanarity)
            results += runCheck(AntiSpoofingCheck.HOMOGRAPHY_PLANARITY) { checkHomographyPlanarity(bitmap) }

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
        val homoScore  = checks.scoreOf(AntiSpoofingCheck.HOMOGRAPHY_PLANARITY)
        return when {
            moireScore < 0.35f || (tempScore < 0.30f && specScore < 0.40f) ->
                AntiSpoofingReport.AttackType.SCREEN_DISPLAY
            homoScore < 0.45f || printScore < 0.35f || (specScore < 0.30f && moireScore > 0.6f) ->
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
            CheckResult(
                check  = type,
                passed = score >= 0.5f,
                score  = score,
                reason = if (score < 0.5f) reason else null
            )
        } catch (e: Exception) {
            log.e("AntiSpoofing") { "Erro $type: ${e.message}" }
            CheckResult(check = type, passed = true, score = 0.5f, reason = "Erro: ${e.message}")
        }

    // =========================================================================
    // TÉCNICA 1 — Reflexão Especular
    //
    // FIX v5b: limiar zeroSpecularFrames 3 → 5.
    // O cartão real perde o ângulo de flash em frames intermédios
    // (avgLum=155→110→139 nos logs), causando bloqueio falso aos 3 frames.
    // 5 frames consecutivos sem reflexo ≈ 1 segundo a ~5fps de análise.
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

        log.d("AntiSpoofing") {
            "[SPECULAR] ratio=${"%.5f".format(ratio)} avgLum=${"%.1f".format(avgLum)} zeroFrames=$zeroSpecularFrames"
        }

        return when {
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
            ratio == 0f && zeroSpecularFrames >= 5 ->
                Pair(0.35f, "Ausência persistente de reflexo ($zeroSpecularFrames frames) — possível papel fosco")
            ratio == 0f ->
                Pair(0.60f, null)
            else ->
                Pair(0.65f, null)
        }
    }

    private fun pixelToHsvV(px: Int) = maxOf(Color.red(px), Color.green(px), Color.blue(px))

    // =========================================================================
    // TÉCNICA 2 — Moiré (v5b)
    //
    // Três problemas identificados nos logs com cartão real:
    //
    // PROBLEMA A — FFT@128 macroScreen=true com ratio 12–14:
    //   A 128pt o anel macro (raio 11–30) captura o guilhoché do CC PT.
    //   FIX: macroScreen desativado para fftSize < 256.
    //
    // PROBLEMA B — macroRatio > 10 demasiado permissivo:
    //   Guilhoché do CC PT atinge ratios 10–16 a 256pt e 512pt.
    //   FIX: maxMagMacro > 80.0 (era 40.0) e macroPeakRatio > 20.0 (era 10.0).
    //   Ecrã OLED genuíno produz maxMagMacro >> 100 e ratio >> 20.
    //
    // PROBLEMA C — λc=0.018 demasiado próximo da energia do cartão real:
    //   Cartão real nos logs: energy 0.0087–0.0189.
    //   FIX: λc=0.025. Cartão real fica abaixo; ecrã genuíno tipicamente > 0.030.
    //
    // PROBLEMA D — fftAlert sem energyAlert devolvia fftWorstScore directamente:
    //   Um macroScreen=true isolado resultava em score 0.10 sem confirmação.
    //   FIX: quando só fftAlert, score = max(fftWorstScore, 0.42f).
    // =========================================================================

    private val FFT_SCALES = intArrayOf(128, 256, 512)
    private val MOIRE_ENERGY_LAMBDA = 0.025f // era 0.018 — cartão real ficava em 0.0087–0.0189

    private fun checkMoirePattern(bitmap: Bitmap): Pair<Float, String?> {

        // A) Acumulação de energia laplaciana (Yang et al. 2023)
        val energyScore = moireEnergyAccumulation(bitmap)
        moireEnergyWindow.addLast(energyScore)
        if (moireEnergyWindow.size > 5) moireEnergyWindow.removeFirst()
        val avgMoireEnergy = if (moireEnergyWindow.isNotEmpty())
            moireEnergyWindow.average().toFloat() else 0f

        // B) FFT multi-escala
        var fftWorstScore = 1f
        var fftWorstReason: String? = null
        for (sz in FFT_SCALES) {
            if (sz > minOf(bitmap.width, bitmap.height)) continue
            val (s, r) = checkMoireAtScale(bitmap, sz)
            if (s < fftWorstScore) { fftWorstScore = s; fftWorstReason = r }
        }

        val energyAlert = avgMoireEnergy > MOIRE_ENERGY_LAMBDA
        val fftAlert    = fftWorstScore < 0.50f

        log.d("AntiSpoofing") {
            "[MOIRE] energy=${"%.4f".format(energyScore)} avgEnergy=${"%.4f".format(avgMoireEnergy)}" +
                    " λc=$MOIRE_ENERGY_LAMBDA fftWorst=${"%.2f".format(fftWorstScore)}" +
                    " energyAlert=$energyAlert fftAlert=$fftAlert"
        }

        return when {
            // Confirmação dupla: energia persistente E FFT suspeita
            energyAlert && fftAlert ->
                Pair(minOf(fftWorstScore, 0.15f), fftWorstReason ?: "Padrão Moiré persistente confirmado por energia+FFT")
            // FIX v5b: FFT suspeito sem suporte de energia → score amortecido
            fftAlert ->
                Pair(maxOf(fftWorstScore, 0.42f), fftWorstReason)
            // Energia elevada sem pico FFT → aguarda confirmação
            energyAlert ->
                Pair(0.40f, "Energia Moiré elevada sem pico FFT claro — a aguardar confirmação")
            else ->
                Pair(0.95f, null)
        }
    }

    /**
     * Acumulação de energia Moiré — kernel laplaciano Yang et al. 2023 (Eq.1).
     * Kernel: [-1,-1,-1; -1,8,-1; -1,-1,-1]. Score normalizado [0..1].
     * Valores > λc (0.025) indicam presença de padrão Moiré.
     */
    private fun moireEnergyAccumulation(bitmap: Bitmap): Float {
        val sz = 256
        val gray = bitmapToGrayResized(bitmap, sz, sz)
        var moireEnergy = 0L
        for (r in 1 until sz - 1) {
            for (c in 1 until sz - 1) {
                val center     = gray[r * sz + c] * 8
                val neighbours = gray[(r-1)*sz+(c-1)] + gray[(r-1)*sz+c] + gray[(r-1)*sz+(c+1)] +
                        gray[ r   *sz+(c-1)]                     + gray[ r   *sz+(c+1)] +
                        gray[(r+1)*sz+(c-1)] + gray[(r+1)*sz+c] + gray[(r+1)*sz+(c+1)]
                val response   = center - neighbours
                if (response > 0) moireEnergy += response
            }
        }
        val maxPossible = (sz - 2L) * (sz - 2L) * 8L * 255L
        return (moireEnergy.toFloat() / maxPossible).coerceIn(0f, 1f)
    }

    /**
     * FFT numa escala específica.
     *
     * FIX v5b — macroScreen:
     *   • Desativado para fftSize < 256 (guilhoché activa falsos positivos a 128pt)
     *   • maxMagMacro > 80.0 (era 40.0)
     *   • macroPeakRatio > 20.0 (era 10.0) — guilhoché CC PT atinge 10–16
     *   • macroPeaks in 4..12 (era 4..20)
     */
    private fun checkMoireAtScale(bitmap: Bitmap, fftSize: Int): Pair<Float, String?> {
        val gray = bitmapToGrayResized(bitmap, fftSize, fftSize)
        val real = Array(fftSize) { r -> DoubleArray(fftSize) { c -> gray[r * fftSize + c].toDouble() } }
        val imag = Array(fftSize) { DoubleArray(fftSize) }

        for (row in 0 until fftSize) fft1D(real[row], imag[row])
        for (col in 0 until fftSize) {
            val cr = DoubleArray(fftSize) { real[it][col] }
            val ci = DoubleArray(fftSize) { imag[it][col] }
            fft1D(cr, ci)
            for (row in 0 until fftSize) { real[row][col] = cr[row]; imag[row][col] = ci[row] }
        }

        val mag = Array(fftSize) { r ->
            DoubleArray(fftSize) { c -> sqrt(real[r][c].pow(2) + imag[r][c].pow(2)) }
        }
        fftShift(mag)

        val cx = fftSize / 2; val cy = fftSize / 2
        val sf               = fftSize / 256.0
        val innerMoireRadius = 60.0 * sf
        val macroInner       = 22.0 * sf
        val macroOuter       = 60.0 * sf - 0.5 // exclusivo para evitar sobreposição a 128pt

        var maxMagMoire = 0.0
        var maxMagMacro = 0.0; var sumMagMacro = 0.0; var countMacro = 0

        for (r in 0 until fftSize) {
            for (c in 0 until fftSize) {
                val d = sqrt(((r - cy).toDouble().pow(2) + (c - cx).toDouble().pow(2)))
                val v = mag[r][c]
                if (d > innerMoireRadius) {
                    if (v > maxMagMoire) maxMagMoire = v
                } else if (d >= macroInner && d < macroOuter) {
                    if (v > maxMagMacro) maxMagMacro = v
                    sumMagMacro += v; countMacro++
                }
            }
        }

        val meanMagMacro   = if (countMacro > 0) sumMagMacro / countMacro else 1.0
        val macroPeakRatio = maxMagMacro / meanMagMacro

        val thrMoire = maxMagMoire * 0.95
        val thrMacro = maxMagMacro * 0.92
        var moirePeaks = 0; var macroPeaks = 0

        for (r in 0 until fftSize) {
            for (c in 0 until fftSize) {
                val d = sqrt(((r - cy).toDouble().pow(2) + (c - cx).toDouble().pow(2)))
                val v = mag[r][c]
                if (d > innerMoireRadius && v > thrMoire)              moirePeaks++
                if (d >= macroInner && d < macroOuter && v > thrMacro) macroPeaks++
            }
        }

        // FIX v5b: macroScreen apenas a 256pt+, com limiares mais exigentes
        val isMacroScreen = fftSize >= 256
                && maxMagMacro    > 80.0    // era 40.0
                && macroPeakRatio > 20.0    // era 10.0
                && macroPeaks     in 4..12  // era 4..20

        log.d("AntiSpoofing") {
            "[FFT@$fftSize] moirePeaks=$moirePeaks macroPeaks=$macroPeaks" +
                    " macroRatio=${"%.1f".format(macroPeakRatio)} maxMacro=${"%.0f".format(maxMagMacro)}" +
                    " macroScreen=$isMacroScreen"
        }

        return when {
            isMacroScreen ->
                Pair(0.10f, "Matriz de subpíxeis detetada @${fftSize}pt (ratio=${"%.1f".format(macroPeakRatio)})")
            moirePeaks > 25 ->
                Pair(0.15f, "Padrão Moiré @${fftSize}pt ($moirePeaks picos)")
            moirePeaks > 12 ->
                Pair(0.50f, null)
            else ->
                Pair(0.95f, null)
        }
    }

    // =========================================================================
    // TÉCNICA 3 — Qualidade da Imagem
    // =========================================================================

    private fun checkImageQuality(bitmap: Bitmap): Pair<Float, String?> {
        val gray = bitmapToGray(bitmap); val w = bitmap.width; val h = bitmap.height
        val lap = FloatArray(gray.size)
        for (r in 1 until h - 1) for (c in 1 until w - 1) {
            val i = r * w + c
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
            blur < 12.0     -> Pair(0.60f, null)
            blur > 100.0    -> Pair(1.00f, null)
            else            -> Pair(0.80f, null)
        }
    }

    // =========================================================================
    // TÉCNICA 4 — Giroscópio + OVI
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
                Pair(0.95f, null)
            delta in 10f..25f && colorDelta in 5f..20f ->
                Pair(0.55f, null)
            else ->
                Pair(0.60f, null)
        }
    }

    private fun calculateRotationDeltaDegrees(prev: FloatArray, curr: FloatArray): Float {
        val trace = (0..2).sumOf { i -> (0..2).sumOf { j -> (curr[i*3+j] * prev[i*3+j]).toDouble() } }
        return Math.toDegrees(acos(((trace - 1.0) / 2.0).coerceIn(-1.0, 1.0))).toFloat()
    }

    private fun extractOviRegion(bmp: Bitmap): Bitmap {
        val x = (bmp.width  * 0.55f).toInt()
        val y = (bmp.height * 0.60f).toInt()
        val w = (bmp.width  * 0.25f).toInt().coerceAtLeast(1)
        val h = (bmp.height * 0.25f).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(bmp, x, y, w, h)
    }

    private fun calculateMeanColorDelta(b1: Bitmap, b2: Bitmap): Float {
        val (r1, g1, bc1) = b1.meanRgb(); val (r2, g2, bc2) = b2.meanRgb()
        return sqrt((r1-r2).pow(2) + (g1-g2).pow(2) + (bc1-bc2).pow(2))
    }

    // =========================================================================
    // TÉCNICA 5 — Acelerómetro
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
    // TÉCNICA 6 — Homografia Planar (v5b — implementação corrigida)
    //
    // PROBLEMA na v5: previousCorners era calculado de bitmap.width/height,
    // que são constantes entre frames → dx=0, dy=0, reprojError=0 sempre.
    //
    // FIX v5b: rastreio de centróides de gradiente por quadrante.
    // Divide a imagem em 4 quadrantes e calcula o centróide ponderado pelo
    // módulo do gradiente em cada quadrante (apenas píxeis com gradiente > 20).
    //
    // Para cartão rígido: os 4 centróides transladam em conjunto de forma
    // consistente — erro de reprojeção < 2px.
    // Para papel dobrado: pelo menos um centróide desloca-se de forma
    // inconsistente com a translação global — erro > 6–12px.
    // =========================================================================

    private fun checkHomographyPlanarity(bitmap: Bitmap): Pair<Float, String?> {
        val w = bitmap.width; val h = bitmap.height
        val gray = bitmapToGray(bitmap)
        val halfW = w / 2; val halfH = h / 2

        val currentCentroids = FloatArray(8)
        for (q in 0..3) {
            val r0 = if (q >= 2) halfH else 0
            val r1 = if (q >= 2) h     else halfH
            val c0 = if (q % 2 == 1) halfW else 0
            val c1 = if (q % 2 == 1) w     else halfW

            var sumX = 0.0; var sumY = 0.0; var sumMag = 0.0
            for (r in maxOf(1, r0) until minOf(h - 1, r1)) {
                for (c in maxOf(1, c0) until minOf(w - 1, c1)) {
                    val i   = r * w + c
                    val gx  = gray[i + 1] - gray[i - 1]
                    val gy  = gray[i + w] - gray[i - w]
                    val mag = sqrt(gx * gx + gy * gy)
                    if (mag > 20f) { sumX += c * mag; sumY += r * mag; sumMag += mag }
                }
            }
            currentCentroids[q * 2]     = if (sumMag > 0) (sumX / sumMag).toFloat() else (c0 + c1) / 2f
            currentCentroids[q * 2 + 1] = if (sumMag > 0) (sumY / sumMag).toFloat() else (r0 + r1) / 2f
        }

        val prev = previousGradientCentroids
        previousGradientCentroids = currentCentroids

        if (prev == null) return Pair(0.50f, null)

        // Translação global média
        val dx = (0..3).sumOf { i -> (currentCentroids[i*2]     - prev[i*2]    ).toDouble() }.toFloat() / 4f
        val dy = (0..3).sumOf { i -> (currentCentroids[i*2 + 1] - prev[i*2 + 1]).toDouble() }.toFloat() / 4f

        // Erro de reprojeção por quadrante
        var maxReprojError = 0f
        for (i in 0..3) {
            val errX = currentCentroids[i*2]     - (prev[i*2]     + dx)
            val errY = currentCentroids[i*2 + 1] - (prev[i*2 + 1] + dy)
            maxReprojError = maxOf(maxReprojError, sqrt(errX*errX + errY*errY))
        }

        log.d("AntiSpoofing") {
            "[HOMOGRAPHY] maxReprojError=${"%.2f".format(maxReprojError)}px dx=${"%.1f".format(dx)} dy=${"%.1f".format(dy)}"
        }

        return when {
            maxReprojError > 12f -> Pair(0.35f, "Deformação geométrica — possível papel (${"%.1f".format(maxReprojError)}px)")
            maxReprojError > 6f  -> Pair(0.60f, null)
            maxReprojError < 2f  -> Pair(0.85f, null)
            else                 -> Pair(0.72f, null)
        }
    }

    // =========================================================================
    // TÉCNICA 7 — Nitidez de Arestas
    // =========================================================================

    private fun checkEdgeSharpness(bitmap: Bitmap): Pair<Float, String?> {
        val w = bitmap.width; val h = bitmap.height
        val gray = bitmapToGray(bitmap)
        val gx = FloatArray(gray.size); val gy = FloatArray(gray.size)

        for (r in 1 until h - 1) for (c in 1 until w - 1) {
            val i = r * w + c
            gx[i] = -gray[i-w-1] - 2*gray[i-1] - gray[i+w-1] + gray[i-w+1] + 2*gray[i+1] + gray[i+w+1]
            gy[i] = -gray[i-w-1] - 2*gray[i-w] - gray[i-w+1] + gray[i+w-1] + 2*gray[i+w] + gray[i+w+1]
        }

        val bf  = 0.12f
        val bx1 = (w * bf).toInt(); val bx2 = w - bx1
        val by1 = (h * bf).toInt(); val by2 = h - by1

        var bSum = 0.0; var bN = 0; var iSum = 0.0; var iN = 0
        for (r in 1 until h - 1) for (c in 1 until w - 1) {
            val i   = r * w + c
            val mag = sqrt(gx[i].toDouble().pow(2) + gy[i].toDouble().pow(2))
            if (r !in by1..<by2 || c < bx1 || c >= bx2) { bSum += mag; bN++ }
            else { iSum += mag; iN++ }
        }

        val borderSharp    = if (bN > 0) bSum / bN else 0.0
        val innerSharp     = if (iN > 0) iSum / iN  else 0.0
        val edgeRatio      = if (innerSharp > 0.1) (borderSharp / innerSharp).toFloat() else 1f
        val cardFillsFrame = innerSharp > 8.0

        log.d("AntiSpoofing") {
            "[EDGE] borderSharp=${"%.2f".format(borderSharp)} innerSharp=${"%.2f".format(innerSharp)}" +
                    " ratio=${"%.2f".format(edgeRatio)} fillsFrame=$cardFillsFrame"
        }

        if (cardFillsFrame) {
            return when {
                innerSharp > 15.0 -> Pair(0.85f, null)
                innerSharp > 8.0  -> Pair(0.75f, null)
                else              -> Pair(0.60f, null)
            }
        }

        return when {
            edgeRatio > 1.8f && borderSharp > 15.0 ->
                Pair(0.90f, null)
            borderSharp < 4.0 ->
                Pair(0.30f, "Arestas muito suaves — possível fotocópia (${"%.1f".format(borderSharp)})")
            edgeRatio < 0.7f  ->
                Pair(0.45f, "Gradiente uniforme — suspeito de impressão (ratio=${"%.2f".format(edgeRatio)})")
            else ->
                Pair(0.70f, null)
        }
    }

    // =========================================================================
    // TÉCNICA 8 — Consistência de Cor
    // =========================================================================

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

        val cvR     = if (mR > 1) sdR / mR else 0.0
        val cvG     = if (mG > 1) sdG / mG else 0.0
        val cvB     = if (mB > 1) sdB / mB else 0.0
        val avgCV   = (cvR + cvG + cvB) / 3.0
        val corrRG  = if (sdR > 0 && sdG > 0) (covRG / (sdR * sdG)).coerceIn(-1.0, 1.0) else 0.0
        val corrRB  = if (sdR > 0 && sdB > 0) (covRB / (sdR * sdB)).coerceIn(-1.0, 1.0) else 0.0
        val avgCorr = (corrRG + corrRB) / 2.0

        log.d("AntiSpoofing") {
            "[COLOR] cv=${"%.3f".format(avgCV)} corrRG=${"%.3f".format(corrRG)} corrRB=${"%.3f".format(corrRB)}"
        }

        return when {
            avgCorr > 0.92 ->
                Pair(0.85f, null)
            avgCorr > 0.80 && avgCV in 0.10..0.85 ->
                Pair(0.80f, null)
            avgCV > 0.88 && avgCorr < 0.55 ->
                Pair(0.30f, "Variação de cor muito elevada + canais descorrelacionados — inkjet (CV=${"%.2f".format(avgCV)}, corr=${"%.2f".format(avgCorr)})")
            avgCorr < 0.35 ->
                Pair(0.40f, "Canais descorrelacionados — artefacto CMYK (corr=${"%.2f".format(avgCorr)})")
            else ->
                Pair(0.65f, null)
        }
    }



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
            avgDelta < 1.5        -> Pair(0.15f, "Frames estáticas — possível imagem estática (${"%.1f".format(avgDelta)})")
            avgDelta > 28.0       -> Pair(0.40f, "Movimento excessivo entre frames")
            avgDelta in 2.0..20.0 -> Pair(0.90f, null)
            else                  -> Pair(0.65f, null)
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
    //
    // FIX v5b — HALFTONE:
    // Nos logs: ringPeaks=1248–1797 com e > 600 num cartão REAL.
    // O guilhoché do CC PT é micro-impressão de segurança com padrões de
    // alta frequência — gera centenas de picos de energia moderada no anel.
    //
    // Diferença inkjet vs guilhoché:
    //   Inkjet halftone: poucos picos muito agudos (10–50 pontos com e > 2000)
    //   Guilhoché:       energia difusa (1000+ pontos com e 600–1500, mas <10 com e > 2000)
    //
    // FIX: e > 600 → e > 2000 | ringPeaks > 10 → ringPeaks > 150
    // =========================================================================

    private fun checkPrintArtifacts(bitmap: Bitmap): Pair<Float, String?> {
        val halfScore  = detectHalftonePeaks(bitmap)
        val bandScore  = detectBanding(bitmap)
        val quantScore = detectQuantizationNoise(bitmap)
        val combined   = (halfScore.first + bandScore.first + quantScore.first) / 3f
        val reasons    = listOfNotNull(halfScore.second, bandScore.second, quantScore.second)
        log.d("AntiSpoofing") {
            "[PRINT] halftone=${halfScore.first} band=${bandScore.first} quant=${quantScore.first} → combined=$combined"
        }
        return Pair(combined, reasons.joinToString(" | ").ifEmpty { null })
    }

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
            // FIX v5b: magnitude mínima 600 → 2000 para excluir guilhoché
            if (d in 18.0..32.0) { ringE += e; if (e > 2000) ringPeaks++ }
        }

        val ringRatio = if (totalE > 0) ringE / totalE else 0.0

        log.d("AntiSpoofing") { "[HALFTONE] ringPeaks=$ringPeaks ringRatio=${"%.3f".format(ringRatio)}" }

        return when {
            // FIX v5b: limiar 10 → 150
            ringPeaks > 150 || ringRatio > 0.55 ->
                Pair(0.25f, "Halftone inkjet detetado ($ringPeaks picos, ratio=${"%.2f".format(ringRatio)})")
            ringPeaks > 50 ->
                Pair(0.55f, null)
            else ->
                Pair(0.90f, null)
        }
    }

    private fun detectBanding(bitmap: Bitmap): Pair<Float, String?> {
        val w = bitmap.width; val h = bitmap.height; val gray = bitmapToGray(bitmap)
        val means = DoubleArray(h) { r -> var s = 0.0; for (c in 0 until w) s += gray[r*w+c]; s / w }
        var bVar = 0.0
        for (i in 1 until h) bVar += (means[i] - means[i-1]).pow(2)
        bVar /= (h - 1)
        return when {
            bVar > 40.0 -> Pair(0.30f, "Banding horizontal (var=${"%.1f".format(bVar)})")
            bVar < 5.0  -> Pair(0.90f, null)
            else        -> Pair(0.70f, null)
        }
    }

    private fun detectQuantizationNoise(bitmap: Bitmap): Pair<Float, String?> {
        val w = bitmap.width; val h = bitmap.height; val gray = bitmapToGray(bitmap)
        var abrupt = 0; var total = 0
        for (r in 1 until h - 1) for (c in 1 until w - 1) {
            val i = r * w + c
            if (abs(gray[i] - gray[i+1]) + abs(gray[i] - gray[i+w]) > 60f) abrupt++
            total++
        }
        val noiseR = abrupt.toFloat() / total
        return when {
            noiseR > 0.12f -> Pair(0.35f, "Quantization noise — impressão (${"%.3f".format(noiseR)})")
            noiseR < 0.03f -> Pair(0.85f, null)
            else           -> Pair(0.65f, null)
        }
    }

    // =========================================================================
    // FFT 1D Cooley-Tukey
    // =========================================================================

    private fun fft1D(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        require(n > 0 && (n and (n - 1)) == 0) { "FFT: potência de 2" }
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                real[i] = real[j].also { real[j] = real[i] }
                imag[i] = imag[j].also { imag[j] = imag[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2; val ang = -2.0 * PI / len
            val wBR = cos(ang); val wBI = sin(ang)
            var i = 0
            while (i < n) {
                var wR = 1.0; var wI = 0.0
                for (k in 0 until half) {
                    val uR = real[i+k]; val uI = imag[i+k]
                    val vR = real[i+k+half]*wR - imag[i+k+half]*wI
                    val vI = real[i+k+half]*wI + imag[i+k+half]*wR
                    real[i+k] = uR+vR; imag[i+k] = uI+vI
                    real[i+k+half] = uR-vR; imag[i+k+half] = uI-vI
                    val nw = wR*wBR - wI*wBI; wI = wR*wBI + wI*wBR; wR = nw
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun fftShift(mag: Array<DoubleArray>) {
        val rows = mag.size; val cols = mag[0].size
        val cx = cols / 2; val cy = rows / 2
        for (r in 0 until cy) for (c in 0 until cx) {
            mag[r][c] = mag[r+cy][c+cx].also { mag[r+cy][c+cx] = mag[r][c] }
            mag[r][c+cx] = mag[r+cy][c].also { mag[r+cy][c] = mag[r][c+cx] }
        }
    }

    override fun release() {
        accelerometerHistory.clear()
        frameHashHistory.clear()
        moireEnergyWindow.clear()
        previousBitmapForOVI      = null
        previousRotationMatrix    = null
        previousGradientCentroids = null
        zeroSpecularFrames        = 0
    }
}