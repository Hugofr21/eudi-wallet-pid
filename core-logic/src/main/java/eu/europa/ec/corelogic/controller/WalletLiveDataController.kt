package eu.europa.ec.corelogic.controller

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.aware.PeerHandle
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import eu.europa.ec.corelogic.config.WalletConfigNetworkConfig
import eu.europa.ec.corelogic.controller.wifi.WifiAwareServerController
import eu.europa.ec.corelogic.service.WifiAwareService
import eu.europa.ec.eudi.iso18013.transfer.response.RequestedDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.net.URI


data class NetworkStatus(
    val isConnected: Boolean,
    val ipAddress: String?,
    val macAddress: String?
)


sealed class TransferEventWifiAwarePartialState {
    data object Connected : TransferEventWifiAwarePartialState()
    data object Connecting : TransferEventWifiAwarePartialState()
    data object Disconnected : TransferEventWifiAwarePartialState()
    data class Error(val error: String) : TransferEventWifiAwarePartialState()
    data class ReceiverReady(val msg: String) : TransferEventPartialState()
    data class RequestReceivedPeer(
        val host: String,
        val mac: String?,
        val name: String?,
    ) : TransferEventWifiAwarePartialState()

    data object ResponseSent : TransferEventWifiAwarePartialState()

}


interface WalletLiveDataController {

    fun isWifiAwareAvailable(): Boolean

    val broadcastReceiver: BroadcastReceiver
    val peersLiveData: LiveData<List<PeerHandle>>

    fun checkAndRequestWifiAwarePermissions(): Boolean
    fun getMissingPermissions(): List<String>

    val events: SharedFlow<TransferEventWifiAwarePartialState>

    fun stopWifiAware()
}
class WalletLiveDataControllerImpl(
    private val walletConfigNetworkConfig: WalletConfigNetworkConfig,
    private val wifiAwareController: WifiAwareServerController,
    private val context: Context
) : WalletLiveDataController {

    private val _peersLiveData = MutableLiveData<List<PeerHandle>>(emptyList())
    override val peersLiveData: LiveData<List<PeerHandle>> = _peersLiveData

    private val _events = MutableSharedFlow<TransferEventWifiAwarePartialState>(replay = 0)
    override val events: SharedFlow<TransferEventWifiAwarePartialState> = _events.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun checkAndRequestWifiAwarePermissions(): Boolean {
        return walletConfigNetworkConfig.checkAndRequestWifiAwarePermissions()
    }

    override fun isWifiAwareAvailable(): Boolean {
        return walletConfigNetworkConfig.isWifiAwareAvailable()
    }

    override fun getMissingPermissions(): List<String> {
        return walletConfigNetworkConfig.getMissingPermissions()
    }

    override val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiAwareService.ACTION_PEER_DISCOVERED -> {
                    val peerHandle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiAwareService.EXTRA_PEER, PeerHandle::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(WifiAwareService.EXTRA_PEER) as? PeerHandle
                    }
                    peerHandle?.let {
                        val currentPeers = _peersLiveData.value.orEmpty().toMutableList()
                        if (!currentPeers.contains(it)) {
                            currentPeers.add(it)
                            _peersLiveData.postValue(currentPeers)
                            scope.launch {
                                _events.emit(TransferEventWifiAwarePartialState.Connected)
                            }
                        }
                    }
                }
                WifiAwareService.ACTION_PUBLISH_STATUS -> {
                    val isSuccess = intent.getBooleanExtra(WifiAwareService.EXTRA_STATUS, false)
                    val errorCode = intent.getIntExtra(WifiAwareService.EXTRA_ERROR_CODE, -1)
                    scope.launch {
                        if (!isSuccess) {
                            println("[WalletLiveDataController] Publish failed with code: $errorCode")
                            _events.emit(TransferEventWifiAwarePartialState.Error("Publish failed with code: $errorCode"))
                        } else {
                            _events.emit(TransferEventWifiAwarePartialState.Connecting)
                        }
                    }
                }
                WifiAwareService.ACTION_SUBSCRIBE_STATUS -> {
                    val isSuccess = intent.getBooleanExtra(WifiAwareService.EXTRA_STATUS, false)
                    val errorCode = intent.getIntExtra(WifiAwareService.EXTRA_ERROR_CODE, -1)
                    scope.launch {
                        if (!isSuccess) {
                            println("[WalletLiveDataController] Subscribe failed with code: $errorCode")
                            _events.emit(TransferEventWifiAwarePartialState.Error("Subscribe failed with code: $errorCode"))
                        } else {
                            _events.emit(TransferEventWifiAwarePartialState.Connecting)
                        }
                    }
                }
            }
        }
    }


    init {
        val filter = IntentFilter().apply {
            addAction(WifiAwareService.ACTION_PEER_DISCOVERED)
            addAction(WifiAwareService.ACTION_PUBLISH_STATUS)
            addAction(WifiAwareService.ACTION_SUBSCRIBE_STATUS)
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(broadcastReceiver, filter)
    }


   override fun stopWifiAware() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(broadcastReceiver)
        scope.cancel()
    }
}