package eu.europa.ec.corelogic.controller

import eu.europa.ec.corelogic.config.WalletConfigNetworkConfig

interface WalletLiveDataController {
    fun isWifiAwareAvailable(): Boolean
}

class WalletLiveDataControllerImpl(
    private val walletConfigNetworkConfig: WalletConfigNetworkConfig
) : WalletLiveDataController {
    override fun isWifiAwareAvailable(): Boolean {
        return walletConfigNetworkConfig.isWifiAwareAvailable()
    }
}