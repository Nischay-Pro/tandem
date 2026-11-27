package app.tandemforactual.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidLanAccessTest {
    @Test
    fun privateAndLinkLocalAddressesAreOnTheLocalNetwork() {
        listOf(
            "http://192.168.1.10:5006",
            "http://10.0.0.2",
            "https://172.20.1.1/actual",
            "169.254.3.4:5006",
            "http://[fd12:3456::1]:5006",
            "http://[fe80::1]",
            "http://nas.local:5006",
        ).forEach { assertTrue(it, isOnLocalNetwork(it)) }
    }

    @Test
    fun publicLoopbackAndUnreadableAddressesAreNot() {
        listOf(
            "https://8.8.8.8",
            "http://[2001:4860:4860::8888]",
            "http://127.0.0.1:5006",
            "http://[::1]",
            "",
            "not a url",
        ).forEach { assertFalse(it, isOnLocalNetwork(it)) }
    }
}
