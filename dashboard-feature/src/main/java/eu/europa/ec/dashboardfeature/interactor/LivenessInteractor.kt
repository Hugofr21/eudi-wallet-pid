package eu.europa.ec.dashboardfeature.interactor

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.commonfeature.util.DocumentJsonKeys
import eu.europa.ec.commonfeature.util.extractValueFromDocumentOrEmpty
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.mrzscannerLogic.controller.LivenessDetectionFaceController
import eu.europa.ec.mrzscannerLogic.controller.LivenessResult
import eu.europa.ec.mrzscannerLogic.controller.LivenessUpdate
import eu.europa.ec.mrzscannerLogic.model.ScanType
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File


sealed class PersonInteractorGetUserNamePidDocumentPartialState {
    data class Success(
        val userFirstName: String,
    ) : PersonInteractorGetUserNamePidDocumentPartialState()

    data class Failure(
        val error: String
    ) : PersonInteractorGetUserNamePidDocumentPartialState()
}

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

    fun getUserNameViaMainPidDocument(): Flow<PersonInteractorGetUserNamePidDocumentPartialState>
}

class LivenessInteractorImpl(
    private val livenessController: LivenessDetectionFaceController,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val resourceProvider: ResourceProvider,
) : LivenessInteractor {

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

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

    override fun getUserNameViaMainPidDocument(): Flow<PersonInteractorGetUserNamePidDocumentPartialState> =
        flow {
            val mainPid = walletCoreDocumentsController.getMainPidDocument()
            val userFirstName = mainPid?.let {
                return@let extractValueFromDocumentOrEmpty(
                    document = it,
                    key = DocumentJsonKeys.GIVEN_NAME

                )
            }.orEmpty()

            emit(
                PersonInteractorGetUserNamePidDocumentPartialState.Success(
                    userFirstName = userFirstName
                )
            )
        }.safeAsync {
            PersonInteractorGetUserNamePidDocumentPartialState.Failure(
                error = it.localizedMessage ?: genericErrorMsg
            )
        }
}