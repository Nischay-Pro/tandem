package app.tandemforactual.ui

import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.store.BudgetSyncRemoteException

internal enum class ApiFailureContext {
    SIGN_IN,
    SYNC,
    GENERAL,
}

internal fun apiFailureMessage(
    errorCode: String,
    httpStatus: Int?,
    context: ApiFailureContext,
    fallback: String,
): String {
    val effectiveStatus = apiFailureHttpStatus(errorCode, httpStatus)
    return when (errorCode) {
        "actual_only" ->
            "Actual is running at this address, but Tandem isn’t. Check that Tandem is running and that the proxy sends /api/v1/ to it."
        "address_unreachable", "network-error" -> "Couldn’t reach Tandem at this address. Check the address and connection."
        "actual_unavailable" -> "Tandem can’t reach Actual. Check the Actual connection, then try again."
        "tandem_not_ready" -> "Tandem isn’t ready yet. Check its connection to Actual, then try again."
        "tandem_route_unavailable" ->
            "Tandem’s sign-in route isn’t available. Check the Tandem API address and the proxy’s /api/v1/ route."
        "invalid_credentials" -> "The password didn’t match this Actual server. Check it and try again."
        "invalid_budget_password" -> "That budget password didn’t unlock this budget. Check it and try again."
        "invalid_device_credential", "sign_in_expired" -> "Sign in again to restore access."
        "sign_in_required" -> "Sign in to open a budget."
        "bank_sync_failed" -> "Actual couldn’t sync your bank transactions. Check the bank connection and try again."
        "bank_sync_busy" -> "Bank sync is already running. Try again shortly."
        "duplicate_imported_id" -> "Two statement rows share a reference. Choose a column with unique values."
        "actual_operation_failed" -> "Actual couldn’t complete this change. Check the Actual connection and try again."
        "budget_sync_failed" -> "Tandem couldn’t refresh this budget from Actual. Check the Actual connection and try again."
        "operation_uncertain" -> "Tandem couldn’t confirm whether Actual saved this change. Check Actual, then try again."
        "operation_in_progress" -> "Tandem is still processing this change. Wait a moment, then try again."
        "operation_not_found" -> "Tandem has no saved status for this edit. Try again, or check Actual before removing it."
        "schedule_changed" -> "The scheduled transaction changed in Actual. Refresh the schedule, then try again."
        "operation_rejected" -> "Actual rejected this change earlier. Refresh the budget, then try again."
        "idempotency_key_reused" -> "Tandem matched this change to a different request. Refresh the budget, then try again."
        "operation_result_unavailable" -> "Actual saved this change, but Tandem needs a fresh budget copy. Refresh the budget."
        "invalid_request" -> "Check the details and try again."
        "not_found" ->
            if (context == ApiFailureContext.SYNC) {
                "This budget is no longer available. Choose another budget."
            } else {
                "This item is no longer available. Refresh the budget and try again."
            }
        else ->
            when (effectiveStatus) {
                400, 422 -> "Tandem rejected the request. Check the details and try again."
                401 ->
                    if (context == ApiFailureContext.SYNC) {
                        "Tandem couldn’t verify this device. Sign in again, then sync."
                    } else {
                        "Tandem couldn’t verify this request. Sign in again and try again."
                    }
                403 ->
                    if (context == ApiFailureContext.SYNC) {
                        "This device can’t open this budget. Ask the budget owner for access."
                    } else {
                        "This device can’t open that budget. Ask the budget owner for access."
                    }
                404, 405 -> "Tandem’s API route isn’t available. Check the address and the proxy’s /api/v1/ route."
                408, 425, 429 -> "Tandem couldn’t complete the request. Wait a moment, then try again."
                in 500..599 -> "Tandem is temporarily unavailable. Try again shortly."
                else -> fallback
            }
    }
}

internal fun apiFailureHttpStatus(
    errorCode: String,
    httpStatus: Int?,
): Int? = httpStatus ?: errorCode.removePrefix("http-").toIntOrNull()

internal fun TandemApiException.indicatesTandemReachable(): Boolean = errorCode.indicatesTandemReachable(httpStatus)

internal fun BudgetSyncRemoteException.indicatesTandemReachable(): Boolean = errorCode.indicatesTandemReachable(httpStatus)

private fun String.indicatesTandemReachable(httpStatus: Int?): Boolean =
    httpStatus != null && this !in setOf("network-error", "address_unreachable", "actual_only", "http-404")
