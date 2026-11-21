package app.tandemforactual.api.client

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.actual.ActualDatabase
import app.tandemforactual.data.actual.ActualTimestamp
import app.tandemforactual.data.actual.MerkleTrie
import app.tandemforactual.data.actual.OperationStatus
import app.tandemforactual.data.actual.SyncProtocol
import app.tandemforactual.data.actual.SyncResponse
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.closeDatabase
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.store.BudgetSyncProtocolException
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalRecordProjection
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import app.tandemforactual.data.store.RemoteBudgetRecord
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs only with ACTUAL_TEST_URL set: writes through a real Actual server, then dumps what Tandem reads back. */
class ActualServerRoundTripTest {
    private val url = System.getenv("ACTUAL_TEST_URL")
    private val budget = System.getenv("ACTUAL_TEST_BUDGET") ?: "Parity envelope"
    private val out = System.getenv("ACTUAL_TEST_OUT")

    private suspend fun download(
        client: ActualServerClient,
        fileId: String,
        path: File,
    ): ActualDirectBudget =
        ActualDirectBudget.download(client, fileId, System::currentTimeMillis, { UUID.randomUUID().toString() }) {
            path.writeBytes(it)
            openDatabase(path)
        }

    private fun openDatabase(path: File) = ActualDatabase(BundledSQLiteDriver().open(path.path))

    @Test
    fun writesRoundTripThroughProtocolFixture(): Unit =
        runBlocking {
            val fixture = File("../data/src/jvmTest/resources/actual/parity-envelope")
            val archive = fixture.resolve("budget.zip").readBytes()
            val initial = SyncProtocol.decodeResponse(fixture.resolve("sync-response.bin").readBytes())
            val history = initial.messages.associateByTo(linkedMapOf()) { it.timestamp }
            var merkle = MerkleTrie.fromJson(initial.merkle)
            val engine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/account/login" -> respond("""{"data":{"token":"session"}}""")
                        "/sync/list-user-files" ->
                            respond("""{"data":[{"fileId":"fixture","groupId":"group","name":"Parity envelope"}]}""")
                        "/sync/get-user-file-info" -> respond("""{"data":{"groupId":"group"}}""")
                        "/sync/download-user-file" -> respond(archive)
                        "/sync/sync" -> {
                            val sync = SyncProtocol.decodeRequest((request.body as OutgoingContent.ByteArrayContent).bytes())
                            for (message in sync.messages) {
                                if (history.putIfAbsent(message.timestamp, message) == null) {
                                    merkle = merkle.insert(checkNotNull(ActualTimestamp.parse(message.timestamp)))
                                }
                            }
                            val sent = sync.messages.mapTo(mutableSetOf()) { it.timestamp }
                            val received = history.values.filter { it.timestamp > sync.since && it.timestamp !in sent }
                            respond(SyncProtocol.encode(SyncResponse(received, merkle.prune().toJson().toString())))
                        }
                        else -> error("Unexpected HTTP request to ${request.url.encodedPath}")
                    }
                }
            val directory = createTempDirectory("actual-roundtrip-fixture").toFile()
            try {
                HttpClient(engine).use { http ->
                    verifyRoundTrip(ActualServerClient(http, "https://budget.example"), directory, "Parity envelope", null)
                }
            } finally {
                directory.deleteRecursively()
            }
        }

    @Test
    fun writesRoundTripThroughActual(): Unit =
        runBlocking {
            if (url == null) return@runBlocking
            val directory = createTempDirectory("actual-roundtrip").toFile()
            try {
                HttpClient(Java).use { client ->
                    val password = System.getenv("ACTUAL_TEST_PASSWORD") ?: "demo-password"
                    val session = ActualDirectSession.signInWithPassword(client, url, password, allowInsecurePrivateNetwork = true)
                    val groupId = session.listBudgets().single { it.name == budget }.budgetId
                    verifyFirstRun(session, groupId, directory.resolve("first-run.sqlite"))
                    verifyRoundTrip(ActualServerClient(client, url), directory)
                }
            } finally {
                directory.deleteRecursively()
            }
        }

    private suspend fun verifyRoundTrip(
        client: ActualServerClient,
        directory: File,
        budgetName: String = budget,
        output: String? = out,
    ) {
        client.signIn(System.getenv("ACTUAL_TEST_PASSWORD") ?: "demo-password")
        val fileId = client.listFiles().first { it.name == budgetName && !it.deleted }.fileId
        val path = directory.resolve("initial.sqlite")
        val initial = download(client, fileId, path)
        val conflictId = UUID.randomUUID().toString()
        val (shop, transfer, toDelete) =
            try {
                initial.sync()

                val (checking, savingsPayee, market, groceries) =
                    openDatabase(path).let { db ->
                        try {
                            listOf(
                                db.first("SELECT id FROM accounts WHERE name = 'Checking'")!!["id"] as String,
                                db.first(
                                    "SELECT p.id FROM payees p JOIN accounts a ON a.id = p.transfer_acct WHERE a.name = 'Savings'",
                                )!!["id"] as String,
                                db.first("SELECT id FROM payees WHERE name = 'Market' AND tombstone = 0")!!["id"] as String,
                                db.first("SELECT id FROM categories WHERE name = 'Groceries'")!!["id"] as String,
                            )
                        } finally {
                            db.close()
                        }
                    }
                val today =
                    java.time.LocalDate
                        .now()
                        .toString()

                suspend fun create(
                    payee: String,
                    amount: Long,
                    category: String?,
                ): String {
                    val id = "tandem:${UUID.randomUUID()}"
                    val patch =
                        buildJsonObject {
                            put("account", checking)
                            put("amount", amount)
                            put("date", today)
                            put("payee", payee)
                            put("category", category)
                            put("notes", "round trip")
                            put("cleared", false)
                        }
                    assertEquals(OperationStatus.COMMITTED, initial.submit(UUID.randomUUID().toString(), id, "create", patch, null).status)
                    return id
                }
                val shop = create(market, -1234, groceries)
                val transfer = create(savingsPayee, -5000, null)
                val toDelete = create(market, -1, groceries)

                suspend fun base(id: String) = initial.transaction(id)!!
                assertEquals(
                    OperationStatus.COMMITTED,
                    initial
                        .submit(
                            UUID.randomUUID().toString(),
                            shop,
                            "update",
                            buildJsonObject {
                                put("amount", -4321)
                                put("cleared", true)
                            },
                            base(shop),
                        ).status,
                )
                val replacement = download(client, fileId, directory.resolve("replacement.sqlite"))
                try {
                    verifyDirectOutbox(initial, replacement, transfer, shop, today)
                } finally {
                    replacement.close()
                }
                assertEquals(
                    OperationStatus.COMMITTED,
                    initial.submit(UUID.randomUUID().toString(), toDelete, "delete", JsonObject(emptyMap()), base(toDelete)).status,
                )
                val stale = base(shop)
                initial.submit(UUID.randomUUID().toString(), shop, "update", buildJsonObject { put("notes", "first") }, stale)
                val conflict = initial.submit(conflictId, shop, "update", buildJsonObject { put("notes", "second") }, stale)
                assertEquals(OperationStatus.CONFLICT, conflict.status)
                assertEquals(listOf("notes"), conflict.conflictingFields)
                listOf(shop, transfer, toDelete)
            } finally {
                initial.close()
            }
        assertFailsWith<IllegalArgumentException> {
            ActualDirectBudget.reopen(
                openDatabase(path),
                client,
                fileId,
                "another-group",
                System::currentTimeMillis,
            ) { UUID.randomUUID().toString() }
        }
        val reopened =
            ActualDirectBudget.reopen(openDatabase(path), client, fileId, initial.groupId, System::currentTimeMillis) {
                UUID.randomUUID().toString()
            }
        try {
            val retried =
                reopened.submit(
                    conflictId,
                    shop,
                    "update",
                    buildJsonObject { put("notes", "retried") },
                    reopened.transaction(shop),
                )
            assertEquals(OperationStatus.CONFLICT, retried.status)
            assertEquals(listOf("notes"), retried.conflictingFields)
            val projection =
                reopened.refreshProjection(
                    java.time.LocalDate
                        .now()
                        .toString(),
                )
            assertEquals(reopened.groupId, projection.budgetId)
            assertEquals(initial.cacheGeneration, projection.generation)
            assertEquals(-7000L, projection.records.single { it.recordType == "transaction" && it.recordId == transfer }.amountMinor)
        } finally {
            reopened.close()
        }

        val fresh = download(client, fileId, directory.resolve("fresh.sqlite"))
        try {
            fresh.preview(
                java.time.LocalDate
                    .now()
                    .toString(),
            )
            verifyDownload(fresh, shop, transfer, toDelete, output)
        } finally {
            fresh.close()
        }
    }

    private suspend fun verifyFirstRun(
        session: ActualDirectSession,
        groupId: String,
        path: File,
    ) {
        val database = openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
        try {
            val repository = LocalBudgetRepository(database, System::currentTimeMillis)
            val today =
                java.time.LocalDate
                    .now()
                    .toString()
            val budget =
                session.openNewBudget(groupId, today, repository, System::currentTimeMillis, { UUID.randomUUID().toString() }) {
                    path.writeBytes(it)
                    openDatabase(path)
                }
            try {
                assertTrue(repository.countVisibleRecords(groupId, "transaction") > 0)
                assertEquals(budget.cacheGeneration, repository.readSyncCheckpoint(groupId)?.generation)
            } finally {
                budget.close()
            }
        } finally {
            database.closeDatabase()
        }
    }

    private suspend fun verifyDirectOutbox(
        budget: ActualDirectBudget,
        replacement: ActualDirectBudget,
        transferId: String,
        shopId: String,
        today: String,
    ) {
        val database = openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
        try {
            val repository = LocalBudgetRepository(database, System::currentTimeMillis)
            val baseline = budget.refreshProjection(today)
            val coordinator = ActualDirectSyncCoordinator(budget, repository)
            assertFailsWith<IllegalStateException> { coordinator.synchronize(today, sendQueuedWrites = true) }
            assertTrue(budget.stageMobileCache(today, baseline, repository).matches)
            assertFailsWith<IllegalStateException> {
                ActualDirectSyncCoordinator(replacement, repository).synchronize(today, sendQueuedWrites = true)
            }
            val transfer = baseline.records.single { it.recordType == "transaction" && it.recordId == transferId }
            val operationId = queueUpdate(repository, transfer, buildJsonObject { put("amount", -7000) })
            val interrupted = checkNotNull(repository.claimNextWrite(budget.groupId))
            assertFailsWith<BudgetSyncProtocolException> { budget.submit(interrupted.copy(budgetId = "different-budget")) }
            assertFailsWith<BudgetSyncProtocolException> { budget.submit(interrupted.copy(action = "UNKNOWN")) }
            assertEquals(1, coordinator.synchronize(today, sendQueuedWrites = true).writesCommitted)
            assertEquals("committed", repository.findWrite(operationId)?.status)

            fun record(
                body: JsonObject,
                id: String,
            ) = BudgetSyncJson
                .projectDirectSnapshot(
                    app.tandemforactual.data.actual.ActualSnapshot(
                        listOf(
                            app.tandemforactual.data.actual
                                .ActualRecord("transaction", id, body, "current"),
                        ),
                        baseline.displaySettings!!,
                    ),
                    budget.groupId,
                    budget.cacheGeneration,
                    baseline.cursor,
                ).records
                .single()

            val current = budget.refreshProjection(today)
            val updated = current.records.single { it.recordId == transferId && it.recordType == "transaction" }
            val rejected = queueUpdate(repository, updated, buildJsonObject { put("account", "missing-account") })
            assertEquals(1, coordinator.synchronize(today, sendQueuedWrites = true).writesRejected)
            assertEquals("rejected", repository.findWrite(rejected)?.status)

            val other = current.records.single { it.recordType == "transaction" && it.recordId == shopId }
            val stale = buildJsonObject { put("notes", "queued before the other change") }
            val conflicting = queueUpdate(repository, other, stale)
            val otherBase = BudgetSyncJson.parseJsonObject(other.bodyJson)
            budget.submit(
                UUID.randomUUID().toString(),
                other.recordId,
                "update",
                buildJsonObject { put("notes", "changed elsewhere") },
                otherBase,
            )
            assertEquals(1, coordinator.synchronize(today, sendQueuedWrites = true).writesConflicted)
            assertEquals("conflict", repository.findWrite(conflicting)?.status)

            val createId = "tandem:${UUID.randomUUID()}"
            val newBody = JsonObject(otherBase + ("id" to JsonPrimitive(createId)) + ("notes" to JsonPrimitive("recovery")))
            val createRecord = record(newBody, createId)
            val createPatch =
                JsonObject(
                    newBody.filterKeys {
                        it in
                            setOf("account", "amount", "date", "payee", "category", "notes", "cleared")
                    },
                )
            val createOperation = queueUpdate(repository, createRecord, createPatch, LocalWriteAction.CREATE)
            assertEquals(1, coordinator.synchronize(today, sendQueuedWrites = true).writesCommitted)
            assertEquals("committed", repository.findWrite(createOperation)?.status)

            val created = budget.refreshProjection(today).records.single { it.recordId == createId }
            val interruptedUpdate = queueUpdate(repository, created, buildJsonObject { put("notes", "already saved") })
            assertEquals(OperationStatus.COMMITTED, budget.submit(checkNotNull(repository.claimNextWrite(budget.groupId))).status)
            val baseForDelete = checkNotNull(budget.transaction(createId))
            budget.submit(UUID.randomUUID().toString(), createId, "delete", JsonObject(emptyMap()), baseForDelete)
            assertEquals(1, coordinator.synchronize(today, sendQueuedWrites = true).writesConflicted)
            assertEquals("conflict", repository.findWrite(interruptedUpdate)?.status)

            val deleteOperation = queueUpdate(repository, updated, JsonObject(emptyMap()), LocalWriteAction.DELETE)
            // Retain the transfer for the external readback test. Exercise the DELETE dispatcher with a stale base.
            val delete = checkNotNull(repository.claimNextWrite(budget.groupId))
            assertEquals(deleteOperation, delete.operationId)
            val staleDelete =
                delete.copy(
                    baseSnapshotJson = JsonObject(baseForDelete + ("transferId" to JsonPrimitive("wrong"))).toString(),
                )
            assertEquals(OperationStatus.CONFLICT, budget.submit(staleDelete).status)
        } finally {
            database.closeDatabase()
        }
    }

    private suspend fun queueUpdate(
        repository: LocalBudgetRepository,
        record: RemoteBudgetRecord,
        patch: JsonObject,
        action: LocalWriteAction = LocalWriteAction.UPDATE,
    ): String {
        val operationId = UUID.randomUUID().toString()
        val body = JsonObject(BudgetSyncJson.parseJsonObject(record.bodyJson) + patch)
        repository.enqueueWrite(
            LocalWriteRequest(
                operationId = operationId,
                idempotencyKey = operationId,
                budgetId = record.budgetId,
                recordType = "transaction",
                recordId = record.recordId,
                action = action,
                baseRevision = record.revision.takeUnless { action == LocalWriteAction.CREATE },
                baseSnapshotJson = record.bodyJson.takeUnless { action == LocalWriteAction.CREATE },
                patchJson = patch.toString(),
                optimisticJson = body.toString().takeUnless { action == LocalWriteAction.DELETE },
                createdAtEpochMs = System.currentTimeMillis(),
                optimisticProjection =
                    if (action == LocalWriteAction.DELETE) {
                        null
                    } else {
                        LocalRecordProjection(
                            record.recordDate,
                            (body["account"] as? JsonPrimitive)?.content,
                            record.categoryId,
                            record.payeeId,
                            (body["amount"] as? JsonPrimitive)?.content?.toLong(),
                            record.budgetMonth,
                        )
                    },
            ),
        )
        return operationId
    }

    private suspend fun verifyDownload(
        fresh: ActualDirectBudget,
        shop: String,
        transfer: String,
        toDelete: String,
        output: String?,
    ) {
        val today2 =
            java.time.LocalDate
                .now()
                .toString()
        val savedShop = fresh.transaction(shop)!!
        assertEquals(JsonPrimitive(-4321), savedShop["amount"])
        assertEquals(JsonPrimitive(true), savedShop["cleared"])
        assertEquals(JsonPrimitive("first"), savedShop["notes"])
        assertNull(fresh.transaction(toDelete))
        val checkingSide = fresh.transaction(transfer)!!
        assertEquals(JsonPrimitive(-7000), checkingSide["amount"])
        val savingsSide = fresh.transaction((checkingSide["transferId"] as JsonPrimitive).content)!!
        assertEquals(JsonPrimitive(7000), savingsSide["amount"])
        if (output != null) {
            val snapshot = fresh.snapshot(today2)
            val records =
                JsonArray(
                    snapshot.records.map {
                        JsonObject(
                            mapOf(
                                "type" to JsonPrimitive(it.type),
                                "id" to JsonPrimitive(it.id),
                                "body" to it.body,
                                "revision" to JsonPrimitive(it.revision),
                            ),
                        )
                    },
                )
            val written =
                buildJsonObject {
                    put("shop", shop)
                    put("transfer", transfer)
                    put("deleted", toDelete)
                }
            File(
                output,
            ).writeText(JsonObject(mapOf("today" to JsonPrimitive(today2), "records" to records, "written" to written)).toString())
        }
    }
}
