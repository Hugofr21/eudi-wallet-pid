package eu.europa.ec.mrzscannerLogic.config

import android.graphics.PointF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import coil3.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import eu.europa.ec.mrzscannerLogic.controller.FaceFeatures
import eu.europa.ec.mrzscannerLogic.model.FaceGeometry
import kotlin.math.abs
import kotlin.math.sqrt


enum class FaceError {
    NO_FACE,
    MULTIPLE_FACES
}

/**
 * ML Kit face analyser.
 *
 * FIX: removed the bogus `onBitmapAvailable: Bitmap` constructor parameter.
 *      The bitmap is now captured directly from [ImageProxy] inside [analyze]
 *      and forwarded as the third argument of [onFaceDetected].
 *
 * @param onFaceDetected called with (geometry, features, frameBitmap) on every
 *   frame where exactly one face is detected. [frameBitmap] may be null if the
 *   conversion fails (e.g. unsupported format) — callers must guard for null.
 * @param onError called when zero or more than one face is detected.
 */
class FaceAnalyser(
    private val onFaceDetected: (geometry: FaceGeometry, features: FaceFeatures, bitmap: Bitmap?) -> Unit,
    private val onError: (errorType: FaceError) -> Unit
) : ImageAnalysis.Analyzer {

    private var isProcessing = false

    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.25f)
            .enableTracking()
            .build()
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        if (isProcessing) {
            image.close()
            return
        }

        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }

        isProcessing = true

        val rotationDegrees = image.imageInfo.rotationDegrees

        val frameBitmap: Bitmap? = runCatching { image.toBitmap() }.getOrNull()

        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        val isPortrait = rotationDegrees == 90 || rotationDegrees == 270
        val imgWidth  = if (isPortrait) image.height else image.width
        val imgHeight = if (isPortrait) image.width  else image.height

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                when (faces.size) {
                    0    -> onError(FaceError.NO_FACE)
                    1    -> processFace(faces[0], imgWidth, imgHeight, frameBitmap)
                    else -> onError(FaceError.MULTIPLE_FACES)
                }
            }
            .addOnCompleteListener {
                isProcessing = false
                image.close()
            }
    }


    private fun processFace(face: Face, imgWidth: Int, imgHeight: Int, bitmap: Bitmap?) {
        val yaw   = face.headEulerAngleY
        val pitch = face.headEulerAngleX
        val roll  = face.headEulerAngleZ

        val earLeft  = computeEAR(face, FaceContour.LEFT_EYE)
        val earRight = computeEAR(face, FaceContour.RIGHT_EYE)
        val mar      = computeMAR(face)

        val leftEyeOpenProb  = face.leftEyeOpenProbability  ?: 1f
        val rightEyeOpenProb = face.rightEyeOpenProbability ?: 1f
        val smileProb        = face.smilingProbability      ?: 0f
        val asymmetry        = computeAsymmetryScore(face)

        val geometry = FaceGeometry(
            yawDeg           = yaw,
            pitchDeg         = pitch,
            rollDeg          = roll,
            earLeft          = earLeft,
            earRight         = earRight,
            mar              = mar,
            leftEyeOpenProb  = leftEyeOpenProb,
            rightEyeOpenProb = rightEyeOpenProb,
            asymmetryScore   = asymmetry,
            smileProb        = smileProb
        )

        val features = FaceFeatures(
            boundingBox       = face.boundingBox,
            faceOval          = face.getContour(FaceContour.FACE)?.points              ?: emptyList(),
            leftEye           = face.getContour(FaceContour.LEFT_EYE)?.points          ?: emptyList(),
            rightEye          = face.getContour(FaceContour.RIGHT_EYE)?.points         ?: emptyList(),
            leftEyebrowTop    = face.getContour(FaceContour.LEFT_EYEBROW_TOP)?.points  ?: emptyList(),
            leftEyebrowBottom = face.getContour(FaceContour.LEFT_EYEBROW_BOTTOM)?.points ?: emptyList(),
            rightEyebrowTop   = face.getContour(FaceContour.RIGHT_EYEBROW_TOP)?.points ?: emptyList(),
            rightEyebrowBottom= face.getContour(FaceContour.RIGHT_EYEBROW_BOTTOM)?.points ?: emptyList(),
            noseBridge        = face.getContour(FaceContour.NOSE_BRIDGE)?.points       ?: emptyList(),
            noseBottom        = face.getContour(FaceContour.NOSE_BOTTOM)?.points       ?: emptyList(),
            upperLipTop       = face.getContour(FaceContour.UPPER_LIP_TOP)?.points     ?: emptyList(),
            upperLipBottom    = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points  ?: emptyList(),
            lowerLipTop       = face.getContour(FaceContour.LOWER_LIP_TOP)?.points     ?: emptyList(),
            lowerLipBottom    = face.getContour(FaceContour.LOWER_LIP_BOTTOM)?.points  ?: emptyList(),
            imageWidth        = imgWidth,
            imageHeight       = imgHeight
        )

        onFaceDetected(geometry, features, bitmap)
    }


    private fun computeEAR(face: Face, contourType: Int): Float {
        val pts = face.getContour(contourType)?.points ?: return 0f
        if (pts.size < 13) return 0f

        val p1 = pts[0];  val p2 = pts[4]
        val p3 = pts[6];  val p4 = pts[8]
        val p5 = pts[10]; val p6 = pts[12]

        val vertical1  = euclidean(p2, p6)
        val vertical2  = euclidean(p3, p5)
        val horizontal = euclidean(p1, p4)

        return if (horizontal < 1e-4f) 0f
        else (vertical1 + vertical2) / (2f * horizontal)
    }

    private fun computeMAR(face: Face): Float {
        val upperLipTop = face.getContour(FaceContour.UPPER_LIP_TOP)?.points ?: return 0f
        if (upperLipTop.size < 3) return 0f

        val leftCorner  = upperLipTop.first()
        val rightCorner = upperLipTop.last()
        val horizontal  = euclidean(leftCorner, rightCorner)
        if (horizontal < 1e-4f) return 0f

        val upperLipInner = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points ?: return 0f
        val lowerLipInner = face.getContour(FaceContour.LOWER_LIP_TOP)?.points    ?: return 0f
        if (upperLipInner.isEmpty() || lowerLipInner.isEmpty()) return 0f

        val upperCenter = upperLipInner[upperLipInner.size / 2]
        val lowerCenter = lowerLipInner[lowerLipInner.size / 2]
        val vertical    = euclidean(upperCenter, lowerCenter)

        return vertical / horizontal
    }

    private fun computeAsymmetryScore(face: Face): Float {
        val earL     = computeEAR(face, FaceContour.LEFT_EYE)
        val earR     = computeEAR(face, FaceContour.RIGHT_EYE)
        val earRatio = if (earR < 1e-4f) 1f else earL / earR
        val earAsymmetry = abs(earRatio - 1f).coerceIn(0f, 1f)

        val noseTip = face.getContour(FaceContour.NOSE_BRIDGE)?.points?.lastOrNull()
        val bbox    = face.boundingBox
        val noseTipScore = if (noseTip != null && bbox.width() > 0) {
            abs(noseTip.x - bbox.exactCenterX()) / bbox.width().toFloat()
        } else 0f

        return ((earAsymmetry * 0.5f + noseTipScore * 0.5f) * 2f).coerceIn(0f, 1f)
    }

    private fun euclidean(a: PointF, b: PointF): Float {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        return sqrt(dx * dx + dy * dy).toFloat()
    }

    fun close() = detector.close()
}