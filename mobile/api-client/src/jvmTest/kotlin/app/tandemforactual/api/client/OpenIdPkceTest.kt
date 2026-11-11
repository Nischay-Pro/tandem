package app.tandemforactual.api.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OpenIdPkceTest {
    @Test
    fun challengeMatchesTheRfc7636S256Example() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", openIdPkceS256Challenge(verifier))
    }

    @Test
    fun generatedProofUsesFreshBase64UrlVerifierAndMatchingChallenge() {
        val first = createOpenIdPkceProof()
        val second = createOpenIdPkceProof()

        assertEquals(43, first.codeVerifier.length)
        assertTrue(first.codeVerifier.matches(Regex("[A-Za-z0-9_-]{43}")))
        assertEquals(openIdPkceS256Challenge(first.codeVerifier), first.codeChallenge)
        assertNotEquals(first.codeVerifier, second.codeVerifier)
        assertFalse(first.toString().contains(first.codeVerifier))
        assertFalse(first.toString().contains(first.codeChallenge))
    }
}
