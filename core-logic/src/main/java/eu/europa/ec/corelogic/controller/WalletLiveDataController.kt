package eu.europa.ec.corelogic.controller

import android.Manifest
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishDiscoverySession
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import eu.europa.ec.corelogic.config.WalletConfigNetworkConfig
import eu.europa.ec.corelogic.controller.wifi.PublishCallback
import eu.europa.ec.corelogic.controller.wifi.WifiAwareConfig
import eu.europa.ec.corelogic.controller.wifi.WifiAwareServerController
import eu.europa.ec.corelogic.controller.wifi.WifiAwareServerControllerImpl


data class NetworkStatus(
    val isConnected: Boolean,
    val ipAddress: String?,
    val macAddress: String?
)

interface WalletLiveDataController {
    fun isWifiAwareAvailable(): Boolean

    fun scanPeers()
    val peersLiveData: LiveData<List<PeerHandle>>

    fun checkAndRequestWifiAwarePermissions(): Boolean
}


class WalletLiveDataControllerImpl(
    private val walletConfigNetworkConfig: WalletConfigNetworkConfig,
    private val wifiAwareController: WifiAwareServerController,
) : WalletLiveDataController {

    companion object{
        private const val REQUEST_WIFI_AWARE = 1001
    }


    private val _peers = MutableLiveData<List<PeerHandle>>(emptyList())

    override val peersLiveData: LiveData<List<PeerHandle>> = _peers
    override fun checkAndRequestWifiAwarePermissions(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isWifiAwareAvailable(): Boolean {
        return walletConfigNetworkConfig.isWifiAwareAvailable()
    }


    override fun scanPeers() {

        val config = WifiAwareConfig(
            serviceName = "EUDI_WIFI",
            serviceType = "_eudi._udp",
            port        = 42424
        )

        _peers.postValue(emptyList())

        wifiAwareController.publishService(config, object: PublishCallback {
            @RequiresPermission(allOf = [Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_WIFI_STATE])
            override fun onPublished(session: PublishDiscoverySession) {
                (wifiAwareController as WifiAwareServerControllerImpl)
                    .publishService(config, this)
            }
            override fun onPublishFailed(reason: Int) {
                println("[WifiAware] publish failed, reason=$reason")
            }
            override fun onPeerDiscovered(peerHandle: PeerHandle) {
                val updated = _peers.value.orEmpty() + peerHandle
                _peers.postValue(updated)
                println("[WifiAware] Peer discovered: $peerHandle")
            }
        })
    }
}