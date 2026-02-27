package eu.europa.ec.dashboardfeature.interactor

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import eu.europa.ec.mrzscannerLogic.controller.LivenessDetectionFaceController
import eu.europa.ec.mrzscannerLogic.controller.LivenessResult
import eu.europa.ec.mrzscannerLogic.controller.LivenessUpdate
import eu.europa.ec.mrzscannerLogic.model.ScanType
import kotlinx.coroutines.flow.Flow

interface LivenessInteractor {
    fun startLiveness(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        scanType: ScanType
    ): Flow<LivenessUpdate>

    fun stop()
    fun isScanning(): Boolean
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
}