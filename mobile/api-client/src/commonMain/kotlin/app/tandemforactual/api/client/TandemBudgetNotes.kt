package app.tandemforactual.api.client

import app.tandemforactual.data.actual.setAccountNote
import app.tandemforactual.data.actual.setCategoryGroupNote
import app.tandemforactual.data.actual.setMonthNote

@Suppress("UNUSED_PARAMETER")
suspend fun TandemApiClient.setBudgetCategoryGroupNote(
    budgetId: String,
    groupId: String,
    note: String,
    encryptionPassword: String?,
    idempotencyKey: String? = null,
) = edit(budgetId, encryptionPassword) { it.setCategoryGroupNote(groupId, note) }

@Suppress("UNUSED_PARAMETER")
suspend fun TandemApiClient.setBudgetAccountNote(
    budgetId: String,
    accountId: String,
    note: String,
    encryptionPassword: String?,
    idempotencyKey: String? = null,
) = edit(budgetId, encryptionPassword) { it.setAccountNote(accountId, note) }

@Suppress("UNUSED_PARAMETER")
suspend fun TandemApiClient.setBudgetMonthNote(
    budgetId: String,
    month: String,
    note: String,
    encryptionPassword: String?,
    idempotencyKey: String? = null,
) = edit(budgetId, encryptionPassword) { it.setMonthNote(month, note) }
