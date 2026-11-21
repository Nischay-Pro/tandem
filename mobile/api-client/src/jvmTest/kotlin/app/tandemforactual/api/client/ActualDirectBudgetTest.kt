package app.tandemforactual.api.client

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.actual.ActualDatabase
import app.tandemforactual.data.actual.OperationStatus
import app.tandemforactual.data.actual.SyncProtocol
import app.tandemforactual.data.actual.SyncResponse
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.closeDatabase
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.RemoteBudgetRecord
import app.tandemforactual.data.store.RemoteBudgetSnapshot
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActualDirectBudgetTest {
    private val fixture = File("../data/src/jvmTest/resources/actual/parity-envelope")
    private val now = { 1_795_255_200_000L }
    private val newId = { UUID.randomUUID().toString() }

    @Test
    fun opensPreviewsAndReopensAnActualBudget() =
        runBlocking {
            val archive = fixture.resolve("budget.zip").readBytes()
            val serverState = SyncProtocol.decodeResponse(fixture.resolve("sync-response.bin").readBytes())
            val path = File.createTempFile("actual-budget", ".sqlite")
            var reset = false

            fun connect() = ActualDatabase(BundledSQLiteDriver().open(path.path))
            try {
                HttpClient(
                    MockEngine { request ->
                        when (request.url.encodedPath) {
                            "/sync/get-user-file-info" -> respond("""{"data":{"groupId":"group"}}""")
                            "/sync/download-user-file" -> respond(archive)
                            "/sync/sync" -> {
                                if (reset) return@MockEngine respond("file-has-reset", HttpStatusCode.Conflict)
                                val sent = SyncProtocol.decodeRequest((request.body as OutgoingContent.ByteArrayContent).bytes())
                                respond(
                                    SyncProtocol.encode(
                                        SyncResponse(serverState.messages.filter { it.timestamp > sent.since }, serverState.merkle),
                                    ),
                                )
                            }
                            else -> error("Unexpected path ${request.url.encodedPath}")
                        }
                    },
                ).use { http ->
                    val client = ActualServerClient(http, "https://budget.example").apply { token = "session" }
                    val budget =
                        ActualDirectBudget.download(client, "file", now, newId) { data ->
                            path.writeBytes(data)
                            connect()
                        }
                    try {
                        val today = "2026-11-21"
                        val preview = budget.preview(today)
                        assertTrue(preview.records.isNotEmpty())
                        val record = preview.records.first { it.type == "transaction" }
                        assertEquals(record.body, budget.transaction(record.id))
                        val oldRecords =
                            preview.records.map {
                                RemoteBudgetRecord(
                                    "group",
                                    it.type,
                                    it.id,
                                    it.revision,
                                    it.body.toString(),
                                    "revision",
                                )
                            }
                        val legacy = RemoteBudgetSnapshot("group", "revision", "1", oldRecords, preview.displaySettings)
                        assertTrue(preview.compareWithGateway(legacy).matches)
                        val cache =
                            openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
                        try {
                            val repository = LocalBudgetRepository(cache, now)
                            assertTrue(budget.stageMobileCache(today, legacy, repository).matches)
                            val checkpoint = repository.readSyncCheckpoint(budget.groupId)
                            reset = true
                            assertFailsWith<ActualServerException> {
                                ActualDirectSyncCoordinator(budget, repository).synchronize(today, sendQueuedWrites = true)
                            }
                            assertEquals(checkpoint, repository.readSyncCheckpoint(budget.groupId))
                            assertEquals("file-has-reset", repository.readSyncState(budget.groupId)?.lastErrorCode)
                        } finally {
                            reset = false
                            cache.closeDatabase()
                        }
                        val changed = oldRecords[1].copy(revision = "changed")
                        val added = oldRecords.first().copy(recordId = "extra")
                        val mismatch = preview.compareWithGateway(legacy.copy(records = listOf(changed, added), displaySettings = null))
                        assertFalse(mismatch.matches)
                        assertFalse(mismatch.displaySettingsMatch)
                        assertEquals(setOf(BudgetRecordKey(changed.recordType, changed.recordId)), mismatch.changed)
                        assertEquals(setOf(BudgetRecordKey(added.recordType, added.recordId)), mismatch.missingFromDirect)
                        assertTrue(mismatch.addedByDirect.isNotEmpty())
                        budget.sync()
                        assertEquals(preview, budget.snapshot(today))
                        val patch = Json.parseToJsonElement("""{"notes":"Updated in the test"}""").jsonObject
                        val result = budget.submit("edit", record.id, "update", patch, record.body)
                        assertEquals(OperationStatus.COMMITTED, result.status)
                        assertEquals(patch["notes"], budget.transaction(record.id)?.get("notes"))
                    } finally {
                        budget.close()
                    }
                    assertFailsWith<IllegalArgumentException> {
                        ActualDirectBudget.reopen(
                            connect(),
                            client,
                            "file",
                            "wrong-group",
                            now,
                            newId,
                        )
                    }
                    val reopened = ActualDirectBudget.reopen(connect(), client, "file", "group", now, newId)
                    reopened.close()
                }
            } finally {
                path.delete()
            }
            Unit
        }

    @Test
    fun rejectsUnavailableBudgetArchives() =
        runBlocking {
            var info = """{"data":{}}"""
            val emptyArchive = ByteArrayOutputStream().also { ZipOutputStream(it).close() }.toByteArray()
            HttpClient(
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("file-info")) respond(info) else respond(emptyArchive)
                },
            ).use { http ->
                val client = ActualServerClient(http, "https://budget.example").apply { token = "session" }
                assertEquals(
                    "missing-group-id",
                    assertFailsWith<ActualServerException> {
                        ActualDirectBudget.download(client, "file", now, newId) { error("Unexpected install") }
                    }.reason,
                )
                info = """{"data":{"groupId":"group","encryptMeta":{}}}"""
                assertEquals(
                    "encrypted-budget",
                    assertFailsWith<ActualServerException> {
                        ActualDirectBudget.download(client, "file", now, newId) { error("Unexpected install") }
                    }.reason,
                )
                info = """{"data":{"groupId":"group"}}"""
                assertEquals(
                    "invalid-budget-archive",
                    assertFailsWith<ActualServerException> {
                        ActualDirectBudget.download(client, "file", now, newId) { error("Unexpected install") }
                    }.reason,
                )
            }
        }
}
