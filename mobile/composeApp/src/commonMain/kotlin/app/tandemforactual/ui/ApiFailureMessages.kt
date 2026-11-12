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
            "Actual Budget is responding here, but Tandem isn’t. Check that Tandem is running at this address."
        "address_unreachable", "network-error" -> "Tandem isn’t responding at this address. Check the server address."
        "actual_unavailable" -> "Tandem can’t connect to Actual. Check that the Actual server is running."
        "tandem_not_ready" -> "Tandem is starting up. Try again in a moment."
        "tandem_route_unavailable" ->
            "Tandem isn’t available here. Check the server address."
        "invalid_credentials" -> "That password doesn’t match this Actual server. Check the password."
        "invalid_budget_password" -> "That password didn’t unlock this encrypted budget. Check the budget password."
        "invalid_device_credential", "sign_in_expired" -> "Your sign-in is no longer active. Sign in again."
        "sign_in_required" -> "Sign in to open your budgets."
        "bank_sync_failed" -> "Actual couldn’t sync bank transactions. Check the bank connection."
        "bank_sync_busy" -> "Bank sync is already running. Wait a moment."
        "duplicate_imported_id" -> "Statement rows share a reference. Map a column with unique values."
        "statement_preview_failed" -> "Tandem couldn’t check which statement rows are already in Actual. Check your connection."
        "actual_operation_failed" -> "Actual couldn’t save this change. Refresh the budget before trying again."
        "budget_sync_failed" -> "Tandem couldn’t refresh this budget from Actual. Check the Actual connection."
        "operation_uncertain" -> "Tandem can’t confirm whether Actual saved this change. Check Actual before changing it again."
        "operation_in_progress" -> "Tandem is still processing this change. Wait for it to finish."
        "operation_not_found" -> "Tandem can’t find this edit’s status. Check Actual before removing the edit."
        "schedule_changed" -> "This scheduled transaction changed in Actual. Refresh the schedule before trying again."
        "operation_rejected" -> "Actual rejected this change earlier. Refresh the budget before trying again."
        "idempotency_key_reused" -> "This change is linked to a different request. Refresh the budget before trying again."
        "operation_result_unavailable" ->
            "Actual saved this change, but Tandem hasn’t refreshed the budget. Sync the budget to update this device."
        "invalid_request" ->
            if (context == ApiFailureContext.SIGN_IN) {
                "Tandem couldn’t start sign-in. Check the server address and try again."
            } else {
                "Tandem couldn’t use those details. Check the form."
            }
        "not_found" ->
            if (context == ApiFailureContext.SYNC) {
                "This budget is no longer available. Choose another budget."
            } else {
                "This item is no longer available. Refresh the budget."
            }
        else ->
            when (effectiveStatus) {
                400, 422 -> "Tandem couldn’t accept this request. Check the details."
                401 ->
                    "Tandem can’t verify this device. Sign in again."
                403 ->
                    "This device doesn’t have access to this budget. Ask the budget owner to grant access."
                404, 405 -> "Tandem isn’t available here. Check the server address."
                408, 425, 429 -> "Tandem couldn’t complete the request. Wait a moment before trying again."
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
