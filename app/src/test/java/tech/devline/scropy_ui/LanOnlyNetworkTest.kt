package tech.devline.scropy_ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanOnlyNetworkTest {

    @Test
    fun allowsPrivateAndLocalIpv4Ranges() {
        listOf(
            "10.0.0.8",
            "172.16.0.1",
            "172.31.255.254",
            "192.168.1.20",
            "169.254.1.2",
            "127.0.0.1",
        ).forEach { assertTrue("Expected LAN address: $it", LanOnlyNetwork.isAllowedIpv4(it)) }
    }

    @Test
    fun rejectsPublicReservedAndNonNumericDestinations() {
        listOf(
            "8.8.8.8",
            "1.1.1.1",
            "172.32.0.1",
            "192.0.2.1",
            "100.64.0.1",
            "0.0.0.0",
            "224.0.0.1",
            "255.255.255.255",
            "example.com",
            "192.168.1.1.example.com",
            "010.000.000.001",
            "192.168.1.999",
            "192.168.1",
            "::1",
        ).forEach { assertFalse("Must be blocked: $it", LanOnlyNetwork.isAllowedIpv4(it)) }
    }
}
