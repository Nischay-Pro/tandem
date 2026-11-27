package app.tandemforactual.api.client

import app.tandemforactual.data.database.ActualBudgetFiles
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class StoredBudgetBackupTest {
    private suspend fun signedIn(
        server: FakeActualServer,
        files: ActualBudgetFiles,
    ) = TandemApiClient(http = server.http, budgetFiles = files, today = { "2026-11-21" }).also {
        it.signInWithPassword("https://budget.example", "test", "Phone")
    }

    private suspend fun offline(
        session: String,
        files: ActualBudgetFiles,
    ) = TandemApiClient(http = HttpClient(MockEngine { error("offline") }), budgetFiles = files).also {
        it.restoreStoredSession(session)
    }

    @Test
    fun backsUpTheDevicesCopyWithoutReachingActual(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val files = SealingBudgetFiles()
            val online = signedIn(server, files)
            online.budget("group").closeNow()
            online.createBudgetPayee("locked", "Sealed", "secret")
            online.budget("locked").closeNow()
            val session = online.exportStoredSession()!!

            val client = offline(session, files)
            assertEquals(false, client.storedCopyEncrypted("group"))
            assertEquals(true, client.storedCopyEncrypted("locked"))
            assertTrue(client.budgetBackup("group", null, "Direct", null, null, download = false).isNotEmpty())
            val sealed = client.budgetBackup("locked", "secret", "Locked", null, "passphrase", download = false)
            assertTrue(
                app.tandemforactual.data.planning.BudgetBackupFile
                    .isSealed(sealed),
            )
        }

    @Test
    fun neverDownloadsABudgetTheDeviceHasNoCopyOf(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val files = TempBudgetFiles()
            val session = signedIn(server, files).exportStoredSession()!!
            val client = TandemApiClient(http = server.http, budgetFiles = files).also { it.restoreStoredSession(session) }

            assertNull(client.storedCopyEncrypted("group"))
            assertEquals(
                "no_device_copy",
                assertFailsWith<TandemApiException> { client.budgetBackup("group", null, "Direct", null, null, download = false) }.errorCode,
            )
            assertEquals(0, server.downloads.get())
            assertFalse(files.directory.resolve("group.sqlite").exists())
        }
}
