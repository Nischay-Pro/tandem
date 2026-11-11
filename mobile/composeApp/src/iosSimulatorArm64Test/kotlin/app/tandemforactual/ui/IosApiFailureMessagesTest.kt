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
                "network-error" to (null to "Couldn’t reach Tandem at this address. Check the address and connection."),
                "http-404" to (404 to "Tandem’s API route isn’t available. Check the address and the proxy’s /api/v1/ route."),
                "http-405" to (405 to "Tandem’s API route isn’t available. Check the address and the proxy’s /api/v1/ route."),
                "http-400" to (400 to "Tandem rejected the request. Check the details and try again."),
                "http-422" to (422 to "Tandem rejected the request. Check the details and try again."),
                "http-408" to (408 to "Tandem couldn’t complete the request. Wait a moment, then try again."),
                "http-429" to (429 to "Tandem couldn’t complete the request. Wait a moment, then try again."),
                "http-500" to (500 to "Tandem is temporarily unavailable. Try again shortly."),
                "http-502" to (502 to "Tandem is temporarily unavailable. Try again shortly."),
                "http-503" to (503 to "Tandem is temporarily unavailable. Try again shortly."),
                "http-504" to (504 to "Tandem is temporarily unavailable. Try again shortly."),
                "schedule_changed" to (409 to "The scheduled transaction changed in Actual. Refresh the schedule, then try again."),
                "actual_unavailable" to (503 to "Tandem can’t reach Actual. Check the Actual connection, then try again."),
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
        assertEquals("Couldn’t reach Tandem at this address. Check the address and connection.", thrownNetwork)
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
            "This item is no longer available. Refresh the budget and try again.",
            apiFailureMessage("not_found", 404, ApiFailureContext.GENERAL, "fallback"),
        )
    }

    @Test
    fun passwordMessagesRequireAnExplicitCredentialsCode() {
        assertEquals(
            "The password didn’t match this Actual server. Check it and try again.",
            apiFailureMessage("invalid_credentials", 401, ApiFailureContext.SIGN_IN, "fallback"),
        )
        assertEquals(
            "That budget password didn’t unlock this budget. Check it and try again.",
            apiFailureMessage("invalid_budget_password", 422, ApiFailureContext.SYNC, "fallback"),
        )
        assertFalse(
            apiFailureMessage("http-422", 422, ApiFailureContext.SYNC, "fallback").contains("password", ignoreCase = true),
        )
    }

    @Test
    fun operationStatusNotFoundDoesNotLookLikeABudgetWasRemoved() {
        assertEquals(
            "Tandem has no saved status for this edit. Try again, or check Actual before removing it.",
            apiFailureMessage("operation_not_found", 404, ApiFailureContext.SYNC, "fallback"),
        )
        assertEquals(
            "Tandem’s API route isn’t available. Check the address and the proxy’s /api/v1/ route.",
            apiFailureMessage("http-404", 404, ApiFailureContext.SYNC, "fallback"),
        )
        assertFalse(TandemApiException("http-404", 404).indicatesTandemReachable())
        assertTrue(TandemApiException("not_found", 404).indicatesTandemReachable())
        assertFalse(BudgetSyncRemoteException("http-404", 404).indicatesTandemReachable())
        assertTrue(BudgetSyncRemoteException("not_found", 404).indicatesTandemReachable())
    }
}
