package app.tandemforactual.data.store

import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.database.TandemDatabase

enum class LocalWriteAction {
    CREATE,
    UPDATE,
    DELETE,
}

enum class LocalWriteStatus {
    QUEUED,
    SENDING,
    UNCERTAIN,
    CONFLICT,
    COMMITTED,
    REJECTED,
    SUPERSEDED,
    DISCARDED,
}

enum class BudgetSyncStatus {
    IDLE,
    SYNCING,
    READY,
    NEEDS_FULL_SYNC,
    FAILED,
}

data class RemoteBudgetRecord(
    val budgetId: String,
    val recordType: String,
    val recordId: String,
    val revision: String,
    val bodyJson: String,
    val generation: String,
    val recordDate: String? = null,
    val accountId: String? = null,
    val categoryId: String? = null,
    val payeeId: String? = null,
    val amountMinor: Long? = null,
    val budgetMonth: String? = null,
    val displayMetadata: BudgetRecordDisplayMetadata? = null,
)

data class LocalRecordProjection(
    val recordDate: String?,
    val accountId: String?,
    val categoryId: String?,
    val payeeId: String?,
    val amountMinor: Long?,
    val budgetMonth: String?,
)

sealed interface BudgetChange {
    val budgetId: String
    val recordType: String
    val recordId: String

    data class Upsert(
        val record: RemoteBudgetRecord,
    ) : BudgetChange {
        override val budgetId: String get() = record.budgetId
        override val recordType: String get() = record.recordType
        override val recordId: String get() = record.recordId
    }

    data class Delete(
        override val budgetId: String,
        override val recordType: String,
        override val recordId: String,
        val revision: String?,
    ) : BudgetChange
}

data class BudgetChangePage(
    val budgetId: String,
    val generation: String,
    val fromCursor: String?,
    val nextCursor: String,
    val changes: List<BudgetChange>,
    val displaySettings: BudgetDisplaySettings? = null,
)

data class BudgetSyncCheckpoint(
    val generation: String,
    val cursor: String?,
)

enum class FullSnapshotResult {
    APPLIED,
    STALE_CHECKPOINT,
}

data class LocalWriteRequest(
    val operationId: String,
    val idempotencyKey: String,
    val budgetId: String,
    val recordType: String,
    val recordId: String,
    val action: LocalWriteAction,
    val baseRevision: String?,
    val baseSnapshotJson: String?,
    val patchJson: String,
    val optimisticJson: String?,
    val createdAtEpochMs: Long,
    val optimisticProjection: LocalRecordProjection? = null,
    val supersedesConflictOperationId: String? = null,
)

data class LocalBudgetMutationOutboxItem(
    val operationId: String,
    val budgetId: String,
    val action: String,
    val recordId: String,
    val patchJson: String,
    val createdAtEpochMs: Long,
)

data class LocalScheduleActionOutboxItem(
    val operationId: String,
    val budgetId: String,
    val scheduleId: String,
    val action: String,
    val payloadJson: String,
    val createdAtEpochMs: Long,
)

data class LocalBudgetMutationQueueSnapshot(
    val items: List<OutboxOperationEntity>,
    val metadataJson: String?,
)

data class EnqueuedLocalWrite(
    val operation: OutboxOperationEntity,
    val replayed: Boolean,
    val replacedUnsent: Boolean,
)

enum class ChangePageResult {
    APPLIED,
    NEEDS_FULL_SYNC,
}

class LocalIdempotencyConflictException :
    IllegalStateException(
        "An idempotency key was reused with a different local write",
    )

class LocalOperationConflictException :
    IllegalStateException(
        "A write for this record is already in flight or needs resolution",
    )

class InvalidLocalWriteException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

class LocalBudgetRepository(
    internal val database: TandemDatabase,
    internal val now: () -> Long,
)
