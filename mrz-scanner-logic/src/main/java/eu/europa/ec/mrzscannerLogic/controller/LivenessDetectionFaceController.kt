package eu.europa.ec.mrzscannerLogic.controller

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.core.graphics.scale
import androidx.lifecycle.LifecycleOwner
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.mrzscannerLogic.config.FaceAnalyser
import eu.europa.ec.mrzscannerLogic.config.FaceError
import eu.europa.ec.mrzscannerLogic.model.FaceGeometry
import eu.europa.ec.mrzscannerLogic.model.LivenessThresholds
import eu.europa.ec.mrzscannerLogic.model.ScanType
import eu.europa.ec.mrzscannerLogic.service.CameraFrontService
import eu.europa.ec.mrzscannerLogic.service.FaceNetService
import eu.europa.ec.mrzscannerLogic.service.FaceVerificationResult
import eu.europa.ec.mrzscannerLogic.utils.ImageUtils.bitmapToJpeg
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.atan2


sealed class LivenessUpdate {
    data class ActiveFrame(
        val challengeState: ChallengeState,
        val features: FaceFeatures,
        val bitmap: Bitmap? = null
    ) : LivenessUpdate()

    data class SessionResult(val result: LivenessResult) : LivenessUpdate()
}

data class FaceFeatures(
    val boundingBox: Rect = Rect(),
    val faceOval: List<PointF> = emptyList(),
    val leftEye: List<PointF> = emptyList(),
    val rightEye: List<PointF> = emptyList(),
    val leftEyebrowTop: List<PointF> = emptyList(),
    val leftEyebrowBottom: List<PointF> = emptyList(),
    val rightEyebrowTop: List<PointF> = emptyList(),
    val rightEyebrowBottom: List<PointF> = emptyList(),
    val noseBridge: List<PointF> = emptyList(),
    val noseBottom: List<PointF> = emptyList(),
    val upperLipTop: List<PointF> = emptyList(),
    val upperLipBottom: List<PointF> = emptyList(),
    val lowerLipTop: List<PointF> = emptyList(),
    val lowerLipBottom: List<PointF> = emptyList(),
    val imageWidth: Int = 1,
    val imageHeight: Int = 1
)

enum class Challenge {
    LOOK_LEFT, LOOK_RIGHT, BLINK, SMILE, OPEN_MOUTH, NOD
}

sealed class ChallengeState {
    object Idle : ChallengeState()

    data class Pending(
        val challenge: Challenge,
        val deadlineMs: Long
    ) : ChallengeState()

    data class Countdown(val seconds: Int) : ChallengeState()
    data class Passed(val challenge: Challenge) : ChallengeState()

    data class Failed(val challenge: Challenge, val reason: String) : ChallengeState()
}

sealed class LivenessResult {
    object InProgress : LivenessResult()

    /** All challenges passed. [capturedJpeg] is the selfie. Never empty on success. */
    data class Success(
        val capturedJpeg: ByteArray,
        val verification: FaceVerificationResult? = null
    ) : LivenessResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Success

            if (!capturedJpeg.contentEquals(other.capturedJpeg)) return false

            return true
        }

        override fun hashCode(): Int {
            return capturedJpeg.contentHashCode()
        }
    }

    data class Failure(val reason: String) : LivenessResult()
}

interface LivenessDetectionFaceController {
    fun startScanning(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        scanType: ScanType,
    ): Flow<LivenessUpdate>

    fun stopScanning()
    fun isScanning(): Boolean
    fun saveSelfie(jpegBytes: ByteArray): String?
    fun getSelfieBytes(): ByteArray?
    fun clearSelfie()

}


class LivenessDetectionFaceControllerImpl(
    private val cameraFrontService: CameraFrontService,
    private val logController: LogController,
    private val numberOfChallenges: Int = 3,
    private val context: ResourceProvider,
    private val configLogic: ConfigLogic,
    private val faceNetService: FaceNetService,
) : LivenessDetectionFaceController {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var sessionChallenges: List<Challenge> = emptyList()
    private var currentIndex = 0
    private var sessionJob: Job? = null

    private val _challengeState = MutableStateFlow<ChallengeState>(ChallengeState.Idle)
    private val _livenessResult = MutableStateFlow<LivenessResult>(LivenessResult.InProgress)

    private val _livenessUpdate = MutableSharedFlow<LivenessUpdate>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @Volatile private var latestFaceBoundingBox: FaceFeatures? = null

    private var lastUpdateMs: Long = 0
    private var baselineYaw: Float? = null
    private var baselinePitch: Float? = null
    private var eyesWereClosed = false

    @Volatile private var latestFrameBitmap: Bitmap? = null

    private val selfieFile: File
        get() = File(context.provideContext().filesDir, "auto_self.jpg")

    private val faceRefFile: File
        get() = File(context.provideContext().filesDir, "face_ref.jpg")

    override fun startScanning(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        scanType: ScanType
    ): Flow<LivenessUpdate> {
        stopScanning()
        reset()
        currentIndex = 0
        sessionChallenges = Challenge.entries.shuffled().take(numberOfChallenges)

        val analyser = FaceAnalyser(
            onFaceDetected = { geometry, features, bitmap ->
                bitmap?.let { latestFrameBitmap = it }

                if (features.boundingBox.width() > 0) {
                    latestFaceBoundingBox = features
                }
                onNewFrame(geometry)

                val currentState = _challengeState.value
                val now = System.currentTimeMillis()
                if (now - lastUpdateMs > 60) {
                    _livenessUpdate.tryEmit(
                        LivenessUpdate.ActiveFrame(currentState, features, bitmap)
                    )
                    lastUpdateMs = now
                }
            },
            onError = { errorType ->
                val currentState = _challengeState.value
                if (currentState is ChallengeState.Pending) {
                    val reason = when (errorType) {
                        FaceError.NO_FACE -> "No faces detected on camera."
                        FaceError.MULTIPLE_FACES -> "Multiple faces detected. Capture requires uniqueness."
                    }
                    val failedState = ChallengeState.Failed(currentState.challenge, reason)
                    _challengeState.value = failedState
                    _livenessUpdate.tryEmit(LivenessUpdate.ActiveFrame(failedState, FaceFeatures(), latestFrameBitmap))
                    sessionJob?.cancel()
                    scope.launch {
                        delay(2000)
                        issueNextChallenge()
                    }
                }
            }
        )

        val analysisUseCase = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(Executors.newSingleThreadExecutor(), analyser) }

        val previewUseCase = Preview.Builder()
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

        cameraFrontService.start(lifecycleOwner, previewView, analysisUseCase, previewUseCase)

        issueNextChallenge()
        _livenessUpdate.tryEmit(LivenessUpdate.SessionResult(LivenessResult.InProgress))

        return _livenessUpdate.asSharedFlow()
    }


    override fun stopScanning() {
        sessionJob?.cancel()
        cameraFrontService.stop()
        faceNetService.close()
        reset()
    }

    override fun isScanning(): Boolean = cameraFrontService.isRunning()


    override fun saveSelfie(jpegBytes: ByteArray): String? {
        return try {
            selfieFile.writeBytes(jpegBytes)
            selfieFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    override fun getSelfieBytes(): ByteArray? {
        return try {
            if (selfieFile.exists()) selfieFile.readBytes() else null
        } catch (e: Exception) {
            null
        }
    }

    override fun clearSelfie() {
        if (selfieFile.exists()) selfieFile.delete()
    }

    fun onNewFrame(geometry: FaceGeometry) {
        val state = _challengeState.value
        if (state !is ChallengeState.Pending) return

        val validated = when (state.challenge) {
            Challenge.LOOK_LEFT  -> checkLookLeft(geometry)
            Challenge.LOOK_RIGHT -> checkLookRight(geometry)
            Challenge.BLINK      -> checkBlink(geometry)
            Challenge.SMILE      -> checkSmile(geometry)
            Challenge.OPEN_MOUTH -> checkOpenMouth(geometry)
            Challenge.NOD        -> checkNod(geometry)
        }

        if (validated) onChallengePassed(state.challenge)
    }


    private fun issueNextChallenge() {
        if (currentIndex >= sessionChallenges.size) {
            initiateFinalCaptureSequence()
            return
        }

        val next     = sessionChallenges[currentIndex]
        val deadline = System.currentTimeMillis() + LivenessThresholds.CHALLENGE_TIMEOUT_MS
        _challengeState.value = ChallengeState.Pending(next, deadline)

        baselineYaw   = null
        baselinePitch = null

        sessionJob?.cancel()
        sessionJob = scope.launch {
            delay(LivenessThresholds.CHALLENGE_TIMEOUT_MS)

            val currentState = _challengeState.value
            if (currentState is ChallengeState.Pending) {
                val failedState = ChallengeState.Failed(
                    currentState.challenge,
                    "No movement detected. We'll try again."
                )
                _challengeState.value = failedState
                _livenessUpdate.tryEmit(LivenessUpdate.ActiveFrame(failedState, FaceFeatures()))

                delay(2000)
                issueNextChallenge()
            }
        }
    }

    private fun initiateFinalCaptureSequence() {
        sessionJob?.cancel()
        sessionJob = scope.launch {

            for (secondsLeft in 3 downTo 1) {
                _challengeState.value = ChallengeState.Countdown(secondsLeft)
                delay(1000)
            }

            val currentBitmap = latestFrameBitmap
            val faceBbox      = latestFaceBoundingBox

            if (currentBitmap == null) {
                emit(LivenessResult.Failure("Failed to extract final logical frame."))
                return@launch
            }

            val jpegBytes = bitmapToJpeg(currentBitmap)

            val liveCrop: Bitmap = if (faceBbox != null && faceBbox.boundingBox.width() > 0)
                alignAndCropFace(currentBitmap, faceBbox)
            else
                currentBitmap

            val verification: FaceVerificationResult? = runCatching {

                if (faceRefFile.exists()) {
                    val refCrop = BitmapFactory.decodeByteArray(
                        faceRefFile.readBytes(), 0, faceRefFile.length().toInt()
                    )

                    faceNetService.loadModel()
                    faceNetService.setReferenceIdentity(refCrop)
                    faceNetService.verifyIdentity(liveCrop)

                } else {
                    faceRefFile.writeBytes(bitmapToJpeg(liveCrop))
                    selfieFile.writeBytes(jpegBytes)
                    null
                }

            }.getOrNull()

            emit(LivenessResult.Success(capturedJpeg = jpegBytes, verification = verification))
        }
    }

    // Helper para não repetir o bloco de emissão
    private fun CoroutineScope.emit(result: LivenessResult) {
        _livenessResult.value = result
        _challengeState.value = ChallengeState.Idle
        _livenessUpdate.tryEmit(LivenessUpdate.SessionResult(result))
        cameraFrontService.stop()
    }

    private fun onChallengePassed(challenge: Challenge) {
        sessionJob?.cancel()
        _challengeState.value = ChallengeState.Passed(challenge)
        currentIndex++
        scope.launch {
            delay(600)
            issueNextChallenge()
        }
    }


    fun reset() {
        sessionJob?.cancel()
        _challengeState.value  = ChallengeState.Idle
        _livenessResult.value  = LivenessResult.InProgress
        currentIndex           = 0
        baselineYaw            = null
        baselinePitch          = null
        eyesWereClosed         = false
        lastUpdateMs           = 0
        latestFrameBitmap      = null
    }


    private fun checkLookLeft(g: FaceGeometry) =
        g.yawDeg > LivenessThresholds.YAW_THRESHOLD_DEG

    private fun checkLookRight(g: FaceGeometry) =
        g.yawDeg < -LivenessThresholds.YAW_THRESHOLD_DEG

    private fun checkNod(g: FaceGeometry): Boolean {
        val base = baselinePitch ?: run { baselinePitch = g.pitchDeg; g.pitchDeg }
        return Math.abs(g.pitchDeg - base) > LivenessThresholds.PITCH_THRESHOLD_DEG
    }

    /**
     * Blink detection: EAR must fall below threshold for [BLINK_CONSEC_FRAMES]
     * and then recover, OR the ML Kit eye-open probability must drop near zero.
     */

    private fun checkBlink(g: FaceGeometry): Boolean {
        val closed = (g.earLeft < 0.28f && g.earRight < 0.28f)
                || (g.leftEyeOpenProb < 0.3f && g.rightEyeOpenProb < 0.3f)

        return if (closed) {
            eyesWereClosed = true; false
        } else {
            if (eyesWereClosed) { eyesWereClosed = false; true } else false
        }
    }

    private fun checkSmile(g: FaceGeometry) =
        g.smileProb > LivenessThresholds.SMILE_PROB_THRESHOLD

    private fun checkOpenMouth(g: FaceGeometry) =
        g.mar > LivenessThresholds.MAR_OPEN_THRESHOLD


    private fun alignAndCropFace(frame: Bitmap, features: FaceFeatures): Bitmap {
        if (features.leftEye.isEmpty() || features.rightEye.isEmpty()) {
            return cropSquareFace(frame, features.boundingBox)
        }

        val leftEyeCenter = PointF(
            features.leftEye.map { it.x }.average().toFloat(),
            features.leftEye.map { it.y }.average().toFloat()
        )
        val rightEyeCenter = PointF(
            features.rightEye.map { it.x }.average().toFloat(),
            features.rightEye.map { it.y }.average().toFloat()
        )

        val deltaY = rightEyeCenter.y - leftEyeCenter.y
        val deltaX = rightEyeCenter.x - leftEyeCenter.x
        val angleDegrees = Math.toDegrees(atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat()

        val matrix = android.graphics.Matrix()
        matrix.postRotate(-angleDegrees, frame.width / 2f, frame.height / 2f)

        val rotatedFrame = Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
        return cropSquareFace(rotatedFrame, features.boundingBox)
    }

    private fun cropSquareFace(frame: Bitmap, box: Rect): Bitmap {
        val centerX = box.centerX()
        val centerY = box.centerY()
        val size = box.width().coerceAtLeast(box.height())
        val padding = (size * 0.20f).toInt()
        val finalSize = size + padding

        val left = (centerX - finalSize / 2).coerceIn(0, frame.width)
        val top = (centerY - finalSize / 2).coerceIn(0, frame.height)
        val width = (left + finalSize).coerceAtMost(frame.width) - left
        val height = (top + finalSize).coerceAtMost(frame.height) - top

        val minEdge = width.coerceAtMost(height)
        return Bitmap.createBitmap(frame, left, top, minEdge, minEdge)
    }
}