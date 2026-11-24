package app.tandemforactual.api.client

import androidx.room3.Room
import app.tandemforactual.data.actual.createPayee
import app.tandemforactual.data.actual.deletePayee
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.closeDatabase
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.store.BudgetSyncCoordinator
import app.tandemforactual.data.store.BudgetSyncRemoteException
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import app.tandemforactual.data.store.RemoteOperationStatus
import app.tandemforactual.data.store.budgetWrites
import app.tandemforactual.data.store.countVisibleRecords
import app.tandemforactual.data.store.enqueueWrite
import app.tandemforactual.data.store.importBudgetWrites
import app.tandemforactual.data.store.removeBudget
import app.tandemforactual.data.store.unsentWrites
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DirectBudgetSyncRemoteTest {
    @Test
    fun syncsTheMobileCacheIncrementally(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val client = TandemApiClient(http = server.http, budgetFiles = TempBudgetFiles(), today = { "2026-11-21" })
            client.signInWithPassword("https://budget.example", "test", "Phone")
            val remote = DirectBudgetSyncRemote(client)
            val cache = openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                val repository = LocalBudgetRepository(cache) { 1_795_255_200_000L }
                val coordinator = BudgetSyncCoordinator(repository, remote)
                val first = coordinator.synchronize("group")
                assertTrue(first.fullSnapshotApplied)
                assertTrue(repository.countVisibleRecords("group", "transaction") > 0)
                val quiet = coordinator.synchronize("group")
                assertFalse(quiet.fullSnapshotApplied)
                assertEquals(0, quiet.changesApplied)

                val budget = client.budget("group")
                val checking =
                    budget
                        .snapshot("2026-11-21")
                        .records
                        .first { it.type == "account" && it.body["name"] == JsonPrimitive("Checking") }
                        .id
                val id = "tandem:${UUID.randomUUID()}"
                repository.enqueueWrite(
                    LocalWriteRequest(
                        operationId = UUID.randomUUID().toString(),
                        idempotencyKey = UUID.randomUUID().toString(),
                        budgetId = "group",
                        recordType = "transaction",
                        recordId = id,
                        action = LocalWriteAction.CREATE,
                        baseRevision = null,
                        baseSnapshotJson = null,
                        patchJson =
                            buildJsonObject {
                                put("account", checking)
                                put("amount", -1234)
                                put("date", "2026-11-20")
                                put("payee", null as String?)
                                put("category", null as String?)
                                put("notes", "from the outbox")
                                put("cleared", false)
                            }.toString(),
                        optimisticJson =
                            buildJsonObject {
                                put("id", id)
                                put("account", checking)
                                put("amount", -1234)
                                put("date", "2026-11-20")
                                put("notes", "from the outbox")
                            }.toString(),
                        createdAtEpochMs = 1L,
                        optimisticProjection =
                            app.tandemforactual.data.store.LocalRecordProjection(
                                recordDate = "2026-11-20",
                                accountId = checking,
                                categoryId = null,
                                payeeId = null,
                                amountMinor = -1234,
                                budgetMonth = null,
                            ),
                    ),
                )
                assertEquals(1, coordinator.synchronize("group").writesCommitted)
                val record = checkNotNull(remote.readTransaction("group", id, null))
                assertEquals(-1234L, record.projection.amountMinor)
                assertNull(remote.readTransaction("group", "missing", null))

                client.edit("group") { it.createPayee("Changed in Actual") }
                val changed = coordinator.synchronize("group")
                assertFalse(changed.fullSnapshotApplied)
                assertTrue(changed.changesApplied >= 1)
                val made =
                    budget
                        .snapshot("2026-11-21")
                        .records
                        .first {
                            it.type == "payee" &&
                                it.body["name"] == JsonPrimitive("Changed in Actual")
                        }.id
                client.edit("group") { it.deletePayee(made) }
                assertTrue(coordinator.synchronize("group").changesApplied >= 1)

                budget.saveServedProjection("1", emptyMap())
                assertTrue(coordinator.synchronize("group").fullSnapshotApplied)
                assertEquals("operation_not_found", assertFailsWith<BudgetSyncRemoteException> { remote.readOperation("x") }.errorCode)
                assertTrue(remote.readSnapshot("group", null).records.isNotEmpty())
                server.syncFailure = "file-has-reset"
                assertFailsWith<TandemApiException> { coordinator.synchronize("group") }
            } finally {
                cache.closeDatabase()
            }
        }

    private fun op(
        action: String,
        recordId: String,
        patch: String,
        base: String? = null,
        budgetId: String = "group",
        recordType: String = "transaction",
    ) = OutboxOperationEntity(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        budgetId,
        recordType,
        recordId,
        action,
        null,
        base,
        patch,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "queued",
        0,
        null,
        null,
        1L,
        1L,
    )

    @Test
    fun appliesOutboxOperationsAndPagesSnapshots(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val files = TempBudgetFiles()
            val client = TandemApiClient(http = server.http, budgetFiles = files, today = { "2026-11-21" })
            client.signInWithPassword("https://budget.example", "test", "Phone")
            val remote = DirectBudgetSyncRemote(client)
            val first = remote.readSnapshotPage("group", null, null, null, null, 5, null)
            assertTrue(first.hasMore)
            val second = remote.readSnapshotPage("group", first.generation, first.cursor, first.nextAfterType, first.nextAfterId, 5, null)
            assertEquals(first.records.size, second.records.size)
            assertTrue(second.records.none { it in first.records })
            val moved = remote.readSnapshotPage("group", first.generation, "stale", first.nextAfterType, first.nextAfterId, 5, null)
            assertTrue(moved.resetRequired)
            assertTrue(moved.records.isEmpty())
            val lost = remote.readSnapshotPage("group", first.generation, first.cursor, "transaction", "gone", 5, null)
            assertTrue(lost.resetRequired)
            val all = remote.readSnapshotPage("group", null, null, null, null, 100_000, null)
            assertFalse(all.hasMore)
            assertNull(all.nextAfterId)

            val checking = all.records.first { it.recordType == "account" && it.bodyJson.contains("\"Checking\"") }.recordId
            val id = "tandem:${UUID.randomUUID()}"
            val created =
                remote.submitOperation(
                    op(
                        "CREATE",
                        id,
                        """{"account":"$checking","amount":-500,"date":"2026-11-19","payee":null,"category":null,"notes":null,"cleared":false}""",
                    ),
                    null,
                )
            assertEquals(RemoteOperationStatus.COMMITTED, created.status)
            val base = remote.readTransaction("group", id, null)!!.bodyJson
            assertEquals(
                RemoteOperationStatus.COMMITTED,
                remote.submitOperation(op("UPDATE", id, """{"amount":-700}""", base), null).status,
            )
            val stale = remote.submitOperation(op("UPDATE", id, """{"amount":-900}""", base), null)
            assertEquals(RemoteOperationStatus.CONFLICT, stale.status)
            assertEquals("transaction-conflict", stale.errorCode)
            assertEquals(
                "invalid_request",
                assertFailsWith<TandemApiException> {
                    remote.submitOperation(
                        op("UPDATE", "missing", """{"amount":-1}"""),
                        null,
                    )
                }.errorCode,
            )
            assertEquals(
                RemoteOperationStatus.CONFLICT,
                remote.submitOperation(op("UPDATE", "missing", """{"amount":-1}""", base), null).status,
            )
            val rejected =
                remote.submitOperation(
                    op(
                        "CREATE",
                        "tandem:${UUID.randomUUID()}",
                        """{"account":"missing","amount":-5,"date":"2026-11-19","payee":null,"category":null,"notes":null,"cleared":false}""",
                    ),
                    null,
                )
            assertEquals(RemoteOperationStatus.REJECTED, rejected.status)
            assertEquals(
                RemoteOperationStatus.COMMITTED,
                remote.submitOperation(op("DELETE", id, "{}", remote.readTransaction("group", id, null)!!.bodyJson), null).status,
            )
            val pendingId = "tandem:${UUID.randomUUID()}"
            val pending =
                op(
                    "CREATE",
                    pendingId,
                    """{"account":"$checking","amount":-42,"date":"2026-11-19","payee":null,"category":null,"notes":null,"cleared":false}""",
                )
            server.syncFailure = "token-expired"
            val awaiting = assertFailsWith<BudgetSyncRemoteException> { remote.submitOperation(pending, null) }
            assertTrue(awaiting.awaitingUpload)
            assertTrue(awaiting.isRetryableBeforeSubmission)
            assertFalse(awaiting.isDefinitiveClientError)
            val rejectedOffline =
                remote.submitOperation(
                    op(
                        "CREATE",
                        "tandem:${UUID.randomUUID()}",
                        """{"account":"missing","amount":-5,"date":"2026-11-19","payee":null,"category":null,"notes":null,"cleared":false}""",
                    ),
                    null,
                )
            assertEquals(RemoteOperationStatus.REJECTED, rejectedOffline.status)
            server.syncFailure = null
            assertEquals(RemoteOperationStatus.COMMITTED, remote.submitOperation(pending, null).status)
            assertEquals(-42L, remote.readTransaction("group", pendingId, null)!!.projection.amountMinor)
            assertFailsWith<TandemApiException> { remote.submitOperation(op("MOVE", id, "{}"), null) }
            assertFailsWith<TandemApiException> { remote.submitOperation(op("CREATE", id, "{}", recordType = "payee"), null) }
            assertFailsWith<TandemApiException> { remote.submitOperation(op("CREATE", id, "[]"), null) }

            client.budget("group").closeNow()
            files.open("group")!!.apply { execute("UPDATE tandem_direct_sync SET group_id = 'other'") }.close()
            val reopened = TandemApiClient(http = server.http, budgetFiles = files, today = { "2026-11-21" })
            reopened.signInWithPassword("https://budget.example", "test", "Phone")
            assertFailsWith<Exception> { reopened.budget("group") }
            val defaults = TandemApiClient()
            assertEquals(10, defaults.today().length)
        }

    @Test
    fun editsQueuedBeforeAResetReplayOnceAgainstTheFreshDownload(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val client = TandemApiClient(http = server.http, budgetFiles = TempBudgetFiles(), today = { "2026-11-21" })
            client.signInWithPassword("https://budget.example", "test", "Phone")
            val cache = openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                val repository = LocalBudgetRepository(cache) { 1_795_255_200_000L }
                val coordinator = BudgetSyncCoordinator(repository, DirectBudgetSyncRemote(client))
                coordinator.synchronize("group")
                val checking =
                    client
                        .budget("group")
                        .snapshot("2026-11-21")
                        .records
                        .first { it.type == "account" && it.body["name"] == JsonPrimitive("Checking") }
                        .id

                fun create(id: String) =
                    LocalWriteRequest(
                        operationId = UUID.randomUUID().toString(),
                        idempotencyKey = UUID.randomUUID().toString(),
                        budgetId = "group",
                        recordType = "transaction",
                        recordId = id,
                        action = LocalWriteAction.CREATE,
                        baseRevision = null,
                        baseSnapshotJson = null,
                        patchJson =
                            buildJsonObject {
                                put("account", checking)
                                put("amount", -500)
                                put("date", "2026-11-20")
                                put("payee", null as String?)
                                put("category", null as String?)
                                put("notes", id)
                                put("cleared", false)
                            }.toString(),
                        optimisticJson =
                            buildJsonObject {
                                put("id", id)
                                put("account", checking)
                                put("amount", -500)
                                put("date", "2026-11-20")
                            }.toString(),
                        createdAtEpochMs = 1L,
                        optimisticProjection =
                            app.tandemforactual.data.store.LocalRecordProjection(
                                recordDate = "2026-11-20",
                                accountId = checking,
                                categoryId = null,
                                payeeId = null,
                                amountMinor = -500,
                                budgetMonth = null,
                            ),
                    )
                val uploaded = "tandem:${UUID.randomUUID()}"
                val waiting = "tandem:${UUID.randomUUID()}"
                repository.enqueueWrite(create(uploaded))
                assertEquals(1, coordinator.synchronize("group").writesCommitted)
                repository.enqueueWrite(create(waiting))
                val sent = repository.budgetWrites("group").first { it.recordId == uploaded }
                cache.outboxDao().put(sent.copy(status = "uncertain"))

                client.retireBudget("group")
                val replay = repository.unsentWrites("group")
                repository.removeBudget("group")
                repository.importBudgetWrites(replay)
                assertEquals(2, coordinator.synchronize("group").writesCommitted)

                val ids =
                    client
                        .budget("group")
                        .snapshot("2026-11-21")
                        .records
                        .filter { it.type == "transaction" }
                        .map { it.id }
                assertEquals(1, ids.count { it == uploaded })
                assertEquals(1, ids.count { it == waiting })
            } finally {
                cache.closeDatabase()
            }
        }

    @Test
    fun backgroundSyncAndTheAppShareOneBudgetFileSafely(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val files = TempBudgetFiles()
            val app = TandemApiClient(http = server.http, budgetFiles = files, today = { "2026-11-21" })
            val background = TandemApiClient(http = server.http, budgetFiles = files, today = { "2026-11-21" })
            app.signInWithPassword("https://budget.example", "test", "Phone")
            background.signInWithPassword("https://budget.example", "test", "Phone")
            app.budget("group").sync()
            background.edit("group") { it.createPayee("From background") }
            app.edit("group") { it.createPayee("From the app") }
            background.edit("group") { it.createPayee("Background again") }
            app.budget("group").sync()
            val names =
                app
                    .budget("group")
                    .snapshot("2026-11-21")
                    .records
                    .filter { it.type == "payee" }
                    .map { it.body["name"] }
            assertTrue(JsonPrimitive("From background") in names)
            assertTrue(JsonPrimitive("Background again") in names)
        }

    @Test
    fun theAppAndBackgroundSyncCanWriteToOneBudgetAtTheSameTime(): Unit =
        runBlocking(Dispatchers.Default) {
            val server = FakeActualServer()
            val files = TempBudgetFiles()
            val clients = List(3) { TandemApiClient(http = server.http, budgetFiles = files, today = { "2026-11-21" }) }
            clients.forEach { it.signInWithPassword("https://budget.example", "test", "Phone") }
            clients.first().budget("group").sync()
            val names = List(45) { "Payee $it" }
            names
                .mapIndexed { index, name ->
                    val client = clients[index % clients.size]
                    async {
                        client.edit("group") { it.createPayee(name) }
                        if (index % 5 == 0) client.budget("group").sync()
                    }
                }.forEach { it.await() }
            clients.forEach { it.budget("group").sync() }
            val other = TandemApiClient(http = server.http, budgetFiles = TempBudgetFiles(), today = { "2026-11-21" })
            other.signInWithPassword("https://budget.example", "test", "Tablet")
            val budget = other.budget("group").also { it.sync() }
            val payees =
                budget
                    .snapshot("2026-11-21")
                    .records
                    .filter { it.type == "payee" }
                    .map { it.body["name"] }
            names.forEach { name -> assertEquals(1, payees.count { it == JsonPrimitive(name) }, name) }
        }

    @Test
    fun backsUpTheWholeBudgetSoActualCanImportIt(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val client = TandemApiClient(http = server.http, budgetFiles = TempBudgetFiles(), today = { "2026-11-21" })
            client.signInWithPassword("https://budget.example", "test", "Phone")
            client.budget("group").sync()
            val plain = client.budgetBackup("group", null, "Household", """{"records":[]}""", passphrase = null)
            val opened =
                app.tandemforactual.data.planning.BudgetBackupFile
                    .read(plain, null)
            opened as app.tandemforactual.data.planning.BudgetBackupContents.Opened
            assertEquals("""{"records":[]}""", opened.planningJson)
            assertTrue(opened.metadataJson!!.contains("\"budgetName\":\"Household\""))
            val copy =
                kotlin.io.path
                    .createTempFile(suffix = ".sqlite")
                    .toFile()
                    .apply { writeBytes(opened.database) }
            val restored =
                app.tandemforactual.data.actual.ActualDatabase(
                    androidx.sqlite.driver.bundled
                        .BundledSQLiteDriver()
                        .open(copy.path),
                )
            val live =
                client
                    .budget("group")
                    .snapshot("2026-11-21")
                    .records
                    .count { it.type == "transaction" }
            assertEquals(live.toLong(), restored.first("SELECT count(*) AS n FROM v_transactions")!!["n"])
            restored.close()
            copy.delete()

            val sealed = client.budgetBackup("group", null, "Household", null, passphrase = "secret")
            assertEquals(
                app.tandemforactual.data.planning.BudgetBackupContents.WrongPassphrase,
                app.tandemforactual.data.planning.BudgetBackupFile
                    .read(sealed, "nope"),
            )
            assertEquals(
                "not_supported",
                assertFailsWith<TandemApiException> {
                    TandemApiClient().budgetBackup("group", null, "x", null, null)
                }.errorCode,
            )
        }
}
