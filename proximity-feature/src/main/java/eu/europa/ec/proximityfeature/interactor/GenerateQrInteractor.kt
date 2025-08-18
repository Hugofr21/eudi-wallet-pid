package eu.europa.ec.proximityfeature.interactor

import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.config.toDomainConfig
import eu.europa.ec.corelogic.controller.TransferEventPartialState
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onSubscription


sealed class ProximityPidQRWritePartialState {
    data class QrReady(val qrCode: String) : ProximityPidQRWritePartialState()
    data class Error(val error: String) : ProximityPidQRWritePartialState()
}


interface GenerateQrInteractor {
    fun startQrEngagement(): Flow<ProximityPidQRWritePartialState>
    fun cancelTransfer()

    fun setConfig(config: RequestUriConfig)
}

class GenerateQrInteractorImpl(
    private val resourceProvider : ResourceProvider,
    private val walletCorePresentationController: WalletCorePresentationController
): GenerateQrInteractor {

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()


    override fun setConfig(config: RequestUriConfig) {
        println("[ProximityQRInteractorImpl] setConfig")
        walletCorePresentationController.setConfig(config.toDomainConfig())
    }


    override fun startQrEngagement(): Flow<ProximityPidQRWritePartialState> = flow {
        walletCorePresentationController.events
            .onSubscription {
                walletCorePresentationController.startQrEngagement()
            }.mapNotNull {
                when (it) {

                    is TransferEventPartialState.Error -> {
                        ProximityPidQRWritePartialState.Error(error = it.error)
                    }

                    is TransferEventPartialState.QrEngagementReady -> {
                        ProximityPidQRWritePartialState.QrReady(qrCode = it.qrCode)
                    }

                    else -> null
                }
            }.collect {
                emit(it)
            }
    }.safeAsync {
        ProximityPidQRWritePartialState.Error(error = it.localizedMessage ?: genericErrorMsg)
    }

    override fun cancelTransfer() {
        walletCorePresentationController.stopPresentation()
    }
}