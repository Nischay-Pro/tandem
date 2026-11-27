package app.tandemforactual.api.client

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.actual.ActualDatabase
import app.tandemforactual.data.actual.ZipArchive
import app.tandemforactual.data.database.ActualBudgetFiles
import app.tandemforactual.data.planning.BackupArchive
import app.tandemforactual.data.planning.OpenedBackup
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class BudgetRestoreTest {
    private val date = "2026-11-21"

    private suspend fun signedIn(
        server: FakeActualServer,
        files: ActualBudgetFiles,
    ) = TandemApiClient(http = server.http, budgetFiles = files, today = { date }).also {
        it.signInWithPassword("https://budget.example", "test", "Phone")
    }

    private suspend fun TandemApiClient.backupOf(
        budgetId: String,
        password: String?,
    ): OpenedBackup.Contents =
        BackupArchive.open(budgetBackup(budgetId, password, "Household", null, null), null) as OpenedBackup.Contents

    private suspend fun TandemApiClient.payees(
        budgetId: String,
        password: String? = null,
    ) = budget(budgetId, password)
        .snapshot(date)
        .records
        .filter { it.type == "payee" }
        .map { (it.body["name"] as JsonPrimitive).content }

    private fun database(bytes: ByteArray): Pair<ActualDatabase, File> {
        val file = createTempFile(suffix = ".sqlite").toFile().apply { writeBytes(bytes) }
        return ActualDatabase(BundledSQLiteDriver().open(file.path)) to file
    }

    @Test
    fun aBackupBecomesANewBudgetAndTheOneItCameFromIsUntouched(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val files = TempBudgetFiles()
            val client = signedIn(server, files)
            client.createBudgetPayee("group", "In the backup", null)
            val backup = client.backupOf("group", null)
            client.createBudgetPayee("group", "Made later", null)

            val restored = client.restoreBudgetAsNew(backup.database!!, backup.metadataJson, "Household (restored)", null)

            val upload = server.uploaded.values.single()
            assertEquals(RestoredBudget(upload.groupId, "Household (restored)", encrypted = false), restored)
            assertEquals("Household (restored)", upload.name)
            assertNull(upload.encryptMeta)
            assertTrue(server.resets.isEmpty())
            assertTrue(server.createdKeys.isEmpty())
            val archive = ZipArchive.read(upload.body)
            val metadata = Json.parseToJsonElement(archive.getValue("metadata.json").decodeToString()).jsonObject
            assertFalse("id" in metadata)
            assertEquals(JsonPrimitive("Household (restored)"), metadata["budgetName"])
            assertEquals(JsonPrimitive(true), metadata["resetClock"])
            val (uploaded, file) = database(archive.getValue("db.sqlite"))
            assertEquals(0L, uploaded.first("SELECT count(*) AS n FROM messages_crdt")!!["n"])
            assertEquals(0L, uploaded.first("SELECT count(*) AS n FROM messages_clock")!!["n"])
            assertEquals(0L, uploaded.first("SELECT count(*) AS n FROM transactions WHERE tombstone = 1")!!["n"])
            assertEquals(0L, uploaded.first("SELECT count(*) AS n FROM sqlite_master WHERE name LIKE 'tandem_%'")!!["n"])
            uploaded.close()
            file.delete()

            assertTrue("In the backup" in client.payees(restored.budgetId))
            assertFalse("Made later" in client.payees(restored.budgetId))
            assertTrue("Made later" in client.payees("group"))
            assertTrue(files.directory.listFiles()!!.none { it.name.startsWith("restore-") })
            assertTrue(client.listBudgets().any { it.budgetId == restored.budgetId && it.name == "Household (restored)" })
        }

    @Test
    fun anEncryptedBudgetNeedsItsPasswordAndGoesUpUnderTheSameKey(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val files = SealingBudgetFiles()
            val client = signedIn(server, files)
            client.createBudgetPayee("locked", "Sealed payee", "secret")
            val backup = client.backupOf("locked", "secret")

            assertEquals(
                "budget_password_required",
                assertFailsWith<TandemApiException> { client.restoreBudgetAsNew(backup.database!!, null, "Locked", null) }.errorCode,
            )
            assertEquals(
                "budget_password_incorrect",
                assertFailsWith<TandemApiException> {
                    client.restoreBudgetAsNew(backup.database!!, "not json", "Locked", "nope")
                }.errorCode,
            )
            assertTrue(server.uploaded.isEmpty())

            val restored = client.restoreBudgetAsNew(backup.database!!, "not json", "Locked", "secret")

            assertTrue(restored.encrypted)
            val upload = server.uploaded.values.single()
            val meta = Json.parseToJsonElement(upload.encryptMeta!!).jsonObject
            assertEquals(JsonPrimitive("key"), meta["keyId"])
            val key = Json.parseToJsonElement(server.createdKeys.single()).jsonObject
            assertEquals(JsonPrimitive(upload.fileId), key["fileId"])
            assertEquals(JsonPrimitive("salt"), key["keySalt"])
            assertTrue("Sealed payee" in client.payees(restored.budgetId, "secret"))
            assertTrue(files.directory.listFiles()!!.none { it.name.startsWith("restore-") })
        }

    @Test
    fun aFailedRestoreLeavesTheServerAndTheDeviceAsTheyWere(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val files = TempBudgetFiles()
            val client = signedIn(server, files)
            client.createBudgetPayee("group", "Kept", null)
            val backup = client.backupOf("group", null)
            server.failFileUploads = true

            assertFailsWith<TandemApiException> { client.restoreBudgetAsNew(backup.database!!, backup.metadataJson, "Copy", null) }
            assertEquals(
                "backup_unreadable",
                assertFailsWith<TandemApiException> {
                    client.restoreBudgetAsNew("not a database".encodeToByteArray(), null, "Copy", null)
                }.errorCode,
            )

            assertTrue(server.uploaded.isEmpty())
            assertTrue("Kept" in client.payees("group"))
            assertTrue(files.directory.listFiles()!!.none { it.name.startsWith("restore-") })
            assertEquals(
                "not_supported",
                assertFailsWith<TandemApiException> {
                    signedIn(server, files).apply { budgetFiles = null }.restoreBudgetAsNew(backup.database!!, null, "Copy", null)
                }.errorCode,
            )
            assertEquals(
                "sign_in_required",
                assertFailsWith<TandemApiException> {
                    TandemApiClient().restoreBudgetAsNew(backup.database!!, null, "Copy", null)
                }.errorCode,
            )
        }

    @Test
    fun metadataLosesTheIdsOfItsOldBudget() {
        assertNull(restoredMetadata(null))
        assertNull(restoredMetadata("[1]"))
        assertEquals(
            """{"budgetName":"Home","exportedBy":"Tandem"}""",
            restoredMetadata("""{"id":"b1","budgetName":"Home","cloudFileId":"f","groupId":"g","exportedBy":"Tandem"}"""),
        )
    }
}
