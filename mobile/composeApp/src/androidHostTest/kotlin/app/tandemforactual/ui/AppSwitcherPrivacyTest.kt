package app.tandemforactual.ui

import app.tandemforactual.ui.core.security.hidesContentInAppSwitcher
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppSwitcherPrivacyTest {
    @Test
    fun anyLockOrPrivacySettingKeepsBalancesOutOfTheAppSwitcher() {
        assertFalse(hidesContentInAppSwitcher(biometricLockEnabled = false, hideAmounts = false, encryptedBudget = false))
        assertTrue(hidesContentInAppSwitcher(biometricLockEnabled = true, hideAmounts = false, encryptedBudget = false))
        assertTrue(hidesContentInAppSwitcher(biometricLockEnabled = false, hideAmounts = true, encryptedBudget = false))
        assertTrue(hidesContentInAppSwitcher(biometricLockEnabled = false, hideAmounts = false, encryptedBudget = true))
    }
}
