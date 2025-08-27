package eu.europa.ec.corelogic.config

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.Build
import java.net.Inet4Address
import android.net.ConnectivityManager
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import java.net.NetworkInterface
import java.util.Collections

interface WalletConfigNetworkConfig {
    fun getDeviceIpAddress(useIPv4: Boolean = true): String?
    fun getDeviceMacAddress(useIPv4: Boolean = true): String?
    fun isWifiAwareAvailable(): Boolean
    fun isNetworkAvailable(context: Context?): Boolean

    fun checkAndRequestWifiAwarePermissions(): Boolean

    fun getMissingPermissions(): List<String>

    fun isInternetAvailable(context: Context): Boolean
}


class WalletConfigNetworkConfigImpl(
    private val context: Context
) : WalletConfigNetworkConfig {

    override fun getDeviceIpAddress(useIPv4: Boolean): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val isV4 = addr is Inet4Address
                        if ((useIPv4 && isV4) || (!useIPv4 && !isV4)) {
                            return addr.hostAddress.substringBefore("%")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    override fun getDeviceMacAddress(useIPv4: Boolean): String? {
        try {
            val targetIp = getDeviceIpAddress(useIPv4) ?: return null
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                if (addrs.any { addr ->
                        !addr.isLoopbackAddress &&
                                ((useIPv4 && addr is Inet4Address) || (!useIPv4 && addr !is Inet4Address)) &&
                                addr.hostAddress.substringBefore("%") == targetIp
                    }) {
                    val macBytes = intf.hardwareAddress ?: return null
                    return macBytes.joinToString(":") { byte -> String.format("%02X", byte) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    override fun isWifiAwareAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
    }

    override fun checkAndRequestWifiAwarePermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()


        return permissionsToRequest.isEmpty()
    }


    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun getMissingPermissions(): List<String> {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
            )
        } else {
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
            )
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        return missing
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun isNetworkAvailable(context: Context?): Boolean {
        if (context == null) return false
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as
                ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager
                .activeNetwork)
            if (capabilities != null) {
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                        return true
                    }
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        return true
                    }
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                        return true
                    }
                }
            }
        } else {
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
                return true
            }
        }
        return false
    }


   @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
   override fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            networkInfo.isConnected
        }
    }


}
