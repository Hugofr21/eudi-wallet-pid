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
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.net.wifi.aware.WifiAwareSession
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
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
        networkCallback: ConnectivityManager.NetworkCallback
    )

    /**
     * Stop the server, closing sockets and unregistering callbacks.
     */
    fun stop()
}


data class WifiAwareConfig(
    val serviceName: String,
    val port: Int = 0
)

interface PublishCallback {
    fun onPublished(session: PublishDiscoverySession)
    fun onPublishFailed(reason: Int)
}


class WifiAwareServerControllerImpl(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : WifiAwareServerController {

    private var serverSocket: ServerSocket? = null
    private var discoverySession: DiscoverySession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @RequiresPermission(allOf = [Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_WIFI_STATE])
    override fun publishService(
        config: WifiAwareConfig,
        callback: PublishCallback
    ) {
//        val awareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as WifiAwareManager
//        val publishConfig = PublishConfig.Builder()
//            .setServiceName(config.serviceName)
//            .build()
//
//        awareManager.attach(object : AttachCallback() {
//            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
//            override fun onAttached(session: WifiAwareSession) {
//                if (ActivityCompat.checkSelfPermission(
//                        context,
//                        Manifest.permission.ACCESS_FINE_LOCATION
//                    ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
//                        context,
//                        Manifest.permission.NEARBY_WIFI_DEVICES
//                    ) != PackageManager.PERMISSION_GRANTED
//                ) {
//                    callback.onPublishFailed(PERMISSION_DENIED)
//                    return
//                }
//
//                session.publish(publishConfig, object : DiscoverySessionCallback() {
//                    override fun onPublishStarted(pubSession: PublishDiscoverySession) {
//                        publishSession = pubSession
//                        callback.onPublished(pubSession)
//                    }
//
//                    override fun onSessionResumeFailed(reason: Int) {
//                        callback.onPublishFailed(reason)
//                    }
//                }, null)
//                discoverySession = session
//            }
//
//            override fun onAttachFailed() {
//                callback.onPublishFailed(-1)
//            }
//        }, null)
    }

    override fun startHandling(
        config: WifiAwareConfig,
        networkCallback: ConnectivityManager.NetworkCallback,
    ) {
//        this.networkCallback = networkCallback
//        serverSocket = ServerSocket(config.port).apply {
//            if (config.port == 0) {
//            }
//        }
//
//        val specifier = WifiAwareNetworkSpecifier.Builder(
//            discoverySession!!,
//            publishSession!!.peerHandle
//        )
//            .setPort(serverSocket!!.localPort)
//            .build()
//
//        val request = NetworkRequest.Builder()
//            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
//            .setNetworkSpecifier(specifier)
//            .build()
//
//
//        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//        cm.requestNetwork(request, networkCallback)
//
//        scope.launch {
//            try {
//                while (!serverSocket!!.isClosed) {
//                    val client: Socket = serverSocket!!.accept()
//                    handleClient(client)
//                }
//            } catch (ex: Exception) {
//
//            }
//        }
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
        discoverySession?.close()

        serverSocket?.close()

        networkCallback?.let {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(it)
        }
    }
}
