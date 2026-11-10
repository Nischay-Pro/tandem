package app.tandemforactual.android

import app.tandemforactual.api.client.TandemApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundSyncFailureActionTest {
    @Test
    fun missingAlertPreferencesKeepTheSharedDefaultEnabled() {
        assertTrue(backgroundAlertEnabled(null))
        assertTrue(backgroundAlertEnabled("true"))
        assertFalse(backgroundAlertEnabled("false"))
    }

    @Test
    fun rejectedDeviceCredentialRequiresSignInAgain() {
        val error = TandemApiException("invalid_device_credential", httpStatus = 401)

        assertEquals(BackgroundSyncFailureAction.PAIR_AGAIN, error.backgroundSyncFailureAction())
    }

    @Test
    fun genericUnauthorizedResponseRetriesWithoutClearingSignIn() {
        val error = TandemApiException("http-401", httpStatus = 401)

        assertEquals(BackgroundSyncFailureAction.RETRY, error.backgroundSyncFailureAction())
    }

    @Test
    fun transientServerFailuresRetry() {
        val error = TandemApiException("http-503", httpStatus = 503)

        assertEquals(BackgroundSyncFailureAction.RETRY, error.backgroundSyncFailureAction())
    }

    @Test
    fun otherClientErrorsFinishWithoutRetrying() {
        val error = TandemApiException("http-403", httpStatus = 403)

        assertEquals(BackgroundSyncFailureAction.FINISH, error.backgroundSyncFailureAction())
    }
}
