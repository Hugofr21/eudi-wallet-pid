package eu.europa.ec.corelogic.controller.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.*
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer


interface WifiAwareClientController {

    fun subscribeService()

    fun connectToService()

    fun sendMessage(message: ByteArray): Boolean

    fun stopClient()
}


class WifiAwareClientControllerImpl(
    private val context: Context,
    private val serviceName: String = "MyAwareService"
) : WifiAwareClientController {
    override fun subscribeService() {
        TODO("Not yet implemented")
    }

    override fun connectToService() {
        TODO("Not yet implemented")
    }

    override fun sendMessage(message: ByteArray): Boolean {
        TODO("Not yet implemented")
    }

    override fun stopClient() {
        TODO("Not yet implemented")
    }


}