package eu.europa.ec.dashboardfeature.interactor

import android.content.Context
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletLiveDataController
import eu.europa.ec.corelogic.controller.wifi.WifiAwareConfig
import eu.europa.ec.corelogic.service.WifiAwareService
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

sealed class WifiAwareInteractorPartialState {
    data class Success(
        val userFirstName: String,
    ) : WifiAwareInteractorPartialState()

    data class Failure(
        val error: String
    ) : WifiAwareInteractorPartialState()
}

interface WifiAwareInteractor {
    fun isWifiAvailable(): Boolean
    fun scanPeers()

    fun startSubscription()

    fun stopScan()

}


class WifiAwareInteractorImpl(
    private val resourceProvider: ResourceProvider,
    private val walletLiveDataController: WalletLiveDataController,
    private val context: Context
) : WifiAwareInteractor {

    override fun isWifiAvailable(): Boolean =
        walletLiveDataController.isWifiAwareAvailable()

    override fun scanPeers() {
        val intent = WifiAwareService.createStartIntent(context)
        context.startService(intent)
    }

    override fun startSubscription() {
        val intent = WifiAwareService.createSubscribeIntent(context)
        context.startService(intent)
    }

    override fun stopScan() {
        walletLiveDataController.stopWifiAware()
        val intent = WifiAwareService.createStopIntent(context)
        context.startService(intent)
    }


}