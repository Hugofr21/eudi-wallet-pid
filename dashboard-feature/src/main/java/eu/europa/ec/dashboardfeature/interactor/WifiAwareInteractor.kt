package eu.europa.ec.dashboardfeature.interactor

import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow

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
    fun isWifiCentralClientModeEnabled(): Boolean
    fun sendResponse(): Flow<WifiAwareInteractorPartialState>

}


class WifiAwareInteractorImpl(
    private val resourceProvider: ResourceProvider,
//    private val wifiController: WifiController,
//    private val networkCoreConfig: NetworkCoreConfig,
) : WifiAwareInteractor {
    override fun isWifiAvailable(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isWifiCentralClientModeEnabled(): Boolean {
        TODO("Not yet implemented")
    }

    override fun sendResponse(): Flow<WifiAwareInteractorPartialState> {
        TODO("Not yet implemented")
    }

}