package app.tandemforactual.api.client

import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OpenIdPkceAndroidTest {
    @Test
    fun androidPkceProofUsesRandomVerifierAndSha256Challenge() {
        val proof = createOpenIdPkceProof()
        val nextProof = createOpenIdPkceProof()
        val callbackState = createOpenIdCallbackState()
        val nextCallbackState = createOpenIdCallbackState()

        assertEquals(43, proof.codeVerifier.length)
        assertTrue(proof.codeVerifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertEquals(43, proof.codeChallenge.length)
        assertNotEquals(proof.codeVerifier, nextProof.codeVerifier)
        assertEquals(43, callbackState.length)
        assertTrue(callbackState.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertNotEquals(callbackState, nextCallbackState)

        val expectedChallenge =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(proof.codeVerifier.encodeToByteArray()))
        assertEquals(expectedChallenge, proof.codeChallenge)
    }
}
