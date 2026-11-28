package app.tandemforactual.ui.feature.onboarding

import app.tandemforactual.api.client.PresentedCertificate
import app.tandemforactual.api.client.TrustedCertificates
import app.tandemforactual.ui.core.state.AppState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UntrustedCertificateTest {
    @AfterTest
    fun forgetTrust() = TrustedCertificates.restore(null)

    @Test
    fun aTrustedCertificateIsSavedAndUsedAfterARestart() {
        val state = AppState()
        state.trustedCertificates = trustedWith(state.trustedCertificates, PresentedCertificate("Budget.Home", "AA:BB"))
        assertEquals("budget.home=AA:BB", state.trustedCertificates)
        TrustedCertificates.restore(null)
        AppState().restorePersistentSettings(state.persistentSettings())
        assertTrue(TrustedCertificates.trusts("budget.home", "AA:BB"))
        assertNull(refusedCertificateFor("not a url"))
    }
}
