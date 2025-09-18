package eu.europa.ec.corelogic.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySession
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import android.os.Parcelable
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import eu.europa.ec.corelogic.controller.wifi.PublishCallback
import eu.europa.ec.corelogic.controller.wifi.WifiAwareConfig
import eu.europa.ec.resourceslogic.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.collections.orEmpty
import kotlin.collections.plus
import android.net.wifi.WifiManager



@Parcelize
data class NetworkStatus(
    val isConnected: Boolean,
    val ipAddress: String?,
    val macAddress: String?
) : Parcelable


data class WifiAwareConfig(
    val serviceName: String,
    val serviceType: String,
    val port: Int
)

interface PublishCallback {
    fun onPublished(session: PublishDiscoverySession)
    fun onPublishFailed(reason: Int)
    fun onPeerDiscovered(peerHandle: PeerHandle)
}

interface SubscribeCallback {
    fun onSubscribed(session: SubscribeDiscoverySession)
    fun onSubscribeFailed(reason: Int)
    fun onPeerDiscovered(peerHandle: PeerHandle)
}


class WifiAwareService : Service() {

    companion object {
        private const val TAG = "WifiAwareService"
        private const val NOTIFICATION_ID = 0xBEEF
        private const val CHANNEL_ID = "WifiAwareServiceChannel"
        private const val CHANNEL_NAME = "Wi-Fi Aware"
        private const val PERMISSION_DENIED = -5


        const val ACTION_START_PUBLISH = "eu.europa.ec.corelogic.action.START_PUBLISH"
        const val ACTION_STOP_PUBLISH = "eu.europa.ec.corelogic.action.STOP_PUBLISH"
        const val ACTION_START_SUBSCRIBE = "eu.europa.ec.corelogic.action.START_SUBSCRIBE"
        const val ACTION_PEER_DISCOVERED = "eu.europa.ec.corelogic.action.PEER_DISCOVERED"
        const val ACTION_PUBLISH_STATUS = "eu.europa.ec.corelogic.action.PUBLISH_STATUS"
        const val ACTION_SUBSCRIBE_STATUS = "eu.europa.ec.corelogic.action.SUBSCRIBE_STATUS"
        const val ACTION_RECEIVER_READY = "eu.europa.ec.corelogic.action.RECEIVER_READY"
        const val ACTION_NETWORK_STATUS = "eu.europa.ec.corelogic.action.NETWORK_STATUS"
        const val ACTION_RESPONSE_SENT = "eu.europa.ec.corelogic.action.RESPONSE_SENT"

        const val ACTION_SEND_RESPONSE = "eu.europa.ec.corelogic.action.SEND_RESPONSE"
        const val EXTRA_PEER = "eu.europa.ec.corelogic.extra.PEER"
        const val EXTRA_STATUS = "eu.europa.ec.corelogic.extra.STATUS"
        const val EXTRA_ERROR_CODE = "eu.europa.ec.corelogic.extra.ERROR_CODE"
        const val EXTRA_MESSAGE = "eu.europa.ec.corelogic.extra.MESSAGE"
        const val EXTRA_NETWORK_STATUS = "eu.europa.ec.corelogic.extra.NETWORK_STATUS"


        fun createStartIntent(context: Context): Intent {
            return Intent(context, WifiAwareService::class.java).apply { action = ACTION_START_PUBLISH }
        }

        fun createStopIntent(context: Context): Intent {
            return Intent(context, WifiAwareService::class.java).apply { action = ACTION_STOP_PUBLISH }
        }

        fun createSubscribeIntent(context: Context): Intent {
            return Intent(context, WifiAwareService::class.java).apply { action = ACTION_START_SUBSCRIBE }
        }

        fun createSendResponseIntent(context: Context, peerHandle: PeerHandle, response: String): Intent {
            return Intent(context, WifiAwareService::class.java).apply {
                action = ACTION_SEND_RESPONSE
                putExtra(EXTRA_PEER, peerHandle as Parcelable)
                putExtra(EXTRA_MESSAGE, response)
            }
        }
    }

    private val svcScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private var wifiAwareSession: WifiAwareSession? = null
    private var discoverySession: DiscoverySession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var subscribeSession: SubscribeDiscoverySession? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private lateinit var wifiAwareManager: WifiAwareManager
    private val _peers = MutableLiveData<List<PeerHandle>>(emptyList())
    private val activeClients = mutableMapOf<PeerHandle, Socket>()

    override fun onCreate() {
        super.onCreate()

        wifiAwareManager = getSystemService(Context.WIFI_AWARE_SERVICE) as WifiAwareManager
        createNotificationChannel()
    }



    @RequiresPermission(allOf = [Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_WIFI_STATE])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        println("INTENT1 ${intent?.action}")
//        println("INTENT1 ${intent?.data}")
//
        if (!checkRequiredPermissions()) {
            println("[WifiAwareService] Required permissions not granted:")

            broadcastStatus(false, PERMISSION_DENIED, isPublish = intent?.action == ACTION_START_PUBLISH)
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_START_PUBLISH -> {
                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Wi-Fi Aware Service")
                    .setContentText("Publishing service...")
                    .setSmallIcon(R.drawable.ic_notifications)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()

                startForeground(NOTIFICATION_ID, notification)

                val config = WifiAwareConfig(
                    serviceName = "EUDI_WIFI",
                    serviceType = "_eudi.tcp",
                    port = 42424
                )

                publishService(config, object : PublishCallback {
                    override fun onPublished(session: PublishDiscoverySession) {
                        println("[WifiAware] Publish started: $session")
                        broadcastStatus(true, isPublish = true)
                    }

                    override fun onPublishFailed(reason: Int) {
                        println("[WifiAware] Publish failed, reason=$reason")
                        broadcastStatus(false, reason, isPublish = true)
                        stopForeground(true)
                        stopSelf()
                    }

                    override fun onPeerDiscovered(peerHandle: PeerHandle) {
                        val updated = _peers.value.orEmpty() + peerHandle
                        _peers.postValue(updated)
                        println("[WifiAware] Peer discovered: $peerHandle")
                        broadcastPeerDiscovered(peerHandle)
                    }
                })
            }
            ACTION_START_SUBSCRIBE -> {
                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Wi-Fi Aware Service")
                    .setContentText("Scanning for nearby devices...")
                    .setSmallIcon(R.drawable.ic_notifications)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
                startForeground(NOTIFICATION_ID, notification)

                val config = WifiAwareConfig(
                    serviceName = "EUDI_WIFI",
                    serviceType = "_eudi.tcp",
                    port = 42424
                )

                subscribeService(config, object : SubscribeCallback {
                    override fun onSubscribed(session: SubscribeDiscoverySession) {
                        println("[WifiAware] Subscribe started: $session")
                        broadcastStatus(true, isPublish = false)
                    }

                    override fun onSubscribeFailed(reason: Int) {
                        println("[WifiAware] Subscribe failed, reason=$reason")
                        broadcastStatus(false, reason, isPublish = false)
                        stopForeground(true)
                        stopSelf()
                    }

                    override fun onPeerDiscovered(peerHandle: PeerHandle) {
                        val updated = _peers.value.orEmpty() + peerHandle
                        _peers.postValue(updated)
                        println("[WifiAware] Peer discovered: $peerHandle")
                        broadcastPeerDiscovered(peerHandle)
                    }
                })
            }
            ACTION_STOP_PUBLISH -> {
                stop()
                stopForeground(true)
                stopSelf()
            }
            ACTION_SEND_RESPONSE -> {
                val peerHandle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_PEER, PeerHandle::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_PEER) as? PeerHandle
                }
                val response = intent.getStringExtra(EXTRA_MESSAGE) ?: return START_STICKY
                peerHandle?.let { sendResponseToPeer(it, response) }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stop()
        svcScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Wi-Fi Aware service notifications"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_WIFI_STATE])
    private fun publishService(config: WifiAwareConfig, callback: PublishCallback) {
        if (!wifiAwareManager.isAvailable) {
            println("[WifiAware] Wi-Fi Aware not available")
            callback.onPublishFailed(-4)
            return
        }

        val publishConfig = PublishConfig.Builder()
            .setServiceName(config.serviceName)
            .setServiceSpecificInfo(config.serviceType.toByteArray())
            .setPublishType(PublishConfig.PUBLISH_TYPE_UNSOLICITED)
            .build()

        wifiAwareManager.attach(object : AttachCallback() {
            @RequiresApi(Build.VERSION_CODES.TIRAMISU)
            override fun onAttached(session: WifiAwareSession) {
                println("[WifiAware] onAttached: creating publish session")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val nearbyWifiGranted = ContextCompat.checkSelfPermission(
                        this@WifiAwareService,
                        Manifest.permission.NEARBY_WIFI_DEVICES
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!nearbyWifiGranted) {
                        println("[WifiAware] Permission denied: NEARBY_WIFI_DEVICES")
                        callback.onPublishFailed(PERMISSION_DENIED)
                        return
                    }
                } else {
                    val fineLocationGranted = ContextCompat.checkSelfPermission(
                        this@WifiAwareService,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!fineLocationGranted) {
                        println("[WifiAware] Permission denied: ACCESS_FINE_LOCATION")
                        callback.onPublishFailed(PERMISSION_DENIED)
                        return
                    }
                }

                session.publish(publishConfig, object : DiscoverySessionCallback() {
                    override fun onPublishStarted(pubSession: PublishDiscoverySession) {
                        println("[WifiAware] onPublishStarted: $pubSession")
                        publishSession = pubSession
                        callback.onPublished(pubSession)
                    }

                    override fun onServiceDiscovered(
                        peerHandle: PeerHandle,
                        serviceSpecificInfo: ByteArray,
                        matchFilter: List<ByteArray>
                    ) {
                        println("[WifiAware] onServiceDiscovered: $peerHandle")
                        callback.onPeerDiscovered(peerHandle)
                    }

                    override fun onSessionConfigFailed() {
                        println("[WifiAware] onSessionConfigFailed")
                        callback.onPublishFailed(-2)
                    }

                    override fun onSessionTerminated() {
                        println("[WifiAware] onSessionTerminated")
                        callback.onPublishFailed(-3)
                    }
                }, null)
                wifiAwareSession = session
            }

            override fun onAttachFailed() {
                println("[WifiAware] onAttachFailed")
                callback.onPublishFailed(-1)
            }
        }, null)
    }

    @RequiresPermission(allOf = [Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_WIFI_STATE])
    private fun subscribeService(config: WifiAwareConfig, callback: SubscribeCallback) {
        if (!wifiAwareManager.isAvailable) {
            println("[WifiAware] Wi-Fi Aware not available")
            callback.onSubscribeFailed(-4)
            return
        }

        val subscribeConfig = SubscribeConfig.Builder()
            .setServiceName(config.serviceName)
            .setServiceSpecificInfo(config.serviceType.toByteArray())
            .setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)
            .build()

        wifiAwareManager.attach(object : AttachCallback() {
            @RequiresApi(Build.VERSION_CODES.TIRAMISU)
            override fun onAttached(session: WifiAwareSession) {
                println("[WifiAware] onAttached: creating subscribe session")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val nearbyWifiGranted = ContextCompat.checkSelfPermission(
                        this@WifiAwareService,
                        Manifest.permission.NEARBY_WIFI_DEVICES
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!nearbyWifiGranted) {
                        println("[WifiAware] Permission denied: NEARBY_WIFI_DEVICES")
                        callback.onSubscribeFailed(PERMISSION_DENIED)
                        return
                    }
                } else {
                    val fineLocationGranted = ContextCompat.checkSelfPermission(
                        this@WifiAwareService,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!fineLocationGranted) {
                        println("[WifiAware] Permission denied: ACCESS_FINE_LOCATION")
                        callback.onSubscribeFailed(PERMISSION_DENIED)
                        return
                    }
                }

                session.subscribe(subscribeConfig, object : DiscoverySessionCallback() {
                    override fun onSubscribeStarted(subSession: SubscribeDiscoverySession) {
                        println("[WifiAware] onSubscribeStarted: $subSession")
                        subscribeSession = subSession
                        callback.onSubscribed(subSession)
                    }

                    override fun onServiceDiscovered(
                        peerHandle: PeerHandle,
                        serviceSpecificInfo: ByteArray,
                        matchFilter: List<ByteArray>
                    ) {
                        println("[WifiAware] onServiceDiscovered: $peerHandle")
                        callback.onPeerDiscovered(peerHandle)
                    }

                    override fun onSessionConfigFailed() {
                        println("[WifiAware] onSessionConfigFailed")
                        callback.onSubscribeFailed(-2)
                    }

                    override fun onSessionTerminated() {
                        println("[WifiAware] onSessionTerminated")
                        callback.onSubscribeFailed(-3)
                    }
                }, null)
                wifiAwareSession = session
            }

            override fun onAttachFailed() {
                println("[WifiAware] onAttachFailed")
                callback.onSubscribeFailed(-1)
            }
        }, null)
    }

    private fun startHandling(
        config: WifiAwareConfig,
        networkCallback: ConnectivityManager.NetworkCallback,
        peerHandle: PeerHandle
    ) {
        this.networkCallback = networkCallback
        serverSocket = ServerSocket(config.port).apply {
            if (config.port == 0) {
                println("ServerSocket(0) automatically assigns an available port")
            }
        }

        val specifier = WifiAwareNetworkSpecifier.Builder(discoverySession!!, peerHandle)
            .setPort(serverSocket!!.localPort)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(specifier)
            .build()

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
            @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                val ipAddress = network.getIpAddress()
                val macAddress = getMacAddress()
                val networkStatus = NetworkStatus(
                    isConnected = true,
                    ipAddress = ipAddress,
                    macAddress = macAddress
                )
                broadcastNetworkStatus(networkStatus)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                val networkStatus = NetworkStatus(
                    isConnected = false,
                    ipAddress = null,
                    macAddress = null
                )
                broadcastNetworkStatus(networkStatus)
            }
        })

        svcScope.launch {
            try {
                while (!serverSocket!!.isClosed) {
                    val client: Socket = serverSocket!!.accept()
                    activeClients[peerHandle] = client
                    handleClient(client, peerHandle)
                }
            } catch (ex: Exception) {
                println("[ERROR] Peer server disconnected: ${ex.message}")
                broadcastNetworkStatus(NetworkStatus(isConnected = false, ipAddress = null, macAddress = null))
            }
        }
    }

    private fun handleClient(sock: Socket, peerHandle: PeerHandle) = svcScope.launch {
        sock.use {
            val input: InputStream = it.getInputStream()
            val output: OutputStream = it.getOutputStream()
            val buffer = ByteArray(1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                val message = String(buffer, 0, read)
                println("[WifiAwareService] Recebido: $message")
                if (message.contains("READY")) {
                    val intent = Intent(ACTION_RECEIVER_READY).apply {
                        putExtra(EXTRA_MESSAGE, message)
                        putExtra(EXTRA_PEER, peerHandle as Parcelable)
                    }
                    this@WifiAwareService.sendBroadcast(intent)
                }
                output.write(buffer, 0, read)
            }
        }
        activeClients.remove(peerHandle)
    }

    private fun sendResponseToPeer(peerHandle: PeerHandle, response: String) {
        val clientSocket = activeClients[peerHandle]
        if (clientSocket == null) {
            println("[WifiAwareService] No active socket for peer: $peerHandle")
            return
        }
        svcScope.launch {
            try {
                clientSocket.getOutputStream().use { output ->
                    output.write(response.toByteArray())
                    output.flush()
                    println("[WifiAwareService] Resposta enviada: $response")
                    val intent = Intent(ACTION_RESPONSE_SENT).apply {
                        putExtra(EXTRA_PEER, peerHandle as Parcelable)
                    }
                    this@WifiAwareService.sendBroadcast(intent)
                }
            } catch (e: Exception) {
                println("[WifiAwareService] Falha ao enviar resposta: ${e.message}")
            }
        }
    }

    private fun stop() {
        publishSession?.close()
        subscribeSession?.close()
        wifiAwareSession?.close()
        discoverySession?.close()
        serverSocket?.close()
        activeClients.values.forEach { it.close() }
        activeClients.clear()
        networkCallback?.let {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(it)
        }
        _peers.postValue(emptyList())
        broadcastNetworkStatus(NetworkStatus(isConnected = false, ipAddress = null, macAddress = null))
    }

    private fun broadcastPeerDiscovered(peerHandle: PeerHandle) {
        val intent = Intent(ACTION_PEER_DISCOVERED).apply {
            putExtra(EXTRA_PEER, peerHandle as Parcelable)
        }
        this.sendBroadcast(intent)
    }

    private fun broadcastStatus(isSuccess: Boolean, errorCode: Int? = null, isPublish: Boolean) {
        val action = if (isPublish) ACTION_PUBLISH_STATUS else ACTION_SUBSCRIBE_STATUS
        val intent = Intent(action).apply {
            putExtra(EXTRA_STATUS, isSuccess)
            errorCode?.let { putExtra(EXTRA_ERROR_CODE, it) }
        }
        this.sendBroadcast(intent)
    }

    private fun broadcastNetworkStatus(networkStatus: NetworkStatus) {
        val intent = Intent(ACTION_NETWORK_STATUS).apply {
            putExtra(EXTRA_NETWORK_STATUS, networkStatus as Parcelable)
        }
        this.sendBroadcast(intent)
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun Network.getIpAddress(): String? {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val linkProperties = cm.getLinkProperties(this)
        return linkProperties?.linkAddresses?.firstOrNull()?.address?.hostAddress
    }

    private fun getMacAddress(): String? {
        try {
            val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            return wifiInfo.macAddress
        } catch (e: Exception) {
            println("[WifiAwareService] Error getting MAC address: ${e.message}")
            return null
        }
    }

    private fun checkRequiredPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }

        return permissions.all {
            val granted = ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            println("[WifiAwareService] Permission $it: ${if (granted) "GRANTED" else "DENIED"}")
            granted
        }
    }
}
