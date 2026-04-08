package eu.europa.ec.consentuser.interactor

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import eu.europa.ec.mrzscannerLogic.controller.LivenessDetectionFaceController
import eu.europa.ec.mrzscannerLogic.controller.LivenessUpdate
import eu.europa.ec.mrzscannerLogic.model.ScanType
import kotlinx.coroutines.flow.Flow

interface ImageCaptureInteractor {
    fun startLiveness(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        scanType: ScanType
    ): Flow<LivenessUpdate>

    fun stop()
    fun isScanning(): Boolean
    fun saveCapturedSelfie(jpegBytes: ByteArray): String?
    fun getLastSelfiePath(): String?
    fun clearSavedSelfie()
}


class ImageCaptureInteractorImpl(
    private val livenessController: LivenessDetectionFaceController
) : ImageCaptureInteractor {

    override fun startLiveness(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        scanType: ScanType
    ): Flow<LivenessUpdate> {
        return livenessController.startScanning(lifecycleOwner, previewView, scanType)
    }

    override fun stop() {
        livenessController.stopScanning()
    }

    override fun isScanning(): Boolean = livenessController.isScanning()

    override fun saveCapturedSelfie(jpegBytes: ByteArray): String? =
        livenessController.saveSelfie(jpegBytes)

    override fun getLastSelfiePath(): String? {
        val bytes = livenessController.getSelfieBytes()
        return if (bytes != null) "auto_self.jpg" else null
    }

    override fun clearSavedSelfie() = livenessController.clearSelfie()
}