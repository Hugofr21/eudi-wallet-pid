package eu.europa.ec.dashboardfeature.interactor

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import eu.europa.ec.mrzscannerLogic.controller.MrzScanController
import eu.europa.ec.mrzscannerLogic.controller.MrzScanState
import eu.europa.ec.mrzscannerLogic.model.ScanType
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow

interface ScannerInteractor{
    fun startScanning(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        scanType: ScanType
    ): Flow<MrzScanState>
    fun stopScanning()
    fun isCameraAvailable(): Boolean
    fun isScanning(): Boolean

    fun hasCameraPermission(): Boolean
}

class ScannerInteractorImpl(
    private val context: ResourceProvider,
    private val mrzScanController: MrzScanController
) : ScannerInteractor {


    override fun startScanning(lifecycleOwner: LifecycleOwner, previewView: PreviewView, scanType: ScanType): Flow<MrzScanState> {
        return mrzScanController.startScanning(lifecycleOwner, previewView, scanType)
    }


    override fun stopScanning() {
        mrzScanController.stopScanning()
    }


    override fun isCameraAvailable(): Boolean {
        return mrzScanController.isCameraAvailable()
    }


    override fun isScanning(): Boolean {
        return mrzScanController.isScanning()
    }


    override fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context.provideContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
}