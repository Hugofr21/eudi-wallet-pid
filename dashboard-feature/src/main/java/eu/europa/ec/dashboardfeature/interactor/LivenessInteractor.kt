package eu.europa.ec.dashboardfeature.interactor

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import eu.europa.ec.mrzscannerLogic.controller.LivenessDetectionFaceController
import eu.europa.ec.mrzscannerLogic.controller.LivenessResult
import eu.europa.ec.mrzscannerLogic.controller.LivenessUpdate
import eu.europa.ec.mrzscannerLogic.model.ScanType
import kotlinx.coroutines.flow.Flow
import java.io.File

interface LivenessInteractor {
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

class LivenessInteractorImpl(
    private val livenessController: LivenessDetectionFaceController
) : LivenessInteractor {

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
        return if (bytes != null) "pending_selfie.jpg" else null
    }

    override fun clearSavedSelfie() = livenessController.clearSelfie()
}