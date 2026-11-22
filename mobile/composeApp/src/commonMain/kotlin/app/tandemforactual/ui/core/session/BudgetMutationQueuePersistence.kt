package app.tandemforactual.ui.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.store.LocalBudgetMutationOutboxItem
import app.tandemforactual.data.store.LocalBudgetMutationQueueSnapshot
import app.tandemforactual.data.store.LocalBudgetRepository

internal interface BudgetMutationQueuePersistence {
    suspend fun read(): LocalBudgetMutationQueueSnapshot

    suspend fun replace(
        items: List<LocalBudgetMutationOutboxItem>,
        metadataJson: String,
        supersededOperationIds: Set<String> = emptySet(),
    )
}

internal class LocalBudgetRepositoryMutationQueuePersistence(
    internal val repository: LocalBudgetRepository,
) : BudgetMutationQueuePersistence {
    override suspend fun read(): LocalBudgetMutationQueueSnapshot = repository.readBudgetMutationQueue()

    override suspend fun replace(
        items: List<LocalBudgetMutationOutboxItem>,
        metadataJson: String,
        supersededOperationIds: Set<String>,
    ) = repository.replaceBudgetMutationQueue(items, metadataJson, supersededOperationIds)
}
