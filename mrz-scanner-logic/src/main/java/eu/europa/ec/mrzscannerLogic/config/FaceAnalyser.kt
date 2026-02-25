package eu.europa.ec.mrzscannerLogic.config

import android.graphics.PointF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.abs
import kotlin.math.sqrt

class FaceAnalyser (

): ImageAnalysis.Analyzer{

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
        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            image.imageInfo.rotationDegrees
        )

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.size == 1) {
                    processFace(faces[0])
                }
            }
            .addOnCompleteListener {
                image.close()
            }
    }

    private fun processFace(face: Face) {
        val x = face.headEulerAngleX
        val y = face.headEulerAngleY
        val z = face.headEulerAngleZ
        val b = face.boundingBox.bottom
        val h = face.boundingBox.height()
        val w = face.boundingBox.width()

        val earLeft  = computeEAR(face, FaceContour.LEFT_EYE) // eyes left
        val earRight = computeEAR(face, FaceContour.RIGHT_EYE) // eyes right

        val mar      = computeMAR(face)

        val leftEyeOpenProb  = face.leftEyeOpenProbability  ?: 1f
        val rightEyeOpenProb = face.rightEyeOpenProbability ?: 1f

        val asymmetry = computeAsymmetryScore(face)


    }
    private fun computeEAR(face: Face, contourType: Int): Float {
        val pts = face.getContour(contourType)?.points ?: return 0f
        if (pts.size < 13) return 0f

        val p1 = pts[0];  val p2 = pts[4]
        val p3 = pts[6];  val p4 = pts[8]
        val p5 = pts[10]; val p6 = pts[12]

        val vertical1 = euclidean(p2, p6)
        val vertical2 = euclidean(p3, p5)
        val horizontal = euclidean(p1, p4)

        return if (horizontal < 1e-4f) 0f
        else (vertical1 + vertical2) / (2f * horizontal)
    }

    private fun computeMAR(face: Face): Float {
        val upper = face.getContour(FaceContour.UPPER_LIP_TOP)?.points ?: return 0f
        val lower = face.getContour(FaceContour.LOWER_LIP_BOTTOM)?.points ?: return 0f

        if (upper.isEmpty() || lower.isEmpty()) return 0f

        // Vertical: midpoint of upper lip top vs. midpoint of lower lip bottom
        val upperMid = midpoint(upper)
        val lowerMid = midpoint(lower)
        val vertical = euclidean(upperMid, lowerMid)

        // Horizontal: first to last point of the upper lip contour
        val horizontal = euclidean(upper.first(), upper.last())

        return if (horizontal < 1e-4f) 0f else vertical / horizontal
    }


    private fun computeAsymmetryScore(face: Face): Float {
        val earL = computeEAR(face, FaceContour.LEFT_EYE)
        val earR = computeEAR(face, FaceContour.RIGHT_EYE)

        // EAR ratio asymmetry: real faces show more difference under yaw
        val earRatio = if (earR < 1e-4f) 1f else earL / earR
        // Normalise: ratio of 1.0 means perfect symmetry → low "liveness" signal
        // We invert: large deviation from 1.0 means more depth cue
        val earAsymmetry = abs(earRatio - 1f).coerceIn(0f, 1f)

        // Nose-tip offset relative to bounding box width
        val noseTip = face.getContour(FaceContour.NOSE_BRIDGE)?.points?.lastOrNull()
        val bbox    = face.boundingBox
        val noseTipScore = if (noseTip != null && bbox.width() > 0) {
            val centerX = bbox.exactCenterX()
            abs(noseTip.x - centerX) / bbox.width().toFloat()
        } else 0f

        // Combine: both signals weighted equally
        val raw = (earAsymmetry * 0.5f + noseTipScore * 0.5f) * 2f   // scale to [0..1]
        return raw.coerceIn(0f, 1f)
    }

    private fun euclidean(a: PointF, b: PointF): Float {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        return sqrt(dx * dx + dy * dy).toFloat()
    }

    private fun midpoint(pts: List<PointF>): PointF {
        val x = pts.sumOf { it.x.toDouble() } / pts.size
        val y = pts.sumOf { it.y.toDouble() } / pts.size
        return PointF(x.toFloat(), y.toFloat())
    }

    fun close() = detector.close()
}