package tech.devline.scropy_ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.InetAddress
import java.net.Inet4Address

/**
 * App-wide Wi-Fi-LAN-only destination policy.
 *
 * Only literal RFC1918, IPv4 link-local, or loopback addresses are accepted.
 * Non-loopback destinations must be on the currently connected Wi-Fi subnet.
 * Parsing is numeric-only and never performs DNS resolution.
 */
internal object LanOnlyNetwork {

    fun parseIpv4(host: String): InetAddress? {
        val value = host.trim()
        if (value.length !in 7..15) return null

        val parts = value.split('.')
        if (parts.size != 4) return null

        val octets = ByteArray(4)
        val numbers = IntArray(4)
        for (index in parts.indices) {
            val part = parts[index]
            if (part.isEmpty() || part.length > 3 || part.any { !it.isDigit() }) return null
            // Reject ambiguous numeric forms such as 010.000.000.001.
            if (part.length > 1 && part[0] == '0') return null
            val number = part.toIntOrNull() ?: return null
            if (number !in 0..255) return null
            numbers[index] = number
            octets[index] = number.toByte()
        }

        val a = numbers[0]
        val b = numbers[1]
        val isPrivate = a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168)
        val isLocalOnly = (a == 169 && b == 254) || a == 127

        if (!isPrivate && !isLocalOnly) return null

        // Numeric construction: unlike InetAddress.getByName(), this cannot query DNS.
        return try {
            InetAddress.getByAddress(octets)
        } catch (_: Exception) {
            null
        }
    }

    fun isAllowedIpv4(host: String): Boolean = parseIpv4(host) != null

    fun requireWifiNetwork(context: Context): Network {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: throw IOException("Wi-Fi LAN is required")
        return manager.allNetworks.firstOrNull { network ->
            manager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: throw IOException("Connect to Wi-Fi to use Wireless Debugging")
    }

    /**
     * Return the Android Wi-Fi network handle for an address on that Wi-Fi subnet.
     * A zero handle is reserved for loopback, which never leaves the device.
     */
    fun requireWifiNetworkHandle(context: Context, host: String): Long {
        val address = requireAllowedIpv4(host)
        if (address.isLoopbackAddress) return 0L

        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: throw IOException("Wi-Fi LAN is required")
        val network = requireWifiNetwork(context)
        val linkAddresses = manager.getLinkProperties(network)?.linkAddresses.orEmpty()
        val isOnWifiSubnet = linkAddresses.any { linkAddress ->
            val localAddress = linkAddress.address as? Inet4Address ?: return@any false
            isSameSubnet(localAddress, linkAddress.prefixLength, address)
        }

        if (!isOnWifiSubnet) {
            throw IOException("LAN-only mode: target must be on the connected Wi-Fi subnet")
        }
        return network.networkHandle
    }

    private fun isSameSubnet(local: Inet4Address, prefixLength: Int, target: InetAddress): Boolean {
        if (prefixLength !in 1..32) return false
        val localValue = local.address.toUnsignedIpv4()
        val targetValue = target.address.toUnsignedIpv4()
        val mask = (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
        return (localValue and mask) == (targetValue and mask)
    }

    private fun ByteArray.toUnsignedIpv4(): Long {
        if (size != 4) return -1L
        return fold(0L) { result, byte ->
            (result shl 8) or (byte.toInt() and 0xFF).toLong()
        }
    }

    @Throws(IOException::class)
    fun requireAllowedIpv4(host: String): InetAddress =
        parseIpv4(host) ?: throw IOException(
            "LAN-only mode: public IPs and hostnames are blocked"
        )
}
