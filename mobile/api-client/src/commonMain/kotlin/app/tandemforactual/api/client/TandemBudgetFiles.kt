package app.tandemforactual.api.client

import kotlinx.coroutines.sync.withLock

/** Deletes the budget from the Actual server and sets this device's copy aside, so no device can sync it again. */
suspend fun TandemApiClient.deleteBudgetFromServer(budgetId: String) {
    sessionMutex.withLock {
        val client = requireServer()
        call { client.deleteFile(client.budgetFile(budgetId).fileId) }
    }
    retireBudget(budgetId)
}
