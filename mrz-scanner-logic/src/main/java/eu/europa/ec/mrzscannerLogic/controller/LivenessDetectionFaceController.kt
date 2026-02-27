package eu.europa.ec.mrzscannerLogic.controller

import android.graphics.PointF
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.mrzscannerLogic.config.FaceAnalyser
import eu.europa.ec.mrzscannerLogic.model.FaceGeometry
import eu.europa.ec.mrzscannerLogic.model.LivenessThresholds
import eu.europa.ec.mrzscannerLogic.model.ScanType
import eu.europa.ec.mrzscannerLogic.service.CameraFrontService
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

sealed class LivenessUpdate {
    data class ActiveFrame(
        val challengeState: ChallengeState,
        val features: FaceFeatures
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
    LOOK_LEFT,
    LOOK_RIGHT,
    BLINK,
    SMILE,
    OPEN_MOUTH,
    NOD
}

/** Current execution state of a single [Challenge]. */
sealed class ChallengeState {
    /** No challenge is active; waiting to start. */
    object Idle : ChallengeState()

    /** A challenge has been issued and we are waiting for the correct gesture. */
    data class Pending(
        val challenge: Challenge,
        val deadlineMs: Long          // System.currentTimeMillis() + timeout
    ) : ChallengeState()

    /** The gesture was successfully validated within the deadline. */
    data class Passed(val challenge: Challenge) : ChallengeState()

    /** The gesture was NOT performed before the deadline elapsed. */
    data class Failed(val challenge: Challenge, val reason: String) : ChallengeState()
}

/** Overall result of the complete multi-step liveness session. */
sealed class LivenessResult {
    /** Still collecting challenge responses. */
    object InProgress : LivenessResult()

    /**
     * All challenges passed. [capturedJpeg] is the selfie bytes that were
     * captured silently once liveness was confirmed.
     */
    data class Success(val capturedJpeg: ByteArray) : LivenessResult()

    /** One or more challenges failed or timed-out. */
    data class Failure(val reason: String) : LivenessResult()
}


interface LivenessDetectionFaceController{
    /**
     * Inicia o processo de scanning
     * @return [LivenessUpdate] Flow de estados do processo
     */
    fun startScanning(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        scanType: ScanType,
    ): Flow<LivenessUpdate>

    /**
     * Para o processo de scanning e libera recursos
     */
    fun stopScanning()

    /**
     * Verifica se a câmera está disponível no dispositivo
     */
    fun isCameraAvailable(): Boolean

    /**
     * Verifica se o scanning está ativo
     */
    fun isScanning(): Boolean

    /**
     * Ativa/desativa tap-to-focus
     */
    fun enableTapToFocus(enabled: Boolean)
}

class LivenessDetectionFaceControllerImpl(
    private val cameraFrontService: CameraFrontService,
    private val logController: LogController,
    private val numberOfChallenges: Int = 3
): LivenessDetectionFaceController{

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
    private var baselineYaw: Float? = null
    private var baselinePitch: Float? = null
    private var blinkConsecCount = 0
    private var eyesWereClosed = false


    override fun startScanning(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        scanType: ScanType
    ): Flow<LivenessUpdate> {
        reset()
        currentIndex = 0
        sessionChallenges = Challenge.entries.shuffled().take(numberOfChallenges)

        val analyser = FaceAnalyser { geometry, features ->
            onNewFrame(geometry)
            val currentState = _challengeState.value

            _livenessUpdate.tryEmit(LivenessUpdate.ActiveFrame(currentState, features))
        }

        val analysisUseCase = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(Executors.newSingleThreadExecutor(), analyser) }

        cameraFrontService.start(lifecycleOwner, previewView, analysisUseCase)
        issueNextChallenge()

        _livenessUpdate.tryEmit(LivenessUpdate.SessionResult(LivenessResult.InProgress))

        return _livenessUpdate.asSharedFlow()
    }

    /**
     * Must be called for every new camera frame while a session is active.
     * Thread-safe: may be called from a background analyser thread.
     */
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


    fun reset() {
        sessionJob?.cancel()
        _challengeState.value = ChallengeState.Idle
        _livenessResult.value = LivenessResult.InProgress
    }


    private fun issueNextChallenge() {
        if (currentIndex >= sessionChallenges.size) {
            _livenessResult.value = LivenessResult.Success(ByteArray(0))
            _challengeState.value = ChallengeState.Idle
            return
        }

        val next = sessionChallenges[currentIndex]
        val deadline = System.currentTimeMillis() + LivenessThresholds.CHALLENGE_TIMEOUT_MS
        _challengeState.value = ChallengeState.Pending(next, deadline)


        baselineYaw = null
        baselinePitch = null


        sessionJob?.cancel()
        sessionJob = scope.launch {
            delay(LivenessThresholds.CHALLENGE_TIMEOUT_MS)
            val currentState = _challengeState.value
            if (currentState is ChallengeState.Pending) {
                val failureResult = LivenessResult.Failure("No movement was detected by the camera. Follow the instructions.")
                val failedState = ChallengeState.Failed(currentState.challenge, "No movement was detected by the camera. Follow the instructions.")
                _challengeState.value = failedState

                _livenessUpdate.tryEmit(LivenessUpdate.ActiveFrame(failedState, FaceFeatures()))

                delay(1000)

                _livenessResult.value = failureResult
                _livenessUpdate.tryEmit(LivenessUpdate.SessionResult(failureResult))
            }
        }
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

    override fun stopScanning() {
        sessionJob?.cancel()
        cameraFrontService.stop()
        reset()
    }
    override fun isCameraAvailable(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isScanning(): Boolean = cameraFrontService.isRunning()

    override fun enableTapToFocus(enabled: Boolean) {
        TODO("Not yet implemented")
    }

    private fun checkLookLeft(g: FaceGeometry): Boolean {
        return g.yawDeg > LivenessThresholds.YAW_THRESHOLD_DEG
    }

    private fun checkLookRight(g: FaceGeometry): Boolean {
        return g.yawDeg < -LivenessThresholds.YAW_THRESHOLD_DEG
    }

    private fun checkNod(g: FaceGeometry): Boolean {
        val base = baselinePitch ?: run { baselinePitch = g.pitchDeg; g.pitchDeg }
        return Math.abs(g.pitchDeg - base) > LivenessThresholds.PITCH_THRESHOLD_DEG
    }

    /**
     * Blink detection: EAR must fall below threshold for [BLINK_CONSEC_FRAMES]
     * and then recover, OR the ML Kit eye-open probability must drop near zero.
     */
    private fun checkBlink(g: FaceGeometry): Boolean {
        val isClosedGeometrically = g.earLeft < 0.28f && g.earRight < 0.28f

        val isClosedProbabilistically = g.leftEyeOpenProb < 0.3f && g.rightEyeOpenProb < 0.3f

        val currentlyClosed = isClosedGeometrically || isClosedProbabilistically

        return if (currentlyClosed) {
            eyesWereClosed = true
            false
        } else {
            if (eyesWereClosed) {
                eyesWereClosed = false
                true
            } else {
                false
            }
        }
    }

    /**
     * Smile Detection (SMILE):
     * Uses the ML Kit's neural network classification, which assesses the tension
     * of the zygomatic muscles, regardless of whether the mouth is open or closed.
     */
    private fun checkSmile(g: FaceGeometry): Boolean {
        return g.smileProb > LivenessThresholds.SMILE_PROB_THRESHOLD
    }

    /**
     * Mouth Opening Detection (OPEN_MOUTH):
     * Uses the geometric calculation of the MAR (Mouth Aspect Ratio) on the internal contours.
     * Ignores emotional expression and focuses exclusively on the mechanical displacement of the jaw.
     */
    private fun checkOpenMouth(g: FaceGeometry): Boolean {
        return g.mar > LivenessThresholds.MAR_OPEN_THRESHOLD
    }
}