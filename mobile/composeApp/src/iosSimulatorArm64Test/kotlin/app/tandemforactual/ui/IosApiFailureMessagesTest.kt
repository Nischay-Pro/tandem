package app.tandemforactual.ui

import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.store.BudgetSyncRemoteException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosApiFailureMessagesTest {
    @Test
    fun actualLoginAvailabilityDistinguishesTheFourConnectionOutcomes() =
        runTest {
            assertLoginMethodsOutcomes(loginMethodsOutcomes())
        }

    @Test
    fun signInAndSyncUseTheSameNetworkAndServiceMessages() {
        val cases =
            listOf(
                "network-error" to (null to "Tandem isn’t responding at this address. Check the server address."),
                "http-404" to (404 to "Tandem’s API route isn’t available. Check that your proxy sends /api/v1/ to Tandem."),
                "http-405" to (405 to "Tandem’s API route isn’t available. Check that your proxy sends /api/v1/ to Tandem."),
                "http-400" to (400 to "Tandem couldn’t accept this request. Check the details."),
                "http-422" to (422 to "Tandem couldn’t accept this request. Check the details."),
                "http-408" to (408 to "Tandem couldn’t complete the request. Wait a moment before trying again."),
                "http-429" to (429 to "Tandem couldn’t complete the request. Wait a moment before trying again."),
                "http-500" to (500 to "Tandem is temporarily unavailable. Try again shortly."),
                "http-502" to (502 to "Tandem is temporarily unavailable. Try again shortly."),
                "http-503" to (503 to "Tandem is temporarily unavailable. Try again shortly."),
                "http-504" to (504 to "Tandem is temporarily unavailable. Try again shortly."),
                "schedule_changed" to (409 to "This scheduled transaction changed in Actual. Refresh the schedule before trying again."),
                "actual_unavailable" to (503 to "Tandem can’t connect to Actual. Check that the Actual server is running."),
            )

        cases.forEach { (code, expected) ->
            val signIn = apiFailureMessage(code, expected.first, ApiFailureContext.SIGN_IN, "fallback")
            val sync = apiFailureMessage(code, expected.first, ApiFailureContext.SYNC, "fallback")
            assertEquals(expected.second, signIn, "sign-in: $code")
            assertEquals(signIn, sync, "sync: $code")
            assertFalse(signIn.contains("password", ignoreCase = true), code)
        }

        val thrownNetwork =
            TandemApiException("network-error", cause = IllegalStateException("private host details"))
                .userFacingMessage("fallback", ApiFailureContext.SIGN_IN)
        assertEquals("Tandem isn’t responding at this address. Check the server address.", thrownNetwork)
        assertFalse(thrownNetwork.contains("private host details"))
        val thrownSyncNetwork =
            TandemApiException("network-error", cause = IllegalStateException("private host details"))
                .userFacingMessage("fallback", ApiFailureContext.SYNC)
        assertEquals(thrownNetwork, thrownSyncNetwork)
        assertFalse(thrownSyncNetwork.contains("password", ignoreCase = true))

        listOf(ApiFailureContext.SIGN_IN, ApiFailureContext.SYNC).forEach { context ->
            assertFalse(apiFailureMessage("http-401", 401, context, "fallback").contains("password", ignoreCase = true))
            assertFalse(apiFailureMessage("http-403", 403, context, "fallback").contains("password", ignoreCase = true))
        }

        assertEquals(
            "This budget is no longer available. Choose another budget.",
            apiFailureMessage("not_found", 404, ApiFailureContext.SYNC, "fallback"),
        )
        assertEquals(
            "This item is no longer available. Refresh the budget.",
            apiFailureMessage("not_found", 404, ApiFailureContext.GENERAL, "fallback"),
        )
    }

    @Test
    fun passwordMessagesRequireAnExplicitCredentialsCode() {
        assertEquals(
            "That password doesn’t match this Actual server. Check the password.",
            apiFailureMessage("invalid_credentials", 401, ApiFailureContext.SIGN_IN, "fallback"),
        )
        assertEquals(
            "That password didn’t unlock this encrypted budget. Check the budget password.",
            apiFailureMessage("invalid_budget_password", 422, ApiFailureContext.SYNC, "fallback"),
        )
        assertFalse(
            apiFailureMessage("http-422", 422, ApiFailureContext.SYNC, "fallback").contains("password", ignoreCase = true),
        )
    }

    @Test
    fun operationStatusNotFoundDoesNotLookLikeABudgetWasRemoved() {
        assertEquals(
            "Tandem has no saved status for this edit. Check Actual before removing it.",
            apiFailureMessage("operation_not_found", 404, ApiFailureContext.SYNC, "fallback"),
        )
        assertEquals(
            "Tandem’s API route isn’t available. Check that your proxy sends /api/v1/ to Tandem.",
            apiFailureMessage("http-404", 404, ApiFailureContext.SYNC, "fallback"),
        )
        assertFalse(TandemApiException("http-404", 404).indicatesTandemReachable())
        assertTrue(TandemApiException("not_found", 404).indicatesTandemReachable())
        assertFalse(BudgetSyncRemoteException("http-404", 404).indicatesTandemReachable())
        assertTrue(BudgetSyncRemoteException("not_found", 404).indicatesTandemReachable())
    }
}
