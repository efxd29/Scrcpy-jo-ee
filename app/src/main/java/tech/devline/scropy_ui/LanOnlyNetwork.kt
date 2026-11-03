package tech.devline.scropy_ui

import java.io.IOException
import java.net.InetAddress

/**
 * App-wide LAN-only destination policy.
 *
 * Only literal IPv4 addresses in RFC1918, IPv4 link-local, or loopback ranges
 * are accepted. Parsing is numeric-only and never performs DNS resolution.
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

    @Throws(IOException::class)
    fun requireAllowedIpv4(host: String): InetAddress =
        parseIpv4(host) ?: throw IOException(
            "LAN-only mode: use a private/local IPv4 address; public IPs and hostnames are blocked"
        )
}
