package app.tandemforactual.api.client

import app.tandemforactual.data.actual.setBudgetType

@Suppress("UNUSED_PARAMETER")
suspend fun TandemApiClient.setBudgetType(
    budgetId: String,
    type: String,
    encryptionPassword: String?,
    idempotencyKeyOverride: String? = null,
) = edit(budgetId, encryptionPassword) { it.setBudgetType(type) }
