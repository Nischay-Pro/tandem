package app.tandemforactual.ui

import app.tandemforactual.ui.feature.auth.biometricUnlockAfterUnlock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BiometricUnlockSettingTest {
    @Test
    fun followsHowTheEncryptedBudgetWasUnlocked() {
        assertTrue(biometricUnlockAfterUnlock(current = false, fromBiometrics = true, kept = null))
        assertTrue(biometricUnlockAfterUnlock(current = false, fromBiometrics = false, kept = true))
        assertFalse(biometricUnlockAfterUnlock(current = true, fromBiometrics = false, kept = false))
        assertTrue(biometricUnlockAfterUnlock(current = true, fromBiometrics = false, kept = null))
        assertFalse(biometricUnlockAfterUnlock(current = false, fromBiometrics = false, kept = null))
    }
}
