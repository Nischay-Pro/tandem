package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosOpenIdCallbackTest {
    @Test
    fun acceptsOnlyTheRegisteredPrivateSchemeCallbackWithPendingState() {
        val token = "4f0c6a1e-2b1d-4c51-9d4e-1a2b3c4d5e6f"
        val state = "S".repeat(43)
        val base = "app.tandemforactual://signin/complete"

        assertEquals(ActualOpenIdReturn(token, state), actualOpenIdReturn("$base/$state/openid-cb?token=$token"))
        assertNull(actualOpenIdReturn("app.tandemforactual:/signin/complete/$state/openid-cb?token=$token"))
        assertNull(actualOpenIdReturn("https://tandem.example/signin/complete/$state/openid-cb?token=$token"))
        assertNull(actualOpenIdReturn("$base/other/openid-cb?token=$token"))
    }
}
