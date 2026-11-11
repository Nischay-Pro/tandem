package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosOpenIdCallbackTest {
    @Test
    fun acceptsOnlyTheRegisteredPrivateSchemeCallbackWithPendingState() {
        val code = "C".repeat(43)
        val state = "S".repeat(43)

        assertEquals(ActualOpenIdReturn(code, state), actualOpenIdReturn("app.tandemforactual://signin/complete?code=$code&state=$state"))
        assertNull(actualOpenIdReturn("app.tandemforactual:/signin/complete?code=$code&state=$state"))
        assertNull(actualOpenIdReturn("https://tandem.example/signin/complete?code=$code&state=$state"))
        assertNull(actualOpenIdReturn("app.tandemforactual://signin/complete?code=$code&state=other"))
        assertNull(actualOpenIdReturn("app.tandemforactual://signin/complete?code=short&state=$state"))
    }
}
