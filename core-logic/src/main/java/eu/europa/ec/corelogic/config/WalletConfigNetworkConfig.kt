package eu.europa.ec.corelogic.config

import android.content.Context
import android.content.pm.PackageManager
import android.net.LinkProperties
import android.os.Build
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

interface WalletConfigNetworkConfig {
    fun getDeviceIpAddress(useIPv4: Boolean = true): String?
    fun getDeviceMacAddress(useIPv4: Boolean = true): String?
    fun isWifiAwareAvailable(): Boolean
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

    companion object {
        fun getIP(context: Context, useIPv4: Boolean = true): String? {
            return WalletConfigNetworkConfigImpl(context).getDeviceIpAddress(useIPv4)
        }

        fun getMAC(context: Context, useIPv4: Boolean = true): String? {
            return WalletConfigNetworkConfigImpl(context).getDeviceMacAddress(useIPv4)
        }

        fun isWifiAwareAvailable(context: Context): Boolean {
            return WalletConfigNetworkConfigImpl(context).isWifiAwareAvailable()
        }
    }
}
