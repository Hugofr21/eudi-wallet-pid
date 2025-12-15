package eu.europa.ec.mrzscannerLogic.service

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

interface FaceService{
    fun detectFaces(image: InputImage): Task<List<Face>>
}

class FaceServiceImpl(

): FaceService {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setMinFaceSize(0.05f)
        .build()

    private val detector = FaceDetection.getClient(options)

    override fun detectFaces(image: InputImage): Task<List<Face>> {
        return detector.process(image)
    }

}