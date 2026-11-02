package app.tandemforactual.data.store

import androidx.room3.Room
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class LocalBudgetRepositoryTest {
    @Test
    fun writesAreOptimisticDurableAndIdempotent() =
        runBlocking {
            val database = newDatabase()
            try {
                var now = 100L
                val repository = LocalBudgetRepository(database) { now }
                repository.applySnapshot(BUDGET, GENERATION, "c1", listOf(transaction()))

                val request = updateRequest()
                val first = repository.enqueueWrite(request)
                assertFalse(first.replayed)
                assertFalse(first.replacedUnsent)
                assertEquals("queued", first.operation.status)
                assertEquals(1, repository.readPendingWrites(BUDGET).size)
                assertEquals("food", first.operation.optimisticCategoryId)
                assertEquals(
                    "{\"id\":\"txn-1\",\"amount\":1200}",
                    repository.findRecords(BUDGET, "transaction").single().localJson,
                )
                assertEquals(transaction().bodyJson, repository.findRecord(BUDGET, "transaction", "txn-1")?.remoteJson)

                now = 200L
                val replay = repository.enqueueWrite(request)
                assertTrue(replay.replayed)
                assertEquals(100L, replay.operation.createdAtEpochMs)
                assertEquals(1, repository.readPendingWrites(BUDGET).size)
            } finally {
                database.close()
            }
        }

    @Test
    fun persistedStatusNamesRemainStableForQueueRecovery() {
        assertEquals(
            listOf("IDLE", "SYNCING", "READY", "NEEDS_FULL_SYNC", "FAILED"),
            BudgetSyncStatus.entries.map { it.name },
        )
        assertEquals(
            listOf("QUEUED", "SENDING", "UNCERTAIN", "CONFLICT", "COMMITTED", "REJECTED", "SUPERSEDED", "DISCARDED"),
            LocalWriteStatus.entries.map { it.name },
        )
        assertEquals(listOf("CREATE", "UPDATE", "DELETE"), LocalWriteAction.entries.map { it.name })
    }

    @Test
    fun unsentCreateCanBeEditedWithoutCreatingAnotherOutboxEntry() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val first =
                    updateRequest(
                        operationId = "create-1",
                        idempotencyKey = "create-key-1",
                        recordId = "txn-local",
                        action = LocalWriteAction.CREATE,
                        baseRevision = null,
                        baseSnapshot = null,
                        patch = "{\"amount\":1000}",
                        optimistic = "{\"id\":\"txn-local\",\"amount\":1000}",
                    )
                repository.enqueueWrite(first)
                val replacement =
                    first.copy(
                        operationId = "create-2",
                        idempotencyKey = "create-key-2",
                        patchJson = "{\"amount\":1500}",
                        optimisticJson = "{\"id\":\"txn-local\",\"amount\":1500}",
                    )

                val result = repository.enqueueWrite(replacement)

                assertTrue(result.replacedUnsent)
                assertEquals("create-2", result.operation.operationId)
                assertEquals(listOf("create-2"), repository.readPendingWrites(BUDGET).map { it.operationId })
                assertEquals(replacement.optimisticJson, repository.findRecords(BUDGET, "transaction").single().localJson)
            } finally {
                database.close()
            }
        }

    @Test
    fun fullSnapshotRejectsWrongGenerationAndDuplicateRecordsAtomically() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applySnapshot(BUDGET, GENERATION, "c1", listOf(transaction()))

                assertFails<InvalidLocalWriteException> {
                    repository.applySnapshot(BUDGET, "generation-2", "c2", listOf(transaction()))
                }
                assertFails<InvalidLocalWriteException> {
                    repository.applySnapshot(
                        BUDGET,
                        GENERATION,
                        "c3",
                        listOf(transaction(), transaction(revision = "revision-duplicate")),
                    )
                }
                assertFails<InvalidLocalWriteException> {
                    repository.applySnapshot(
                        BUDGET,
                        GENERATION,
                        "c4",
                        listOf(transaction().copy(budgetId = "another-budget")),
                    )
                }

                assertEquals(transaction().bodyJson, repository.findRecord(BUDGET, "transaction", "txn-1")?.remoteJson)
                assertEquals("c1", repository.readSyncState(BUDGET)?.cursor)
            } finally {
                database.close()
            }
        }

    @Test
    fun pendingWritesSurviveDatabaseReopenAndSendingWritesBecomeUncertain() =
        runBlocking {
            val databaseFile = File.createTempFile("companion-budget-", ".db")
            databaseFile.delete()
            try {
                val firstDatabase = persistentDatabase(databaseFile.absolutePath)
                val firstRepository = LocalBudgetRepository(firstDatabase) { 100L }
                firstRepository.applySnapshot(BUDGET, GENERATION, "c1", listOf(transaction()))
                val write = firstRepository.enqueueWrite(updateRequest())
                firstRepository.claimNextWrite(BUDGET)
                firstDatabase.close()

                val reopenedDatabase = persistentDatabase(databaseFile.absolutePath)
                try {
                    val reopenedRepository = LocalBudgetRepository(reopenedDatabase) { 200L }
                    assertEquals("c1", reopenedRepository.readSyncState(BUDGET)?.cursor)
                    assertEquals("sending", reopenedRepository.readPendingWrites(BUDGET).single().status)
                    assertEquals(1, reopenedRepository.recoverInterruptedWrites())

                    val recovered = reopenedRepository.readPendingWrites(BUDGET).single()
                    assertEquals(write.operation.operationId, recovered.operationId)
                    assertEquals("uncertain", recovered.status)
                    assertEquals(
                        "{\"id\":\"txn-1\",\"amount\":1200}",
                        reopenedRepository.findRecords(BUDGET, "transaction").single().localJson,
                    )
                } finally {
                    reopenedDatabase.close()
                }
            } finally {
                File(databaseFile.absolutePath + "-wal").delete()
                File(databaseFile.absolutePath + "-shm").delete()
                databaseFile.delete()
            }
        }

    @Test
    fun queuedWritesUseOperationIdAsTheTieBreakerForPerRecordOrdering() =
        runBlocking {
            val database = newDatabase()
            try {
                val outbox = database.outboxDao()
                val queuedBeforeLaterBlocker =
                    outboxOperation(
                        operationId = "op-02",
                        idempotencyKey = "key-02",
                        recordId = "txn-tie",
                        status = "queued",
                        attemptCount = 0,
                    )
                outbox.insert(queuedBeforeLaterBlocker)
                outbox.insert(
                    queuedBeforeLaterBlocker.copy(
                        operationId = "op-10",
                        idempotencyKey = "key-10",
                        status = "uncertain",
                        attemptCount = 1,
                    ),
                )
                outbox.insert(
                    outboxOperation(
                        operationId = "op-22",
                        idempotencyKey = "key-22",
                        recordId = "txn-blocked",
                        status = "queued",
                        attemptCount = 0,
                    ),
                )
                outbox.insert(
                    outboxOperation(
                        operationId = "op-11",
                        idempotencyKey = "key-11",
                        recordId = "txn-blocked",
                        status = "uncertain",
                        attemptCount = 1,
                    ),
                )

                assertEquals("op-02", outbox.findNextQueued(BUDGET)?.operationId)
                assertEquals(1, outbox.markSending("op-02", 200L))
                assertNull(outbox.findNextQueued(BUDGET))
            } finally {
                database.close()
            }
        }

    @Test
    fun invalidWriteBasesAndRemoteIdentitiesAreRejectedWithoutChangingCachedData() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applySnapshot(BUDGET, GENERATION, "c1", listOf(transaction()))

                assertFails<InvalidLocalWriteException> {
                    repository.enqueueWrite(updateRequest(recordId = "missing"))
                }
                assertFails<InvalidLocalWriteException> {
                    repository.enqueueWrite(
                        updateRequest(operationId = "stale", idempotencyKey = "stale-key", baseRevision = "revision-old"),
                    )
                }
                assertFails<InvalidLocalWriteException> {
                    repository.enqueueWrite(
                        updateRequest(
                            operationId = "duplicate-create",
                            idempotencyKey = "duplicate-create-key",
                            action = LocalWriteAction.CREATE,
                            baseRevision = null,
                            baseSnapshot = null,
                            optimistic = "{\"id\":\"txn-1\",\"amount\":2000}",
                        ),
                    )
                }
                assertFails<InvalidLocalWriteException> {
                    repository.enqueueWrite(
                        updateRequest(
                            operationId = "create-without-projection",
                            idempotencyKey = "create-without-projection-key",
                            recordId = "txn-new",
                            action = LocalWriteAction.CREATE,
                            baseRevision = null,
                            baseSnapshot = null,
                            optimistic = "{\"id\":\"txn-new\",\"amount\":1000}",
                            projection = null,
                        ),
                    )
                }
                assertFails<InvalidLocalWriteException> {
                    repository.enqueueWrite(
                        updateRequest(
                            operationId = "invalid-delete-projection",
                            idempotencyKey = "invalid-delete-projection-key",
                            action = LocalWriteAction.DELETE,
                            optimistic = null,
                            projection = LocalRecordProjection(null, null, null, null, null, null),
                        ),
                    )
                }
                assertFails<InvalidLocalWriteException> {
                    repository.enqueueWrite(
                        updateRequest(
                            operationId = "invalid-delete",
                            idempotencyKey = "invalid-delete-key",
                            action = LocalWriteAction.DELETE,
                        ),
                    )
                }

                val queued = repository.enqueueWrite(updateRequest())
                assertFails<InvalidLocalWriteException> {
                    repository.markWriteConflict(queued.operation.operationId, "", transaction())
                }
                assertFails<InvalidLocalWriteException> {
                    repository.markWriteConflict(queued.operation.operationId, "conflict", transaction())
                }
                assertFails<InvalidLocalWriteException> {
                    repository.markWriteCommitted(queued.operation.operationId, transaction())
                }
                repository.claimNextWrite(BUDGET)
                assertFails<InvalidLocalWriteException> {
                    repository.markWriteConflict(queued.operation.operationId, "concurrent-edit", transaction(recordId = "other"))
                }
                assertEquals(transaction().bodyJson, repository.findRecord(BUDGET, "transaction", "txn-1")?.remoteJson)
                assertEquals("sending", database.outboxDao().find(queued.operation.operationId)?.status)
            } finally {
                database.close()
            }
        }

    @Test
    fun nonTransactionDeletesAndUnknownRecordDeletesCreateTombstones() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applySnapshot(BUDGET, GENERATION, "c1", emptyList())
                val result =
                    repository.applyChangePage(
                        BudgetChangePage(
                            budgetId = BUDGET,
                            generation = GENERATION,
                            fromCursor = "c1",
                            nextCursor = "c2",
                            changes =
                                listOf(
                                    BudgetChange.Delete(BUDGET, "category", "category-gone", "category-rev-1"),
                                    BudgetChange.Delete(BUDGET, "transaction", "transaction-gone", "transaction-rev-1"),
                                ),
                        ),
                    )

                assertEquals(ChangePageResult.APPLIED, result)
                assertTrue(assertNotNull(repository.findRecord(BUDGET, "category", "category-gone")).remoteDeleted)
                assertTrue(assertNotNull(repository.findRecord(BUDGET, "transaction", "transaction-gone")).remoteDeleted)
            } finally {
                database.close()
            }
        }

    @Test
    fun invalidChangePagesAndIdentifiersAreRejectedBeforeApplying() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applySnapshot(BUDGET, GENERATION, "c1", listOf(transaction()))

                assertFails<InvalidLocalWriteException> {
                    repository.applyChangePage(
                        BudgetChangePage(
                            budgetId = BUDGET,
                            generation = GENERATION,
                            fromCursor = "c1",
                            nextCursor = "c2",
                            changes = listOf(transactionChange().copy(record = transaction().copy(budgetId = "other-budget"))),
                        ),
                    )
                }
                assertFails<InvalidLocalWriteException> {
                    repository.applyChangePage(
                        BudgetChangePage(
                            budgetId = BUDGET,
                            generation = GENERATION,
                            fromCursor = "c1",
                            nextCursor = "c2",
                            changes = listOf(transactionChange(generation = "other-generation")),
                        ),
                    )
                }
                assertFails<InvalidLocalWriteException> { repository.markSyncFailed(BUDGET, " ") }
                assertEquals("c1", repository.readSyncState(BUDGET)?.cursor)
                assertEquals(listOf("txn-1"), repository.findRecords(BUDGET, "transaction").map { it.recordId })
            } finally {
                database.close()
            }
        }

    @Test
    fun uncertainAndRejectedWritesHaveDurableTerminalStates() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applySnapshot(BUDGET, GENERATION, "c1", listOf(transaction()))
                val uncertain = repository.enqueueWrite(updateRequest())
                assertFails<InvalidLocalWriteException> {
                    repository.markWriteUncertain(uncertain.operation.operationId, "network-timeout")
                }
                repository.claimNextWrite(BUDGET)
                repository.markWriteUncertain(uncertain.operation.operationId, "network-timeout")
                assertEquals("uncertain", repository.readPendingWrites(BUDGET).single().status)

                repository.markWriteRejected(uncertain.operation.operationId, "validation-rejected")
                val rejected = assertNotNull(database.outboxDao().find(uncertain.operation.operationId))
                assertEquals("rejected", rejected.status)
                assertEquals("validation-rejected", rejected.lastErrorCode)
                assertEquals("rejected", repository.readPendingWrites(BUDGET).single().status)
                assertNull(repository.claimNextWrite(BUDGET))
                assertFails<InvalidLocalWriteException> {
                    repository.markWriteRejected(uncertain.operation.operationId, "replayed-rejection")
                }
                assertFails<NoSuchElementException> {
                    repository.markWriteUncertain("missing-operation", "network-timeout")
                }
                assertFails<InvalidLocalWriteException> {
                    repository.markWriteRejected(uncertain.operation.operationId, "")
                }
            } finally {
                database.close()
            }
        }

    @Test
    fun unsentEditsReplaceTheQueuedCommandButCannotChangeItsBase() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applySnapshot(BUDGET, GENERATION, "c1", listOf(transaction()))
                repository.enqueueWrite(updateRequest())

                val replacement =
                    updateRequest(
                        operationId = "op-2",
                        idempotencyKey = "key-2",
                        patch = "{\"amount\":1300,\"notes\":\"coffee\"}",
                        optimistic = "{\"id\":\"txn-1\",\"amount\":1300,\"notes\":\"coffee\"}",
                    )
                val result = repository.enqueueWrite(replacement)

                assertTrue(result.replacedUnsent)
                assertEquals("op-2", result.operation.operationId)
                assertEquals("key-2", result.operation.idempotencyKey)
                assertEquals(1, repository.readPendingWrites(BUDGET).size)
                assertEquals(replacement.optimisticJson, repository.findRecords(BUDGET, "transaction").single().localJson)

                val incompatibleBase =
                    replacement.copy(
                        operationId = "op-3",
                        idempotencyKey = "key-3",
                        baseSnapshotJson = "{\"id\":\"txn-1\",\"amount\":1001}",
                    )
                assertFails<LocalOperationConflictException> { repository.enqueueWrite(incompatibleBase) }
            } finally {
                database.close()
            }
        }

    @Test
    fun changedIdempotencyPayloadAndInvalidJsonAreRejected() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applySnapshot(BUDGET, GENERATION, "c1", listOf(transaction()))
                repository.enqueueWrite(updateRequest())

                val changedPayload = updateRequest(patch = "{\"amount\":9999}")
                assertFails<LocalIdempotencyConflictException> { repository.enqueueWrite(changedPayload) }
                val changedProjection =
                    updateRequest(
                        projection = LocalRecordProjection("2026-10-01", "checking", "rent", "market", 1200L, null),
                    )
                assertFails<LocalIdempotencyConflictException> { repository.enqueueWrite(changedProjection) }
                val malformed = updateRequest(patch = "[]")
                assertFails<InvalidLocalWriteException> { repository.enqueueWrite(malformed) }
                val syntacticallyMalformed = updateRequest(patch = "{")
                assertFails<InvalidLocalWriteException> { repository.enqueueWrite(syntacticallyMalformed) }
                assertFails<InvalidLocalWriteException> {
                    repository.enqueueWrite(
                        updateRequest(
                            operationId = "create-with-base",
                            idempotencyKey = "create-with-base-key",
                            action = LocalWriteAction.CREATE,
                            baseRevision = "revision-1",
                            baseSnapshot = transaction().bodyJson,
                        ),
                    )
                }
                assertFails<InvalidLocalWriteException> {
                    repository.enqueueWrite(
                        updateRequest(
                            operationId = "create-without-record",
                            idempotencyKey = "create-without-record-key",
                            action = LocalWriteAction.CREATE,
                            baseRevision = null,
                            baseSnapshot = null,
                            optimistic = null,
                        ),
                    )
                }
                assertEquals(1, repository.readPendingWrites(BUDGET).size)
            } finally {
                database.close()
            }
        }

    @Test
    fun claimedWritesBecomeUncertainAfterRestartAndBlockLaterWrites() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applySnapshot(BUDGET, GENERATION, "c1", listOf(transaction()))
                repository.enqueueWrite(updateRequest())

                val claim = assertNotNull(repository.claimNextWrite(BUDGET))
                assertEquals("sending", claim.status)
                assertEquals(1, claim.attemptCount)
                assertNull(repository.claimNextWrite(BUDGET))
                assertEquals(1, repository.recoverInterruptedWrites())

                val uncertain = repository.readPendingWrites(BUDGET).single()
                assertEquals("uncertain", uncertain.status)
                assertEquals("key-1", uncertain.idempotencyKey)
                assertNull(repository.claimNextWrite(BUDGET))
                assertFails<LocalOperationConflictException> {
                    repository.enqueueWrite(updateRequest(operationId = "op-2", idempotencyKey = "key-2"))
                }
            } finally {
                database.close()
            }
        }

    @Test
    fun conflictsKeepTheLocalDraftAndRefreshTheRemoteBase() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applySnapshot(BUDGET, GENERATION, "c1", listOf(transaction()))
                val localProjection = LocalRecordProjection("2026-10-03", "savings", "rent", "landlord", 1200L, "2026-10")
                val queued = repository.enqueueWrite(updateRequest(projection = localProjection))
                repository.claimNextWrite(BUDGET)

                val remote =
                    transaction(
                        revision = "revision-2",
                        bodyJson = "{\"id\":\"txn-1\",\"amount\":1500,\"notes\":\"web edit\"}",
                        date = "2026-10-04",
                        accountId = "remote-account",
                        categoryId = "remote-category",
                        payeeId = "remote-payee",
                        amountMinor = 1500L,
                    )
                val conflict = repository.markWriteConflict(queued.operation.operationId, "concurrent-edit", remote)
                val row = assertNotNull(repository.findRecord(BUDGET, "transaction", "txn-1"))

                assertEquals("conflict", conflict.status)
                assertEquals("concurrent-edit", conflict.lastErrorCode)
                assertEquals(remote.bodyJson, row.remoteJson)
                assertEquals("{\"id\":\"txn-1\",\"amount\":1200}", row.localJson)
                assertEquals(localProjection.recordDate, row.recordDate)
                assertEquals(localProjection.accountId, row.accountId)
                assertEquals(localProjection.categoryId, row.categoryId)
                assertEquals(localProjection.payeeId, row.payeeId)
                assertEquals(localProjection.amountMinor, row.amountMinor)
                assertEquals("txn-1", repository.findRecords(BUDGET, "transaction").single().recordId)
            } finally {
                database.close()
            }
        }

    @Test
    fun conflictCanBeResolvedByAWriteBasedOnTheLatestRemoteRevision() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val baseline =
                    transaction(
                        bodyJson =
                            """{"id":"txn-1","amount":1000,"date":"2026-10-01","account":"checking","category":"food","payee":"market","month":"2026-10"}""",
                    )
                repository.applySnapshot(BUDGET, GENERATION, "c1", listOf(baseline))
                val conflicted = repository.enqueueWrite(updateRequest())
                repository.claimNextWrite(BUDGET)
                val remote =
                    transaction(
                        revision = "revision-2",
                        bodyJson =
                            """{"id":"txn-1","amount":1500,"date":"2026-10-02","account":"remote","category":"rent","payee":"landlord","month":"2026-10"}""",
                        date = "2026-10-02",
                        accountId = "remote",
                        categoryId = "rent",
                        payeeId = "landlord",
                        amountMinor = 1500L,
                    )
                repository.markWriteConflict(conflicted.operation.operationId, "concurrent-edit", remote)

                val resolutionRequest =
                    updateRequest(
                        operationId = "op-resolution",
                        idempotencyKey = "key-resolution",
                        baseRevision = remote.revision,
                        baseSnapshot = remote.bodyJson,
                        patch = "{\"amount\":1800}",
                        optimistic = "{\"id\":\"txn-1\",\"amount\":1800}",
                        projection = LocalRecordProjection("2026-10-02", "remote", "rent", "landlord", 1800L, "2026-10"),
                    )
                val resolution = repository.enqueueWrite(resolutionRequest)

                assertEquals("superseded", database.outboxDao().find(conflicted.operation.operationId)?.status)
                assertEquals("queued", resolution.operation.status)
                assertEquals(remote.revision, resolution.operation.baseRevision)
                assertEquals(resolutionRequest.optimisticJson, repository.findRecord(BUDGET, "transaction", "txn-1")?.localJson)
                assertEquals("op-resolution", repository.claimNextWrite(BUDGET)?.operationId)
            } finally {
                database.close()
            }
        }

    @Test
    fun rejectedEditsRestoreRemoteProjectionAndDoNotLeavePhantomCreates() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remoteBody =
                    """{"id":"txn-1","amount":1000,"date":"2026-10-01","account":"checking","category":"food","payee":"market","month":"2026-10"}"""
                repository.applySnapshot(
                    BUDGET,
                    GENERATION,
                    "c1",
                    listOf(transaction(bodyJson = remoteBody, budgetMonth = "2026-10")),
                )
                val update =
                    repository.enqueueWrite(
                        updateRequest(
                            projection = LocalRecordProjection("2026-10-03", "savings", "rent", "landlord", 1200L, "2026-11"),
                        ),
                    )
                repository.claimNextWrite(BUDGET)
                val rejectedUpdate = repository.markWriteRejected(update.operation.operationId, "validation-rejected")
                val restored = assertNotNull(repository.findRecord(BUDGET, "transaction", "txn-1"))

                assertEquals("rejected", rejectedUpdate.status)
                assertNull(restored.localJson)
                assertFalse(restored.localDeleted)
                assertEquals("2026-10-01", restored.recordDate)
                assertEquals("checking", restored.accountId)
                assertEquals("food", restored.categoryId)
                assertEquals("market", restored.payeeId)
                assertEquals(1000L, restored.amountMinor)
                assertEquals("2026-10", restored.budgetMonth)

                val delete =
                    repository.enqueueWrite(
                        updateRequest(
                            operationId = "op-delete-rejected",
                            idempotencyKey = "key-delete-rejected",
                            action = LocalWriteAction.DELETE,
                            patch = "{}",
                            optimistic = null,
                            projection = null,
                            baseSnapshot = remoteBody,
                        ),
                    )
                repository.claimNextWrite(BUDGET)
                repository.markWriteRejected(delete.operation.operationId, "validation-rejected")
                assertEquals(listOf("txn-1"), repository.findRecords(BUDGET, "transaction").map { it.recordId })

                val create =
                    updateRequest(
                        operationId = "op-create-rejected",
                        idempotencyKey = "key-create-rejected",
                        recordId = "txn-local",
                        action = LocalWriteAction.CREATE,
                        baseRevision = null,
                        baseSnapshot = null,
                        patch = "{\"amount\":700}",
                        optimistic = "{\"id\":\"txn-local\",\"amount\":700}",
                    )
                repository.enqueueWrite(create)
                repository.claimNextWrite(BUDGET)
                repository.markWriteRejected(create.operationId, "create-not-supported")

                assertEquals(listOf("txn-1"), repository.findRecords(BUDGET, "transaction").map { it.recordId })
                assertNull(repository.findRecord(BUDGET, "transaction", "txn-local")?.localJson)
                assertEquals("rejected", database.outboxDao().find(create.operationId)?.status)
            } finally {
                database.close()
            }
        }

    @Test
    fun uncertainWritesCanBeExplicitlyRetriedOrDiscarded() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applySnapshot(BUDGET, GENERATION, "c1", listOf(transaction()))
                val write = repository.enqueueWrite(updateRequest())
                repository.claimNextWrite(BUDGET)
                repository.markWriteUncertain(write.operation.operationId, "http-404")

                val retry = repository.retryUncertainWrite(write.operation.operationId)

                assertEquals("queued", retry.status)
                assertEquals(write.operation.operationId, retry.operationId)
                assertEquals(write.operation.idempotencyKey, retry.idempotencyKey)
                assertEquals(1, retry.attemptCount)

                repository.claimNextWrite(BUDGET)
                repository.markWriteUncertain(write.operation.operationId, "network-error")
                val discarded = repository.discardWrite(write.operation.operationId)

                assertEquals("discarded", discarded.status)
                assertEquals("discarded-by-user", discarded.lastErrorCode)
                assertNull(repository.findRecord(BUDGET, "transaction", "txn-1")?.localJson)
                assertEquals(1000L, repository.findRecords(BUDGET, "transaction").single().amountMinor)

                val deletedConflict =
                    repository.enqueueWrite(
                        updateRequest(
                            operationId = "op-deleted-conflict",
                            idempotencyKey = "key-deleted-conflict",
                        ),
                    )
                repository.claimNextWrite(BUDGET)
                repository.markWriteConflict(deletedConflict.operation.operationId, "remote-deleted", null)
                repository.discardWrite(deletedConflict.operation.operationId)

                assertTrue(repository.findRecord(BUDGET, "transaction", "txn-1")?.remoteDeleted == true)
                assertTrue(repository.findRecords(BUDGET, "transaction").isEmpty())
            } finally {
                database.close()
            }
        }

    @Test
    fun optimisticIndexProjectionIsVisibleImmediatelyAndReplacedByCommittedServerProjection() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applySnapshot(BUDGET, GENERATION, "c1", listOf(transaction()))
                val localProjection = LocalRecordProjection("2026-10-03", "savings", "rent", "landlord", 1200L, "2026-10")
                val write =
                    repository.enqueueWrite(
                        updateRequest(
                            patch = "{\"amount\":1200,\"date\":\"2026-10-03\",\"category\":\"rent\"}",
                            optimistic = "{\"id\":\"txn-1\",\"amount\":1200,\"date\":\"2026-10-03\",\"category\":\"rent\"}",
                            projection = localProjection,
                        ),
                    )

                assertTrue(repository.findRecords(BUDGET, "transaction", accountId = "checking").isEmpty())
                assertEquals(
                    listOf("txn-1"),
                    repository
                        .findRecords(
                            BUDGET,
                            "transaction",
                            accountId = "savings",
                            categoryId = "rent",
                            payeeId = "landlord",
                            fromDate = "2026-10-03",
                            throughDate = "2026-10-03",
                        ).map { it.recordId },
                )

                repository.claimNextWrite(BUDGET)
                val serverRecord =
                    transaction(
                        revision = "revision-2",
                        bodyJson = "{\"id\":\"txn-1\",\"amount\":1250}",
                        date = "2026-10-04",
                        accountId = "actual-account",
                        categoryId = "actual-category",
                        payeeId = "actual-payee",
                        amountMinor = 1250L,
                    )
                repository.markWriteCommitted(write.operation.operationId, serverRecord)

                assertTrue(repository.findRecords(BUDGET, "transaction", accountId = "savings").isEmpty())
                assertEquals(
                    listOf("txn-1"),
                    repository
                        .findRecords(
                            BUDGET,
                            "transaction",
                            accountId = "actual-account",
                            categoryId = "actual-category",
                            payeeId = "actual-payee",
                            fromDate = "2026-10-04",
                            throughDate = "2026-10-04",
                        ).map { it.recordId },
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun remoteDeleteConflictsWithAnUnsentEditButKeepsItsDraftVisible() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applySnapshot(BUDGET, GENERATION, "c1", listOf(transaction()))
                repository.enqueueWrite(updateRequest())

                val result =
                    repository.applyChangePage(
                        BudgetChangePage(
                            budgetId = BUDGET,
                            generation = GENERATION,
                            fromCursor = "c1",
                            nextCursor = "c2",
                            changes = listOf(BudgetChange.Delete(BUDGET, "transaction", "txn-1", "r2")),
                        ),
                    )

                val record = assertNotNull(repository.findRecord(BUDGET, "transaction", "txn-1"))
                assertEquals(ChangePageResult.APPLIED, result)
                assertTrue(record.remoteDeleted)
                assertTrue(record.localJson != null)
                assertEquals("conflict", repository.readPendingWrites(BUDGET).single().status)
                assertEquals(listOf("txn-1"), repository.findRecords(BUDGET, "transaction").map { it.recordId })
            } finally {
                database.close()
            }
        }

    @Test
    fun committedUpdatesAndDeletesClearTheirOptimisticState() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applySnapshot(BUDGET, GENERATION, "c1", listOf(transaction()))
                val update = repository.enqueueWrite(updateRequest())
                repository.claimNextWrite(BUDGET)
                val applied =
                    transaction(
                        revision = "revision-2",
                        bodyJson = "{\"id\":\"txn-1\",\"amount\":1200}",
                    )
                val committedUpdate = repository.markWriteCommitted(update.operation.operationId, applied)
                val updatedRecord = assertNotNull(repository.findRecord(BUDGET, "transaction", "txn-1"))
                assertEquals("committed", committedUpdate.status)
                assertEquals("op-1", committedUpdate.serverOperationId)
                assertEquals(applied.bodyJson, updatedRecord.remoteJson)
                assertNull(updatedRecord.localJson)
                assertFails<InvalidLocalWriteException> {
                    repository.markWriteCommitted(update.operation.operationId, applied)
                }

                val delete =
                    repository.enqueueWrite(
                        updateRequest(
                            operationId = "op-delete",
                            idempotencyKey = "key-delete",
                            action = LocalWriteAction.DELETE,
                            patch = "{}",
                            optimistic = null,
                            projection = null,
                            baseRevision = "revision-2",
                            baseSnapshot = applied.bodyJson,
                        ),
                    )
                assertTrue(repository.findRecords(BUDGET, "transaction").isEmpty())
                repository.claimNextWrite(BUDGET)
                val committedDelete = repository.markWriteCommitted(delete.operation.operationId, null)
                val deletedRecord = assertNotNull(repository.findRecord(BUDGET, "transaction", "txn-1"))
                assertEquals("committed", committedDelete.status)
                assertTrue(deletedRecord.remoteDeleted)
                assertNull(deletedRecord.localJson)
                assertTrue(repository.findRecords(BUDGET, "transaction").isEmpty())
            } finally {
                database.close()
            }
        }

    @Test
    fun cursorAndGenerationDivergenceRequireAFullSnapshotWithoutApplyingChanges() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val first =
                    repository.applyChangePage(
                        BudgetChangePage(
                            budgetId = BUDGET,
                            generation = GENERATION,
                            fromCursor = null,
                            nextCursor = "c1",
                            changes = listOf(transactionChange()),
                        ),
                    )
                assertEquals(ChangePageResult.APPLIED, first)
                assertEquals("c1", repository.readSyncState(BUDGET)?.cursor)

                val cursorMismatch =
                    repository.applyChangePage(
                        BudgetChangePage(
                            budgetId = BUDGET,
                            generation = GENERATION,
                            fromCursor = "old-cursor",
                            nextCursor = "c2",
                            changes = listOf(transactionChange(recordId = "should-not-apply")),
                        ),
                    )
                assertEquals(ChangePageResult.NEEDS_FULL_SYNC, cursorMismatch)
                assertNull(repository.findRecord(BUDGET, "transaction", "should-not-apply"))
                assertEquals(BudgetSyncStatus.NEEDS_FULL_SYNC.name, repository.readSyncState(BUDGET)?.status)

                repository.applySnapshot(BUDGET, GENERATION, "c3", listOf(transaction()))
                val generationChange =
                    repository.applyChangePage(
                        BudgetChangePage(
                            budgetId = BUDGET,
                            generation = "restored-generation",
                            fromCursor = "c3",
                            nextCursor = "c4",
                            changes = listOf(transactionChange(recordId = "also-not-applied", generation = "restored-generation")),
                        ),
                    )
                assertEquals(ChangePageResult.NEEDS_FULL_SYNC, generationChange)
                assertNull(repository.findRecord(BUDGET, "transaction", "also-not-applied"))
            } finally {
                database.close()
            }
        }

    @Test
    fun staleFullSnapshotCannotOverwriteChangesAppliedAfterItsRequestStarted() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applySnapshot(BUDGET, GENERATION, "c1", listOf(transaction()))
                val requestCheckpoint = assertNotNull(repository.readSyncCheckpoint(BUDGET))
                val incrementalRecord =
                    transaction(
                        revision = "revision-2",
                        bodyJson = "{\"id\":\"txn-1\",\"amount\":1100}",
                    )
                assertEquals(
                    ChangePageResult.APPLIED,
                    repository.applyChangePage(
                        BudgetChangePage(
                            budgetId = BUDGET,
                            generation = GENERATION,
                            fromCursor = "c1",
                            nextCursor = "c2",
                            changes = listOf(BudgetChange.Upsert(incrementalRecord)),
                        ),
                    ),
                )

                val staleResult =
                    repository.applyFullSnapshot(
                        budgetId = BUDGET,
                        generation = GENERATION,
                        cursor = "c1",
                        expectedCheckpoint = requestCheckpoint,
                        records = listOf(transaction()),
                    )

                assertEquals(FullSnapshotResult.STALE_CHECKPOINT, staleResult)
                assertEquals(incrementalRecord.bodyJson, repository.findRecord(BUDGET, "transaction", "txn-1")?.remoteJson)
                assertEquals("c2", repository.readSyncState(BUDGET)?.cursor)
            } finally {
                database.close()
            }
        }

    @Test
    fun fullSnapshotTombstonesMissingRowsAndPreservesPendingCreation() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applySnapshot(BUDGET, GENERATION, "c1", listOf(transaction()))
                repository.enqueueWrite(updateRequest())
                repository.applySnapshot(BUDGET, "restored-generation", "c2", emptyList())

                val deletedRemote = assertNotNull(repository.findRecord(BUDGET, "transaction", "txn-1"))
                assertTrue(deletedRemote.remoteDeleted)
                assertNull(deletedRemote.remoteJson)
                assertEquals("conflict", repository.readPendingWrites(BUDGET).single().status)
                assertEquals("{\"id\":\"txn-1\",\"amount\":1200}", deletedRemote.localJson)

                val create =
                    updateRequest(
                        operationId = "create-1",
                        idempotencyKey = "create-key",
                        recordId = "txn-local",
                        action = LocalWriteAction.CREATE,
                        baseRevision = null,
                        baseSnapshot = null,
                        patch = "{\"amount\":2500}",
                        optimistic = "{\"id\":\"txn-local\",\"amount\":2500}",
                    )
                repository.enqueueWrite(create)
                repository.applySnapshot(BUDGET, "restored-generation", "c3", emptyList())

                val pendingCreate = repository.readPendingWrites(BUDGET).single { it.operationId == "create-1" }
                val localCreate = assertNotNull(repository.findRecord(BUDGET, "transaction", "txn-local"))
                assertEquals(
                    listOf("txn-1", "txn-local"),
                    repository.findRecords(BUDGET, "transaction").map { it.recordId },
                )
                assertEquals("queued", pendingCreate.status)
                assertFalse(localCreate.remoteDeleted)
            } finally {
                database.close()
            }
        }

    @Test
    fun syncIndicatorsPreserveTheLastSuccessfulCursorAndRecordErrors() =
        runBlocking {
            val database = newDatabase()
            try {
                var now = 10L
                val repository = LocalBudgetRepository(database) { now }
                repository.markSyncStarted(BUDGET)
                assertEquals(BudgetSyncStatus.SYNCING.name, repository.readSyncState(BUDGET)?.status)
                assertNull(repository.readSyncState(BUDGET)?.lastSyncedAtEpochMs)

                repository.applySnapshot(BUDGET, GENERATION, "c1", listOf(transaction()))
                now = 30L
                repository.markSyncStarted(BUDGET)
                repository.markSyncFailed(BUDGET, "network-unavailable")

                val state = assertNotNull(repository.readSyncState(BUDGET))
                assertEquals(BudgetSyncStatus.FAILED.name, state.status)
                assertEquals("network-unavailable", state.lastErrorCode)
                assertEquals("c1", state.cursor)
                assertEquals(10L, state.lastSyncedAtEpochMs)
                assertEquals(30L, state.lastAttemptAtEpochMs)
            } finally {
                database.close()
            }
        }

    @Test
    fun localRecordsSupportLiveObservationAndIndexedFilters() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applySnapshot(
                    BUDGET,
                    GENERATION,
                    "c1",
                    listOf(
                        transaction(
                            recordId = "txn-a",
                            accountId = "checking",
                            categoryId = "food",
                            payeeId = "market",
                            date = "2026-10-02",
                        ),
                        transaction(
                            recordId = "txn-b",
                            accountId = "savings",
                            categoryId = "rent",
                            payeeId = "landlord",
                            date = "2026-10-01",
                        ),
                    ),
                )

                val filtered =
                    repository.findRecords(
                        BUDGET,
                        "transaction",
                        accountId = "checking",
                        categoryId = "food",
                        payeeId = "market",
                        fromDate = "2026-10-01",
                        throughDate = "2026-10-31",
                    )
                val observed = repository.observeRecords(BUDGET, "transaction").first()
                val recent = repository.findRecentRecords(BUDGET, "transaction", limit = 1)
                assertEquals(listOf("txn-a"), filtered.map { it.recordId })
                assertEquals(listOf("txn-a", "txn-b"), observed.map { it.recordId })
                assertEquals(listOf("txn-a"), recent.map { it.recordId })
                assertFails<IllegalArgumentException> { repository.findRecentRecords(BUDGET, "transaction", limit = 0) }
            } finally {
                database.close()
            }
        }

    private fun newDatabase(): TandemDatabase =
        openTandemDatabase(
            Room
                .inMemoryDatabaseBuilder<TandemDatabase>()
                .setQueryCoroutineContext(Dispatchers.IO),
        )

    private suspend fun LocalBudgetRepository.applySnapshot(
        budgetId: String,
        generation: String,
        cursor: String,
        records: List<RemoteBudgetRecord>,
    ): FullSnapshotResult =
        applyFullSnapshot(
            budgetId = budgetId,
            generation = generation,
            cursor = cursor,
            expectedCheckpoint = readSyncCheckpoint(budgetId),
            records = records,
        )

    private fun persistentDatabase(path: String): TandemDatabase =
        openTandemDatabase(
            Room
                .databaseBuilder<TandemDatabase>(name = path)
                .setQueryCoroutineContext(Dispatchers.IO),
        )

    private fun outboxOperation(
        operationId: String,
        idempotencyKey: String,
        recordId: String,
        status: String,
        attemptCount: Int,
    ): OutboxOperationEntity =
        OutboxOperationEntity(
            operationId = operationId,
            idempotencyKey = idempotencyKey,
            budgetId = BUDGET,
            recordType = "transaction",
            recordId = recordId,
            action = LocalWriteAction.UPDATE.name,
            baseRevision = "revision-1",
            baseSnapshotJson = "{}",
            patchJson = "{}",
            optimisticJson = "{}",
            optimisticRecordDate = null,
            optimisticAccountId = null,
            optimisticCategoryId = null,
            optimisticPayeeId = null,
            optimisticAmountMinor = null,
            optimisticBudgetMonth = null,
            status = status,
            attemptCount = attemptCount,
            serverOperationId = null,
            lastErrorCode = null,
            createdAtEpochMs = 100L,
            updatedAtEpochMs = 100L,
        )

    private fun transaction(
        recordId: String = "txn-1",
        revision: String = "revision-1",
        bodyJson: String = "{\"id\":\"$recordId\",\"amount\":1000}",
        generation: String = GENERATION,
        date: String = "2026-10-01",
        accountId: String? = "checking",
        categoryId: String? = "food",
        payeeId: String? = "market",
        amountMinor: Long = 1000L,
        budgetMonth: String? = null,
    ): RemoteBudgetRecord =
        RemoteBudgetRecord(
            budgetId = BUDGET,
            recordType = "transaction",
            recordId = recordId,
            revision = revision,
            bodyJson = bodyJson,
            generation = generation,
            recordDate = date,
            accountId = accountId,
            categoryId = categoryId,
            payeeId = payeeId,
            amountMinor = amountMinor,
            budgetMonth = budgetMonth,
        )

    private fun transactionChange(
        recordId: String = "txn-1",
        generation: String = GENERATION,
    ): BudgetChange.Upsert = BudgetChange.Upsert(transaction(recordId = recordId, generation = generation))

    private fun updateRequest(
        operationId: String = "op-1",
        idempotencyKey: String = "key-1",
        recordId: String = "txn-1",
        action: LocalWriteAction = LocalWriteAction.UPDATE,
        baseRevision: String? = "revision-1",
        baseSnapshot: String? = "{\"id\":\"$recordId\",\"amount\":1000}",
        patch: String = "{\"amount\":1200}",
        optimistic: String? = "{\"id\":\"$recordId\",\"amount\":1200}",
        projection: LocalRecordProjection? =
            LocalRecordProjection(
                recordDate = "2026-10-01",
                accountId = "checking",
                categoryId = "food",
                payeeId = "market",
                amountMinor = 1200L,
                budgetMonth = null,
            ),
    ): LocalWriteRequest =
        LocalWriteRequest(
            operationId = operationId,
            idempotencyKey = idempotencyKey,
            budgetId = BUDGET,
            recordType = "transaction",
            recordId = recordId,
            action = action,
            baseRevision = baseRevision,
            baseSnapshotJson = baseSnapshot,
            patchJson = patch,
            optimisticJson = optimistic,
            optimisticProjection = projection,
            createdAtEpochMs = 100L,
        )

    private suspend inline fun <reified T : Throwable> assertFails(noinline block: suspend () -> Any?) {
        try {
            block()
            fail("Expected ${T::class.simpleName}")
        } catch (error: Throwable) {
            if (error !is T) throw error
        }
    }

    private companion object {
        const val BUDGET = "budget-1"
        const val GENERATION = "generation-1"
    }
}
