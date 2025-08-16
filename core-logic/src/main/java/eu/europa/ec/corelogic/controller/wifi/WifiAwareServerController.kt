package eu.europa.ec.corelogic.controller.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySession
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket


interface WifiAwareServerController {
    /**
     * Publish the service with the given configuration.
     * @param config publish parameters including service name and IDs
     * @param callback invoked when publish succeeds or fails
     */
    fun publishService(
        config: WifiAwareConfig,
        callback: PublishCallback
    )

    /**
     * Start handling incoming connections after publish.
     * @param config same as used to publish, including port
     * @param networkCallback network request callback to receive network
     */
    fun startHandling(
        config: WifiAwareConfig,
        networkCallback: ConnectivityManager.NetworkCallback,
        peerHandle: PeerHandle
    )

    /**
     * Stop the server, closing sockets and unregistering callbacks.
     */
    fun stop()

}


data class WifiAwareConfig(
    val serviceName: String,
    val serviceType: String,
    val port: Int = 0
)

interface PublishCallback {
    fun onPublished(session: PublishDiscoverySession)
    fun onPublishFailed(reason: Int)
    fun onPeerDiscovered(peerHandle: PeerHandle)
}


class WifiAwareServerControllerImpl(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : WifiAwareServerController {
    private var serverSocket: ServerSocket? = null
    private var wifiAwareSession: WifiAwareSession? = null
    private var  discoverySession:  DiscoverySession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @RequiresPermission(allOf = [Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_WIFI_STATE])
    override fun publishService(
        config: WifiAwareConfig,
        callback: PublishCallback
    ) {
        val awareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as WifiAwareManager
        val publishConfig = PublishConfig.Builder()
            .setServiceName(config.serviceName)
            .build()

        awareManager.attach(object : AttachCallback() {
            @RequiresApi(Build.VERSION_CODES.TIRAMISU)
            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
            override fun onAttached(session: WifiAwareSession) {
                println("[WifiAware] onAttached: criando sessão")
                // https://developer.android.com/develop/connectivity/wifi/wifi-permissions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val nearbyWifiGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.NEARBY_WIFI_DEVICES
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!nearbyWifiGranted) {
                        println("[WifiAware] Permission denied: NEARBY_WIFI_DEVICES")
                        callback.onPublishFailed(PERMISSION_DENIED)
                        return
                    }
                } else {
                    val fineLocationGranted = ContextCompat.checkSelfPermission(
                        context,
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
                        println("[WifiAwareServerControllerImpl] onPublishStarted() OK")
                        publishSession = pubSession
                        callback.onPublished(pubSession)
                    }

                    override fun onServiceDiscovered(
                        peerHandle: PeerHandle,
                        serviceSpecificInfo: ByteArray,
                        matchFilter: List<ByteArray>
                    ) {
                        println("[WifiAwareServerControllerImpl] onServiceDiscovered() OK")
                        callback.onPeerDiscovered(peerHandle)
                    }

                    override fun onSessionConfigFailed() {
                        println("[WifiAwareServerControllerImpl] onSessionConfigFailed() ERR")
                        callback.onPublishFailed(-2)
                    }

                    override fun onSessionTerminated() {
                        println("[WifiAwareServerControllerImpl] onSessionTerminated() ERR")
                        callback.onPublishFailed(-3)
                    }
                }, null)
                wifiAwareSession = session
            }

            override fun onAttachFailed() {
                println("[WifiAwareServerControllerImpl] onAttachFailed() ERR")
                callback.onPublishFailed(-1)
            }
        }, null)
    }


    override fun startHandling(
        config: WifiAwareConfig,
        networkCallback: ConnectivityManager.NetworkCallback,
        peerHandle: PeerHandle
    ) {
        this.networkCallback = networkCallback

        serverSocket = ServerSocket(config.port).apply {
            if (config.port == 0) {
               print("ServerSocket(0) automatically assigns an available port")
            }
        }

        val specifier = WifiAwareNetworkSpecifier.Builder(
            discoverySession!!,
            peerHandle
        )
            .setPort(serverSocket!!.localPort)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(specifier)
            .build()


        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.requestNetwork(request, networkCallback)

        scope.launch {
            try {
                while (!serverSocket!!.isClosed) {
                    val client: Socket = serverSocket!!.accept()
                    handleClient(client)
                }
            } catch (ex: Exception) {
              println("[ERROR] Peer server disconnected: ${ex.message}")
            }
        }
    }

    private fun handleClient(sock: Socket) = scope.launch {
        sock.use {
            val input: InputStream = it.getInputStream()
            val output: OutputStream = it.getOutputStream()
            val buffer = ByteArray(1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
        }
    }

    override fun stop() {
        publishSession?.close()
        wifiAwareSession?.close()
        discoverySession?.close()
        serverSocket?.close()
        networkCallback?.let {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(it)
        }
    }

}
