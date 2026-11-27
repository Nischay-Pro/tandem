package app.tandemforactual.android

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyProtectionTest {
    @Test
    fun theSecureFlagHidesRecentsOnlyWhereTheSystemCantSkipTheSnapshot() {
        assertTrue(usesSecureWindowFlag(Build.VERSION_CODES.S_V2, hidden = true))
        assertFalse(usesSecureWindowFlag(Build.VERSION_CODES.S_V2, hidden = false))
        assertFalse(usesSecureWindowFlag(Build.VERSION_CODES.TIRAMISU, hidden = true))
    }

    @Test
    fun secretsStayInStrongBoxWhereThePhoneHasOne() {
        assertTrue(prefersStrongBox(Build.VERSION_CODES.P, hasStrongBox = true))
        assertFalse(prefersStrongBox(Build.VERSION_CODES.P, hasStrongBox = false))
        assertFalse(prefersStrongBox(Build.VERSION_CODES.O_MR1, hasStrongBox = true))
    }
}
