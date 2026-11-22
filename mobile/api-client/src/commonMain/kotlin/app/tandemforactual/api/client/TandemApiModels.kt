package app.tandemforactual.api.client

data class AuthenticatedDevice(
    val deviceId: String,
    val principalId: String,
    val budgetIds: List<String>,
)

data class BudgetCatalogItem(
    val budgetId: String,
    val name: String,
    val isEncrypted: Boolean,
)

data class ActualLoginMethodOption(
    val method: String,
    val displayName: String,
    val active: Boolean,
)

data class ActualLoginMethods(
    val bootstrapped: Boolean,
    val loginMethod: String?,
    val availableLoginMethods: List<ActualLoginMethodOption>,
)

data class StatementImportItem(
    val date: String,
    val amountMinor: Long,
    val payeeName: String,
    val notes: String?,
    val importedId: String,
)

data class StatementImportSummary(
    val added: Int,
    val updated: Int,
    val errorCount: Int,
)

data class StatementImportPreview(
    val existingImportedIds: Set<String>,
    val serverChecked: Boolean = true,
) {
    fun newRowCount(totalRows: Int): Int = (totalRows - existingImportedIds.size).coerceAtLeast(0)
}

private val errorMessages =
    mapOf(
        "address_unreachable" to "Tandem can’t reach an Actual server at this address. Check the server address.",
        "sign_in_required" to "Sign in to open your budgets.",
        "sign_in_expired" to "Your sign-in is no longer active. Sign in again.",
        "invalid_credentials" to "That password doesn’t match this Actual server. Check the password.",
        "budget_unavailable" to "This budget isn’t on the Actual server any more.",
        "encrypted_budget" to "Tandem can’t open encrypted budgets yet.",
        "file_has_reset" to "This budget’s sync history was reset in Actual. Remove it from this device and open it again.",
        "file_has_new_key" to "This budget’s encryption key changed in Actual. Remove it from this device and open it again.",
        "out_of_sync" to "Tandem and Actual couldn’t agree on this budget’s changes. Try syncing again.",
        "clock_drift" to "This device’s clock is too far from the server’s. Check the date and time.",
        "not_supported" to "Use Actual for this until Tandem supports it.",
        "rule_unsupported" to "A rule uses a formula or template that Tandem can’t run yet. Make this change in Actual.",
        "invalid_request" to "Tandem couldn’t use those details. Check the form.",
        "invalid-server-url" to "Enter a server address that starts with https://.",
    )

class TandemApiException(
    val errorCode: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
    safeMessageOverride: String? = null,
) : Exception(
        safeMessageOverride ?: errorMessages[errorCode] ?: "Tandem couldn’t reach Actual. Check your connection and try again.",
        cause,
    )

class TandemApiProtocolException(
    message: String,
) : IllegalStateException(message)
