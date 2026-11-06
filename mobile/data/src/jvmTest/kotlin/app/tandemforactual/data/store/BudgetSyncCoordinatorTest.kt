package app.tandemforactual.data.store

import androidx.room3.Room
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private data class ChangeRequest(
    val cursor: String,
    val throughCursor: String?,
    val encryptionPassword: String?,
)

class BudgetSyncCoordinatorTest {
    @Test
    fun initialSnapshotThenPagedFeedAppliesUpsertsAndDeletions() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction(), transaction("txn-2"))))
                val coordinator = BudgetSyncCoordinator(repository, remote)

                val first = coordinator.synchronize(BUDGET, sendQueuedWrites = false)
                assertTrue(first.fullSnapshotApplied)
                assertEquals("1", repository.readSyncState(BUDGET)?.cursor)

                remote.changeHandler = { _, generation, cursor, through, _ ->
                    when {
                        cursor == "1" && through == null ->
                            changePage(
                                generation = generation,
                                current = "3",
                                through = "3",
                                next = "2",
                                hasMore = true,
                                changes = listOf(BudgetChange.Upsert(transaction(revision = "revision-2", amount = 1200L))),
                            )
                        cursor == "2" && through == "3" ->
                            changePage(
                                generation = generation,
                                current = "3",
                                through = "3",
                                next = "3",
                                hasMore = false,
                                changes = listOf(BudgetChange.Delete(BUDGET, "transaction", "txn-2", "revision-2")),
                            )
                        else -> error("Unexpected feed cursor $cursor/$through")
                    }
                }

                val result = coordinator.synchronize(BUDGET, encryptionPassword = "budget-secret", sendQueuedWrites = false)

                assertFalse(result.fullSnapshotApplied)
                assertEquals(2, result.changePagesApplied)
                assertEquals(2, result.changesApplied)
                assertEquals("3", repository.readSyncState(BUDGET)?.cursor)
                assertEquals("revision-2", repository.findRecord(BUDGET, "transaction", "txn-1")?.remoteRevision)
                assertTrue(repository.findRecord(BUDGET, "transaction", "txn-2")?.remoteDeleted == true)
                assertEquals(listOf("1", "2"), remote.changeRequests.map { it.cursor })
                assertEquals(listOf(null, "3"), remote.changeRequests.map { it.throughCursor })
                assertEquals(listOf("budget-secret", "budget-secret"), remote.changeRequests.map { it.encryptionPassword })
            } finally {
                database.close()
            }
        }

    @Test
    fun snapshotPagesAreStagedIndividuallyAndAppliedTogether() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val records = listOf(transaction("txn-1"), transaction("txn-2"))
                val remote = FakeBudgetSyncRemote(snapshot(records = records))
                val requests = mutableListOf<String?>()
                remote.snapshotPageHandler = { budgetId, generation, cursor, afterType, afterId, _, _ ->
                    requests += afterId
                    val current = if (afterId == null) records.first() else records.last()
                    RemoteBudgetSnapshotPage(
                        budgetId = budgetId,
                        generation = generation ?: GENERATION,
                        cursor = cursor ?: "2",
                        records = listOf(current),
                        hasMore = afterId == null,
                        resetRequired = false,
                        nextAfterType = if (afterId == null) current.recordType else null,
                        nextAfterId = if (afterId == null) current.recordId else null,
                        displaySettings = null,
                    )
                }

                val result = BudgetSyncCoordinator(repository, remote).synchronize(BUDGET, sendQueuedWrites = false)

                assertTrue(result.fullSnapshotApplied)
                assertEquals(listOf(null, "txn-1"), requests)
                assertEquals(listOf("txn-1", "txn-2"), repository.findRecords(BUDGET, "transaction").map { it.recordId })
                assertEquals(BudgetSyncStatus.READY.name, repository.readSyncState(BUDGET)?.status)
            } finally {
                database.close()
            }
        }

    @Test
    fun twoLocalDevicesApplyTheSameSharedChangeFeed() =
        runBlocking {
            val firstDatabase = newDatabase()
            val secondDatabase = newDatabase()
            try {
                val firstRepository = LocalBudgetRepository(firstDatabase) { 100L }
                val secondRepository = LocalBudgetRepository(secondDatabase) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))
                val firstDevice = BudgetSyncCoordinator(firstRepository, remote)
                val secondDevice = BudgetSyncCoordinator(secondRepository, remote)
                firstDevice.synchronize(BUDGET, sendQueuedWrites = false)
                secondDevice.synchronize(BUDGET, sendQueuedWrites = false)
                remote.changeHandler = { _, generation, cursor, _, _ ->
                    if (cursor == "1") {
                        changePage(
                            generation = generation,
                            current = "2",
                            through = "2",
                            next = "2",
                            changes = listOf(BudgetChange.Upsert(transaction(revision = "revision-2", amount = 1300L))),
                        )
                    } else {
                        changePage(generation, cursor, cursor, cursor)
                    }
                }

                firstDevice.synchronize(BUDGET, sendQueuedWrites = false)
                secondDevice.synchronize(BUDGET, sendQueuedWrites = false)

                assertEquals("revision-2", firstRepository.findRecord(BUDGET, "transaction", TXN)?.remoteRevision)
                assertEquals("revision-2", secondRepository.findRecord(BUDGET, "transaction", TXN)?.remoteRevision)
                assertEquals("2", firstRepository.readSyncState(BUDGET)?.cursor)
                assertEquals("2", secondRepository.readSyncState(BUDGET)?.cursor)
            } finally {
                firstDatabase.close()
                secondDatabase.close()
            }
        }

    @Test
    fun outboxClaimIsScopedToTheRequestedBudget() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                repository.applyFullSnapshot(BUDGET, GENERATION, "1", null, listOf(transaction()))
                repository.applyFullSnapshot("budget-2", GENERATION, "1", null, listOf(transaction().copy(budgetId = "budget-2")))
                val queued = updateRequest().copy(budgetId = "budget-2", operationId = "93cc8887-fdc8-4d80-af19-8354fbfac606")
                repository.enqueueWrite(queued)
                val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))

                val result = BudgetSyncCoordinator(repository, remote).synchronize(BUDGET)

                assertEquals(0, result.writesCommitted)
                assertEquals(0, remote.submitCount)
                assertEquals("queued", repository.readPendingWrites("budget-2").single().status)
            } finally {
                database.close()
            }
        }

    @Test
    fun statusOnlyCheckReconcilesOneUncertainWriteWithoutSendingQueuedWrites() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote =
                    FakeBudgetSyncRemote(
                        snapshot(records = listOf(transaction(), transaction("txn-2"))),
                    )
                val coordinator = BudgetSyncCoordinator(repository, remote)
                coordinator.synchronize(BUDGET, sendQueuedWrites = false)
                coordinator.synchronize(BUDGET, sendQueuedWrites = false)
                val changeFeedReadsBeforeCheck = remote.changeRequests.size

                val uncertainRequest = updateRequest()
                val queuedRequest =
                    updateRequest(
                        operationId = "queued-operation",
                        idempotencyKey = "queued-key",
                        recordId = "txn-2",
                    ).copy(createdAtEpochMs = 200L)
                repository.enqueueWrite(uncertainRequest)
                repository.enqueueWrite(queuedRequest)
                assertEquals(uncertainRequest.operationId, repository.claimNextWrite(BUDGET)?.operationId)
                repository.markWriteUncertain(uncertainRequest.operationId, "network-error")
                remote.operationHandler = { operationId -> operationResult(operationId, RemoteOperationStatus.COMMITTED) }
                remote.transactionHandler = { _, transactionId, _ -> remoteTransaction(transactionId, "revision-2", 1200L) }

                val result =
                    coordinator.synchronize(
                        BUDGET,
                        sendQueuedWrites = false,
                        reconcileUncertainOperationId = uncertainRequest.operationId,
                    )

                assertEquals(1, result.writesCommitted)
                assertEquals(1, remote.operationPolls)
                assertEquals(changeFeedReadsBeforeCheck + 1, remote.changeRequests.size)
                assertEquals(0, remote.submitCount)
                assertEquals("committed", database.outboxDao().find(uncertainRequest.operationId)?.status)
                assertEquals("queued", database.outboxDao().find(queuedRequest.operationId)?.status)
            } finally {
                database.close()
            }
        }

    @Test
    fun statusOnlyCheckReportsLookupFailureAndKeepsTheOperationUncertain() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))
                val coordinator = BudgetSyncCoordinator(repository, remote)
                coordinator.synchronize(BUDGET, sendQueuedWrites = false)
                val request = updateRequest()
                repository.enqueueWrite(request)
                repository.claimNextWrite(BUDGET)
                repository.markWriteUncertain(request.operationId, "network-error")
                remote.readOperationError = BudgetSyncRemoteException("http-503", 503)

                val result =
                    coordinator.synchronize(
                        BUDGET,
                        sendQueuedWrites = false,
                        reconcileUncertainOperationId = request.operationId,
                    )

                assertEquals(1, result.writesUncertain)
                assertEquals("http-503", result.operationStatusErrorCode)
                assertEquals("uncertain", database.outboxDao().find(request.operationId)?.status)
                assertEquals(1, remote.operationPolls)
                assertEquals(0, remote.submitCount)
            } finally {
                database.close()
            }
        }

    @Test
    fun generationResetReplacesRemoteBaselineAndKeepsQueuedDraft() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))
                val coordinator = BudgetSyncCoordinator(repository, remote)
                coordinator.synchronize(BUDGET, sendQueuedWrites = false)
                val request = updateRequest()
                repository.enqueueWrite(request)
                remote.changeHandler = { _, _, _, _, _ ->
                    changePage(
                        generation = GENERATION_2,
                        current = "5",
                        through = "5",
                        next = "5",
                        resetRequired = true,
                    )
                }
                remote.snapshotResponse =
                    snapshot(GENERATION_2, "5", listOf(transaction(generation = GENERATION_2, revision = "revision-2")))

                val result = coordinator.synchronize(BUDGET, sendQueuedWrites = false)

                assertTrue(result.fullSnapshotApplied)
                assertEquals(GENERATION_2, repository.readSyncState(BUDGET)?.generation)
                assertEquals("revision-2", repository.findRecord(BUDGET, "transaction", TXN)?.remoteRevision)
                assertEquals(request.optimisticJson, repository.findRecord(BUDGET, "transaction", TXN)?.localJson)
                assertEquals(request.operationId, repository.readPendingWrites(BUDGET).single().operationId)
            } finally {
                database.close()
            }
        }

    @Test
    fun ambiguousPostIsPolledAfterReconnectWithoutReposting() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))
                val coordinator = BudgetSyncCoordinator(repository, remote)
                coordinator.synchronize(BUDGET, sendQueuedWrites = false)
                val request = updateRequest()
                repository.enqueueWrite(request)
                remote.submitError = BudgetSyncRemoteException("network-error")
                remote.readOperationError = BudgetSyncRemoteException("http-404", 404)

                val disconnected = coordinator.synchronize(BUDGET)

                assertEquals(1, disconnected.writesUncertain)
                assertEquals("uncertain", repository.readPendingWrites(BUDGET).single().status)
                assertEquals(1, remote.submitCount)

                remote.submitError = null
                remote.readOperationError = null
                remote.operationHandler = { operationId ->
                    operationResult(operationId, RemoteOperationStatus.COMMITTED)
                }
                remote.transactionHandler = { _, transactionId, _ -> remoteTransaction(transactionId, "revision-2", 1200L) }

                val reconnected = coordinator.synchronize(BUDGET)

                assertEquals(1, reconnected.writesCommitted)
                assertEquals(1, remote.submitCount)
                assertEquals(2, remote.operationPolls)
                assertEquals("committed", database.outboxDao().find(request.operationId)?.status)
                assertEquals(1, database.outboxDao().find(request.operationId)?.attemptCount)
                assertNull(repository.findRecord(BUDGET, "transaction", TXN)?.localJson)
                assertEquals("revision-2", repository.findRecord(BUDGET, "transaction", TXN)?.remoteRevision)
            } finally {
                database.close()
            }
        }

    @Test
    fun uncertainWriteRequiresExplicitRetryAndReusesItsIdempotencyIdentity() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))
                val coordinator = BudgetSyncCoordinator(repository, remote)
                coordinator.synchronize(BUDGET, sendQueuedWrites = false)
                val request = updateRequest()
                repository.enqueueWrite(request)
                remote.submitError = BudgetSyncRemoteException("network-error")
                remote.readOperationError = BudgetSyncRemoteException("http-404", 404)

                val uncertain = coordinator.synchronize(BUDGET)
                assertEquals(1, uncertain.writesUncertain)
                assertEquals(1, remote.submitCount)

                val queued = repository.retryUncertainWrite(request.operationId)
                assertEquals("queued", queued.status)
                assertEquals(request.operationId, queued.operationId)
                assertEquals(request.idempotencyKey, queued.idempotencyKey)
                remote.submitError = null
                remote.readOperationError = null
                remote.submitHandler = { operation -> operationResult(operation.operationId, RemoteOperationStatus.COMMITTED) }
                remote.transactionHandler = { _, transactionId, _ -> remoteTransaction(transactionId, "revision-2", 1200L) }

                val retried = coordinator.synchronize(BUDGET)

                assertEquals(1, retried.writesCommitted)
                assertEquals(listOf(request.operationId, request.operationId), remote.submittedOperations.map { it.operationId })
                assertEquals(listOf(request.idempotencyKey, request.idempotencyKey), remote.submittedOperations.map { it.idempotencyKey })
                assertEquals(2, database.outboxDao().find(request.operationId)?.attemptCount)
            } finally {
                database.close()
            }
        }

    @Test
    fun interruptedSendIsRecoveredAndPolledAfterDatabaseReopen() =
        runBlocking {
            val file = File.createTempFile("companion-sync-", ".db")
            file.delete()
            val firstDatabase = persistentDatabase(file.absolutePath)
            var firstDatabaseClosed = false
            try {
                val firstRepository = LocalBudgetRepository(firstDatabase) { 100L }
                firstRepository.applyFullSnapshot(BUDGET, GENERATION, "1", null, listOf(transaction()))
                val request = updateRequest()
                firstRepository.enqueueWrite(request)
                firstRepository.claimNextWrite(BUDGET)
                firstDatabase.close()
                firstDatabaseClosed = true

                val reopenedDatabase = persistentDatabase(file.absolutePath)
                try {
                    val repository = LocalBudgetRepository(reopenedDatabase) { 200L }
                    val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))
                    remote.operationHandler = { operationId -> operationResult(operationId, RemoteOperationStatus.COMMITTED) }
                    remote.transactionHandler = { _, transactionId, _ -> remoteTransaction(transactionId, "revision-2", 1200L) }

                    val result = BudgetSyncCoordinator(repository, remote).synchronize(BUDGET)

                    assertEquals(1, result.writesCommitted)
                    assertEquals(1, remote.operationPolls)
                    assertEquals(0, remote.submitCount)
                    assertEquals("committed", reopenedDatabase.outboxDao().find(request.operationId)?.status)
                    assertEquals(1, reopenedDatabase.outboxDao().find(request.operationId)?.attemptCount)
                    assertEquals("revision-2", repository.findRecord(BUDGET, "transaction", TXN)?.remoteRevision)
                } finally {
                    reopenedDatabase.close()
                }
            } finally {
                if (!firstDatabaseClosed) {
                    firstDatabase.close()
                }
                File(file.absolutePath + "-wal").delete()
                File(file.absolutePath + "-shm").delete()
                file.delete()
            }
        }

    @Test
    fun separateCoordinatorWaitsInsteadOfRecoveringAnotherCoordinatorsActiveSend() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))
                val foregroundCoordinator = BudgetSyncCoordinator(repository, remote)
                val backgroundCoordinator = BudgetSyncCoordinator(repository, remote)
                foregroundCoordinator.synchronize(BUDGET, sendQueuedWrites = false)
                val request = updateRequest()
                repository.enqueueWrite(request)

                val submitStarted = CompletableDeferred<Unit>()
                val finishSubmit = CompletableDeferred<Unit>()
                remote.submitHandler = { operation ->
                    submitStarted.complete(Unit)
                    finishSubmit.await()
                    operationResult(operation.operationId, RemoteOperationStatus.COMMITTED)
                }
                remote.transactionHandler = { _, transactionId, _ -> remoteTransaction(transactionId, "revision-2", 1200L) }
                val foreground = async { foregroundCoordinator.synchronize(BUDGET) }
                submitStarted.await()

                val background = async { backgroundCoordinator.synchronize(BUDGET) }
                yield()
                assertEquals("sending", repository.readPendingWrites(BUDGET).single().status)

                finishSubmit.complete(Unit)
                assertEquals(1, foreground.await().writesCommitted)
                assertEquals(0, background.await().writesCommitted)
                assertEquals("committed", database.outboxDao().find(request.operationId)?.status)
            } finally {
                database.close()
            }
        }

    @Test
    fun conflictKeepsLocalDraftAndStoresLatestRemoteSnapshot() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))
                val coordinator = BudgetSyncCoordinator(repository, remote)
                coordinator.synchronize(BUDGET, sendQueuedWrites = false)
                val request = updateRequest()
                repository.enqueueWrite(request)
                remote.submitHandler =
                    { operation -> operationResult(operation.operationId, RemoteOperationStatus.CONFLICT, "field-conflict") }
                remote.transactionHandler = { _, transactionId, _ -> remoteTransaction(transactionId, "revision-2", 1500L) }

                val result = coordinator.synchronize(BUDGET)

                assertEquals(1, result.writesConflicted)
                assertEquals("conflict", database.outboxDao().find(request.operationId)?.status)
                assertEquals(request.optimisticJson, repository.findRecord(BUDGET, "transaction", TXN)?.localJson)
                assertEquals("revision-2", repository.findRecord(BUDGET, "transaction", TXN)?.remoteRevision)
            } finally {
                database.close()
            }
        }

    @Test
    fun duplicateLocalCreateUsesTheDurableCreateEndpointOnce() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = emptyList()))
                val coordinator = BudgetSyncCoordinator(repository, remote)
                coordinator.synchronize(BUDGET, sendQueuedWrites = false)
                val request =
                    updateRequest(
                        operationId = CREATE_OPERATION,
                        idempotencyKey = "create-key",
                        recordId = "tandem:8c88495a-65d9-4531-8392-93bff2c65374",
                        action = LocalWriteAction.CREATE,
                        baseRevision = null,
                        baseSnapshot = null,
                        patch =
                            "{\"account\":\"checking\",\"amount\":1200,\"date\":\"2026-10-01\",\"payee\":null,\"category\":null,\"notes\":\"\",\"cleared\":false}",
                        optimistic =
                            "{\"id\":\"tandem:8c88495a-65d9-4531-8392-93bff2c65374\",\"amount\":1200}",
                    )
                val first = repository.enqueueWrite(request)
                val duplicate = repository.enqueueWrite(request)
                assertFalse(first.replayed)
                assertTrue(duplicate.replayed)
                remote.submitHandler = { operation ->
                    RemoteOperationResult(
                        operation.operationId,
                        BUDGET,
                        operation.recordId,
                        RemoteOperationStatus.COMMITTED,
                        null,
                    )
                }
                remote.transactionHandler = { _, transactionId, _ ->
                    remoteTransaction(transactionId, "revision-2", 1200L)
                }

                val result = coordinator.synchronize(BUDGET)

                assertEquals(1, result.writesCommitted)
                assertEquals(1, remote.submitCount)
                assertEquals("committed", database.outboxDao().find(CREATE_OPERATION)?.status)
                assertEquals(
                    "tandem:8c88495a-65d9-4531-8392-93bff2c65374",
                    repository.findRecord(BUDGET, "transaction", request.recordId)?.recordId,
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun malformedSnapshotAndRemoteFailureMarkSyncFailedWithSafeCodes() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = emptyList()))
                val coordinator = BudgetSyncCoordinator(repository, remote)
                remote.snapshotResponse = remote.snapshotResponse.copy(budgetId = "other-budget")

                val malformed = runCatching { coordinator.synchronize(BUDGET, sendQueuedWrites = false) }.exceptionOrNull()

                assertTrue(malformed is BudgetSyncProtocolException)
                assertEquals("invalid-sync-response", repository.readSyncState(BUDGET)?.lastErrorCode)

                remote.snapshotHandler = { _, _ -> throw IllegalStateException("private service details") }
                val unavailable = runCatching { coordinator.synchronize(BUDGET, sendQueuedWrites = false) }.exceptionOrNull()

                assertTrue(unavailable is IllegalStateException)
                assertEquals("sync-request-failed", repository.readSyncState(BUDGET)?.lastErrorCode)
            } finally {
                database.close()
            }
        }

    @Test
    fun snapshotsRetryStaleCheckpointsAndStopAfterThreeAttempts() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = emptyList()))
                var attempts = 0
                remote.snapshotHandler = { _, _ ->
                    attempts += 1
                    val expected = repository.readSyncCheckpoint(BUDGET)
                    val localCursor = ((expected?.cursor?.toLongOrNull() ?: 0L) + 1L).toString()
                    repository.applyFullSnapshot(BUDGET, GENERATION, localCursor, expected, listOf(transaction()))
                    snapshot(GENERATION_2, (localCursor.toLong() + 1L).toString(), listOf(transaction(generation = GENERATION_2)))
                }

                val error =
                    runCatching {
                        BudgetSyncCoordinator(repository, remote).synchronize(BUDGET, sendQueuedWrites = false)
                    }.exceptionOrNull()

                assertTrue(error is BudgetSyncProtocolException)
                assertEquals(3, attempts)
                assertEquals("invalid-sync-response", repository.readSyncState(BUDGET)?.lastErrorCode)
            } finally {
                database.close()
            }
        }

    @Test
    fun changeFeedRejectsBadPagesAndFallsBackWhenTheLocalCursorMovesDuringFetch() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))
                BudgetSyncCoordinator(repository, remote).synchronize(BUDGET, sendQueuedWrites = false)

                suspend fun resetCursor() {
                    repository.applyFullSnapshot(
                        BUDGET,
                        GENERATION,
                        "1",
                        repository.readSyncCheckpoint(BUDGET),
                        listOf(transaction()),
                    )
                }

                suspend fun expectRejectedPage(handler: suspend (String, String, String, String?, String?) -> RemoteBudgetChangePage) {
                    remote.changeHandler = handler
                    val error =
                        runCatching {
                            BudgetSyncCoordinator(repository, remote).synchronize(BUDGET, sendQueuedWrites = false)
                        }.exceptionOrNull()
                    assertTrue(error is BudgetSyncProtocolException)
                    resetCursor()
                }

                expectRejectedPage { _, generation, cursor, _, _ ->
                    changePage(generation, cursor, "2", "2").copy(budgetId = "other-budget")
                }
                expectRejectedPage { _, generation, cursor, _, _ -> changePage(generation, "x", "2", "2") }
                expectRejectedPage { _, generation, _, _, _ -> changePage(generation, "2", "2", "0") }
                expectRejectedPage { _, generation, _, _, _ -> changePage(generation, "0", "2", "2") }
                expectRejectedPage { _, generation, cursor, _, _ ->
                    changePage(generation, "2", "2", cursor, hasMore = true)
                }
                expectRejectedPage { _, generation, _, _, _ -> changePage(generation, "2", "2", "1") }

                remote.changeHandler = { _, generation, cursor, through, _ ->
                    if (through == null) {
                        changePage(generation, "2", "2", "2", hasMore = true)
                    } else {
                        changePage(generation, "3", "3", "3")
                    }
                }
                val changedHighWater =
                    runCatching {
                        BudgetSyncCoordinator(repository, remote).synchronize(BUDGET, sendQueuedWrites = false)
                    }.exceptionOrNull()
                assertTrue(changedHighWater is BudgetSyncProtocolException)
                resetCursor()

                remote.changeHandler = { _, generation, _, _, _ -> changePage(generation, "10", "10", "10") }
                val longCursor = BudgetSyncCoordinator(repository, remote).synchronize(BUDGET, sendQueuedWrites = false)
                assertEquals("10", repository.readSyncState(BUDGET)?.cursor)
                assertEquals(1, longCursor.changePagesApplied)

                remote.changeHandler = { _, generation, cursor, _, _ ->
                    repository.applyChangePage(
                        BudgetChangePage(BUDGET, generation, cursor, "11", emptyList()),
                    )
                    changePage(generation, "11", "11", "11")
                }
                remote.snapshotResponse = snapshot(GENERATION_2, "12", listOf(transaction(generation = GENERATION_2)))

                val recovered = BudgetSyncCoordinator(repository, remote).synchronize(BUDGET, sendQueuedWrites = false)

                assertTrue(recovered.fullSnapshotApplied)
                assertEquals(GENERATION_2, repository.readSyncState(BUDGET)?.generation)
            } finally {
                database.close()
            }
        }

    @Test
    fun pendingPostIsPolledAndConflictWithoutRemoteRecordKeepsTheDraft() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))
                val coordinator = BudgetSyncCoordinator(repository, remote)
                coordinator.synchronize(BUDGET, sendQueuedWrites = false)
                val request = updateRequest()
                repository.enqueueWrite(request)
                remote.submitHandler = { operation -> operationResult(operation.operationId, RemoteOperationStatus.QUEUED) }
                remote.operationHandler = { operationId ->
                    operationResult(operationId, RemoteOperationStatus.APPLYING, "unsafe error code")
                }

                val pending = coordinator.synchronize(BUDGET)

                assertEquals(1, pending.writesUncertain)
                assertEquals("remote-error", repository.readPendingWrites(BUDGET).single().lastErrorCode)
                assertEquals(1, remote.operationPolls)

                remote.operationHandler = { operationId -> operationResult(operationId, RemoteOperationStatus.APPLYING) }
                val stillPending = coordinator.synchronize(BUDGET)
                assertEquals(1, stillPending.writesUncertain)
                assertEquals("remote-operation-pending", repository.readPendingWrites(BUDGET).single().lastErrorCode)

                remote.operationHandler = { operationId -> operationResult(operationId, RemoteOperationStatus.CONFLICT) }
                remote.transactionHandler = { _, _, _ -> null }

                val conflict = coordinator.synchronize(BUDGET)

                assertEquals(1, conflict.writesConflicted)
                assertEquals("remote-conflict", repository.readPendingWrites(BUDGET).single().lastErrorCode)
                assertEquals(request.optimisticJson, repository.findRecord(BUDGET, "transaction", TXN)?.localJson)
            } finally {
                database.close()
            }
        }

    @Test
    fun rejectedWriteAndCommittedDeleteReachTheirTerminalStates() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))
                val coordinator = BudgetSyncCoordinator(repository, remote)
                coordinator.synchronize(BUDGET, sendQueuedWrites = false)
                val update = updateRequest()
                repository.enqueueWrite(update)
                remote.submitError = BudgetSyncRemoteException("bad-request", 422)

                val rejected = coordinator.synchronize(BUDGET)

                assertEquals(1, rejected.writesRejected)
                assertEquals("bad-request", repository.readPendingWrites(BUDGET).single().lastErrorCode)

                val delete =
                    updateRequest(
                        operationId = "b35a8f22-5b97-4d4c-a235-f9219bdb3a10",
                        idempotencyKey = "delete-key",
                        action = LocalWriteAction.DELETE,
                        patch = "{}",
                        optimistic = null,
                    )
                repository.enqueueWrite(delete)
                remote.submitError = null
                remote.submitHandler = { operation -> operationResult(operation.operationId, RemoteOperationStatus.COMMITTED) }

                val committed = coordinator.synchronize(BUDGET)

                assertEquals(1, committed.writesCommitted)
                assertEquals("committed", database.outboxDao().find(delete.operationId)?.status)
                assertTrue(repository.findRecord(BUDGET, "transaction", TXN)?.remoteDeleted == true)
            } finally {
                database.close()
            }
        }

    @Test
    fun authenticationAndScopeFailuresKeepTheDraftQueuedForRetry() =
        runBlocking {
            listOf(401, 403, 404).forEach { status ->
                val database = newDatabase()
                try {
                    val repository = LocalBudgetRepository(database) { 100L }
                    val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))
                    val coordinator = BudgetSyncCoordinator(repository, remote)
                    coordinator.synchronize(BUDGET, sendQueuedWrites = false)
                    val request = updateRequest()
                    repository.enqueueWrite(request)
                    remote.submitError = BudgetSyncRemoteException("http-$status", status)

                    val error = runCatching { coordinator.synchronize(BUDGET) }.exceptionOrNull()
                    val queued = repository.readPendingWrites(BUDGET).single()

                    assertTrue(error is BudgetSyncRemoteException)
                    assertEquals("http-$status", queued.lastErrorCode)
                    assertEquals("queued", queued.status)
                    assertEquals(1, queued.attemptCount)
                    assertEquals(request.optimisticJson, repository.findRecord(BUDGET, "transaction", TXN)?.localJson)
                    assertEquals(BudgetSyncStatus.FAILED.name, repository.readSyncState(BUDGET)?.status)
                    assertEquals(0, remote.operationPolls)

                    remote.submitError = null
                    remote.submitHandler = { operation -> operationResult(operation.operationId, RemoteOperationStatus.COMMITTED) }
                    remote.transactionHandler = { _, transactionId, _ -> remoteTransaction(transactionId, "revision-2", 1200L) }
                    val retried = coordinator.synchronize(BUDGET)

                    assertEquals(1, retried.writesCommitted)
                    assertEquals(2, remote.submitCount)
                    assertEquals("committed", database.outboxDao().find(request.operationId)?.status)
                    assertNull(repository.findRecord(BUDGET, "transaction", TXN)?.localJson)
                } finally {
                    database.close()
                }
            }
        }

    @Test
    fun mismatchedSubmitAndMissingCommittedReadbackAreResolvedByPolling() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))
                val coordinator = BudgetSyncCoordinator(repository, remote)
                coordinator.synchronize(BUDGET, sendQueuedWrites = false)
                val first = updateRequest()
                repository.enqueueWrite(first)
                remote.submitHandler = { operation ->
                    operationResult("different-operation", RemoteOperationStatus.COMMITTED)
                }
                remote.operationHandler = { _ -> operationResult("different-operation", RemoteOperationStatus.COMMITTED) }
                remote.transactionHandler = { _, transactionId, _ -> remoteTransaction(transactionId, "revision-2", 1200L) }

                val uncertain = coordinator.synchronize(BUDGET)

                assertEquals(1, uncertain.writesUncertain)
                assertEquals("uncertain", database.outboxDao().find(first.operationId)?.status)

                remote.operationHandler = { operationId -> operationResult(operationId, RemoteOperationStatus.COMMITTED) }
                val recovered = coordinator.synchronize(BUDGET)

                assertEquals(1, recovered.writesCommitted)
                assertEquals(2, remote.operationPolls)

                val second =
                    updateRequest(
                        operationId = "7642c03b-008f-42e6-bd0d-f9b4973d92ba",
                        idempotencyKey = "second-key",
                        baseRevision = "revision-2",
                    )
                repository.enqueueWrite(second)
                remote.submitHandler = { operation -> operationResult(operation.operationId, RemoteOperationStatus.COMMITTED) }
                remote.transactionHandler = { _, _, _ -> null }
                remote.operationHandler = { operationId -> operationResult(operationId, RemoteOperationStatus.REJECTED) }

                val rejected = coordinator.synchronize(BUDGET)

                assertEquals(1, rejected.writesRejected)
                assertEquals("remote-rejected", database.outboxDao().find(second.operationId)?.lastErrorCode)
            } finally {
                database.close()
            }
        }

    @Test
    fun cancellationDuringSubmitAndPollLeavesWritesUncertain() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))
                val coordinator = BudgetSyncCoordinator(repository, remote)
                coordinator.synchronize(BUDGET, sendQueuedWrites = false)
                val request = updateRequest()
                repository.enqueueWrite(request)
                remote.submitError = CancellationException("cancelled")

                val duringSubmit = runCatching { coordinator.synchronize(BUDGET) }.exceptionOrNull()

                assertTrue(duringSubmit is CancellationException)
                assertEquals("uncertain", repository.readPendingWrites(BUDGET).single().status)
                assertEquals("sync-cancelled", repository.readSyncState(BUDGET)?.lastErrorCode)

                val secondDatabase = newDatabase()
                try {
                    val secondRepository = LocalBudgetRepository(secondDatabase) { 100L }
                    val secondRemote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))
                    val secondCoordinator = BudgetSyncCoordinator(secondRepository, secondRemote)
                    secondCoordinator.synchronize(BUDGET, sendQueuedWrites = false)
                    secondRepository.enqueueWrite(request)
                    secondRemote.submitError = BudgetSyncRemoteException("network-error")
                    secondRemote.readOperationError = CancellationException("cancelled while polling")

                    val duringPoll = runCatching { secondCoordinator.synchronize(BUDGET) }.exceptionOrNull()

                    assertTrue(duringPoll is CancellationException)
                    assertEquals("uncertain", secondRepository.readPendingWrites(BUDGET).single().status)
                    assertEquals("sync-cancelled-during-poll", secondRepository.readPendingWrites(BUDGET).single().lastErrorCode)

                    secondRemote.readOperationError = BudgetSyncRemoteException("temporarily-unavailable", 503)
                    val failedRecovery = secondCoordinator.synchronize(BUDGET)
                    assertEquals(1, failedRecovery.writesUncertain)

                    secondRemote.readOperationError = CancellationException("cancelled during recovery")
                    val duringRecovery = runCatching { secondCoordinator.synchronize(BUDGET) }.exceptionOrNull()
                    assertTrue(duringRecovery is CancellationException)
                    assertEquals("uncertain", secondRepository.readPendingWrites(BUDGET).single().status)
                } finally {
                    secondDatabase.close()
                }
            } finally {
                database.close()
            }
        }

    @Test
    fun realJobCancellationPersistsUncertaintyAndNextSyncRecoversIt() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))
                val coordinator = BudgetSyncCoordinator(repository, remote)
                coordinator.synchronize(BUDGET, sendQueuedWrites = false)
                val request = updateRequest()
                repository.enqueueWrite(request)
                val submitStarted = CompletableDeferred<Unit>()
                remote.submitHandler = {
                    submitStarted.complete(Unit)
                    awaitCancellation()
                }

                val job = launch { coordinator.synchronize(BUDGET) }
                submitStarted.await()
                job.cancelAndJoin()

                val interrupted = repository.readPendingWrites(BUDGET).single()
                assertEquals("uncertain", interrupted.status)
                assertEquals("sync-cancelled-during-send", interrupted.lastErrorCode)
                assertEquals("sync-cancelled", repository.readSyncState(BUDGET)?.lastErrorCode)

                remote.operationHandler = { operationId -> operationResult(operationId, RemoteOperationStatus.COMMITTED) }
                remote.transactionHandler = { _, transactionId, _ -> remoteTransaction(transactionId, "revision-2", 1200L) }
                val recovered = coordinator.synchronize(BUDGET)

                assertEquals(1, recovered.writesCommitted)
                assertEquals(1, remote.submitCount)
                assertEquals(1, remote.operationPolls)
                assertEquals("committed", database.outboxDao().find(request.operationId)?.status)
            } finally {
                database.close()
            }
        }

    @Test
    fun cancellationDuringCommittedReadbackLeavesTheWriteUncertain() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))
                val coordinator = BudgetSyncCoordinator(repository, remote)
                coordinator.synchronize(BUDGET, sendQueuedWrites = false)
                val request = updateRequest()
                repository.enqueueWrite(request)
                remote.submitHandler = { operation -> operationResult(operation.operationId, RemoteOperationStatus.COMMITTED) }
                remote.transactionHandler = { _, _, _ -> throw CancellationException("cancelled during readback") }

                val error = runCatching { coordinator.synchronize(BUDGET) }.exceptionOrNull()

                assertTrue(error is CancellationException)
                assertEquals("uncertain", repository.readPendingWrites(BUDGET).single().status)
                assertEquals("sync-cancelled-during-resolution", repository.readPendingWrites(BUDGET).single().lastErrorCode)
            } finally {
                database.close()
            }
        }

    @Test
    fun cancellationDuringPolledReadbackLeavesTheWriteUncertain() =
        runBlocking {
            val database = newDatabase()
            try {
                val repository = LocalBudgetRepository(database) { 100L }
                val remote = FakeBudgetSyncRemote(snapshot(records = listOf(transaction())))
                val coordinator = BudgetSyncCoordinator(repository, remote)
                coordinator.synchronize(BUDGET, sendQueuedWrites = false)
                val request = updateRequest()
                repository.enqueueWrite(request)
                remote.submitError = BudgetSyncRemoteException("network-error")
                remote.operationHandler = { operationId -> operationResult(operationId, RemoteOperationStatus.COMMITTED) }
                remote.transactionHandler = { _, _, _ -> throw CancellationException("cancelled during polled readback") }

                val error = runCatching { coordinator.synchronize(BUDGET) }.exceptionOrNull()

                assertTrue(error is CancellationException)
                assertEquals("uncertain", repository.readPendingWrites(BUDGET).single().status)
                assertEquals("sync-cancelled-during-resolution", repository.readPendingWrites(BUDGET).single().lastErrorCode)
            } finally {
                database.close()
            }
        }

    private fun newDatabase(): TandemDatabase =
        openTandemDatabase(
            Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO),
        )

    private fun persistentDatabase(path: String): TandemDatabase =
        openTandemDatabase(
            Room.databaseBuilder<TandemDatabase>(name = path).setQueryCoroutineContext(Dispatchers.IO),
        )

    private fun snapshot(
        generation: String = GENERATION,
        cursor: String = "1",
        records: List<RemoteBudgetRecord>,
    ) = RemoteBudgetSnapshot(BUDGET, generation, cursor, records)

    private fun changePage(
        generation: String,
        current: String,
        through: String,
        next: String,
        hasMore: Boolean = false,
        resetRequired: Boolean = false,
        changes: List<BudgetChange> = emptyList(),
    ) = RemoteBudgetChangePage(BUDGET, generation, current, through, next, hasMore, resetRequired, changes)

    private fun transaction(
        recordId: String = TXN,
        generation: String = GENERATION,
        revision: String = "revision-1",
        amount: Long = 1000L,
    ) = RemoteBudgetRecord(
        budgetId = BUDGET,
        recordType = "transaction",
        recordId = recordId,
        revision = revision,
        bodyJson = "{\"id\":\"$recordId\",\"amount\":$amount}",
        generation = generation,
        recordDate = "2026-10-01",
        accountId = "checking",
        categoryId = "food",
        payeeId = "market",
        amountMinor = amount,
    )

    private fun updateRequest(
        operationId: String = WRITE_OPERATION,
        idempotencyKey: String = "write-key",
        recordId: String = TXN,
        action: LocalWriteAction = LocalWriteAction.UPDATE,
        baseRevision: String? = "revision-1",
        baseSnapshot: String? = "{\"id\":\"$recordId\",\"amount\":1000}",
        patch: String = "{\"amount\":1200}",
        optimistic: String? = "{\"id\":\"$recordId\",\"amount\":1200}",
    ) = LocalWriteRequest(
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
        createdAtEpochMs = 100L,
        optimisticProjection =
            if (action == LocalWriteAction.DELETE) {
                null
            } else {
                LocalRecordProjection("2026-10-01", "checking", "food", "market", 1200L, null)
            },
    )

    private fun remoteTransaction(
        transactionId: String,
        revision: String,
        amount: Long,
    ) = RemoteTransactionRecord(
        transactionId,
        revision,
        "{\"id\":\"$transactionId\",\"amount\":$amount}",
        LocalRecordProjection("2026-10-01", "checking", "food", "market", amount, null),
    )

    private fun operationResult(
        operationId: String,
        status: RemoteOperationStatus,
        errorCode: String? = null,
    ) = RemoteOperationResult(operationId, BUDGET, TXN, status, errorCode)

    private inner class FakeBudgetSyncRemote(
        var snapshotResponse: RemoteBudgetSnapshot,
    ) : BudgetSyncRemote {
        val changeRequests = mutableListOf<ChangeRequest>()
        val submittedOperations = mutableListOf<app.tandemforactual.data.database.OutboxOperationEntity>()
        var submitCount = 0
        var operationPolls = 0
        var submitError: Exception? = null
        var readOperationError: Exception? = null
        var snapshotHandler: suspend (String, String?) -> RemoteBudgetSnapshot = { _, _ -> snapshotResponse }
        var snapshotPageHandler: (suspend (String, String?, String?, String?, String?, Int, String?) -> RemoteBudgetSnapshotPage)? = null
        var changeHandler: suspend (String, String, String, String?, String?) -> RemoteBudgetChangePage =
            { budgetId, generation, cursor, _, _ ->
                RemoteBudgetChangePage(budgetId, generation, cursor, cursor, cursor, false, false, emptyList())
            }
        var submitHandler: suspend (app.tandemforactual.data.database.OutboxOperationEntity) -> RemoteOperationResult =
            { operation -> operationResult(operation.operationId, RemoteOperationStatus.COMMITTED) }
        var operationHandler: suspend (String) -> RemoteOperationResult =
            { operationId -> operationResult(operationId, RemoteOperationStatus.UNCERTAIN) }
        var transactionHandler: suspend (String, String, String?) -> RemoteTransactionRecord? =
            { _, _, _ -> null }

        override suspend fun readSnapshot(
            budgetId: String,
            encryptionPassword: String?,
        ) = snapshotHandler(budgetId, encryptionPassword)

        override suspend fun readSnapshotPage(
            budgetId: String,
            generation: String?,
            cursor: String?,
            afterType: String?,
            afterId: String?,
            limit: Int,
            encryptionPassword: String?,
        ): RemoteBudgetSnapshotPage =
            snapshotPageHandler?.invoke(budgetId, generation, cursor, afterType, afterId, limit, encryptionPassword)
                ?: super<BudgetSyncRemote>.readSnapshotPage(
                    budgetId,
                    generation,
                    cursor,
                    afterType,
                    afterId,
                    limit,
                    encryptionPassword,
                )

        override suspend fun readChanges(
            budgetId: String,
            generation: String,
            cursor: String,
            throughCursor: String?,
            encryptionPassword: String?,
        ): RemoteBudgetChangePage {
            changeRequests += ChangeRequest(cursor, throughCursor, encryptionPassword)
            return changeHandler(budgetId, generation, cursor, throughCursor, encryptionPassword)
        }

        override suspend fun submitOperation(
            operation: app.tandemforactual.data.database.OutboxOperationEntity,
            encryptionPassword: String?,
        ): RemoteOperationResult {
            submitCount += 1
            submittedOperations += operation
            submitError?.let { throw it }
            return submitHandler(operation)
        }

        override suspend fun readOperation(operationId: String): RemoteOperationResult {
            operationPolls += 1
            readOperationError?.let { throw it }
            return operationHandler(operationId)
        }

        override suspend fun readTransaction(
            budgetId: String,
            transactionId: String,
            encryptionPassword: String?,
        ): RemoteTransactionRecord? = transactionHandler(budgetId, transactionId, encryptionPassword)
    }

    private companion object {
        const val BUDGET = "budget-1"
        const val TXN = "txn-1"
        const val GENERATION = "generation-1"
        const val GENERATION_2 = "generation-2"
        const val WRITE_OPERATION = "6461dd06-cfe0-4854-9ced-16b7011fb87d"
        const val CREATE_OPERATION = "d56bea93-1cb3-4f3e-9938-54b4c15904d8"
    }
}
