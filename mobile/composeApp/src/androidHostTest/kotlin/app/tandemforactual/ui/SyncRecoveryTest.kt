package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncRecoveryTest {
    @Test
    fun signInProblemsAskToSignInAgain() {
        listOf("invalid_device_credential", "sign_in_expired", "sign_in_required", IDENTITY_MISMATCH_ERROR_CODE, "http-401")
            .forEach { assertEquals(SyncRecovery.SIGN_IN, syncRecovery(it), it) }
        assertEquals(SyncRecovery.SIGN_IN, syncRecovery("unexpected", 401))
    }

    @Test
    fun wrongAddressesAskToCheckTheServer() {
        listOf("actual_only", "tandem_route_unavailable", "http-404", "http-405")
            .forEach { assertEquals(SyncRecovery.CHECK_SERVER, syncRecovery(it), it) }
    }

    @Test
    fun missingBudgetsAskToChooseAnother() {
        assertEquals(SyncRecovery.CHOOSE_BUDGET, syncRecovery("not_found", 404))
        assertEquals(SyncRecovery.CHOOSE_BUDGET, syncRecovery("budget_unavailable"))
        assertEquals(SyncRecovery.DOWNLOAD_AGAIN, syncRecovery("file_has_reset", 400))
        assertEquals(SyncRecovery.DOWNLOAD_AGAIN, syncRecovery("file_has_new_key"))
    }

    @Test
    fun everythingElseRetries() {
        listOf(null, "network-error", "budget_sync_failed", "http-503", "budget_password_incorrect")
            .forEach { assertEquals(SyncRecovery.RETRY, syncRecovery(it), it.toString()) }
    }
}
