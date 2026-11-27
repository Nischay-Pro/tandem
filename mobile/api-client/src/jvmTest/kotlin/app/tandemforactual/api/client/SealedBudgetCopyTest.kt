package app.tandemforactual.api.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive

class SealedBudgetCopyTest {
    private suspend fun signedIn(
        server: FakeActualServer,
        files: app.tandemforactual.data.database.ActualBudgetFiles,
    ) = TandemApiClient(http = server.http, budgetFiles = files, today = { "2026-11-21" }).also {
        it.signInWithPassword("https://budget.example", "test", "Phone")
    }

    private suspend fun TandemApiClient.hasPayee(name: String) =
        budget("locked", "secret").snapshot("2026-11-21").records.any { it.type == "payee" && it.body["name"] == JsonPrimitive(name) }

    @Test
    fun encryptedBudgetsAreOnlyKeptSealed(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val files = SealingBudgetFiles()
            val client = signedIn(server, files)
            client.createBudgetPayee("locked", "Sealed", "secret")
            client.budget("locked").closeNow()
            assertNull(files.open("locked"))
            assertTrue(files.sealedKeyInfo("locked")!!.contains("\"id\":\"key\""))
            assertFalse(files.keys.getValue("locked").contentEquals(server.budgetKey.secret))

            val reopened = signedIn(server, files)
            assertEquals("budget_password_incorrect", assertFailsWith<TandemApiException> { reopened.budget("locked", "nope") }.errorCode)
            assertTrue(reopened.hasPayee("Sealed"))
            assertTrue(reopened.budgetBackup("locked", "secret", "Locked", null, null).isNotEmpty())

            reopened.budget("group").closeNow()
            assertNotNull(files.open("group")).close()
            assertNull(files.sealedKeyInfo("group"))
        }

    @Test
    fun aPlaintextCopyFromAnEarlierVersionIsSealedWhenItOpens(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val plain = TempBudgetFiles()
            val earlier = signedIn(server, plain)
            earlier.createBudgetPayee("locked", "Kept", "secret")
            earlier.budget("locked").closeNow()
            assertTrue(plain.directory.resolve("locked.sqlite").isFile)

            val files = SealingBudgetFiles(plain)
            val client = signedIn(server, files)
            assertTrue(client.hasPayee("Kept"))
            assertFalse(plain.directory.resolve("locked.sqlite").exists())
            assertNotNull(files.sealedKeyInfo("locked"))
        }

    @Test
    fun anUnreadableSealedCopyIsDownloadedAgain(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val files = SealingBudgetFiles()
            signedIn(server, files).budget("locked", "secret").closeNow()
            files.directory.resolve("locked.sealed").writeText("not a database")
            assertTrue(
                signedIn(server, files)
                    .budget("locked", "secret")
                    .snapshot("2026-11-21")
                    .records
                    .isNotEmpty(),
            )
        }

    @Test
    fun aDownloadWithoutSealingInstallsThePlainCopy(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val plain = TempBudgetFiles()
            val budget =
                ActualDirectBudget.download(
                    ActualServerClient(server.http, "https://budget.example").apply { token = "session" },
                    "file",
                    { 0L },
                    { "id" },
                    expectedGroupId = null,
                    unlock = { error("Not encrypted") },
                ) { plain.install("group", it) }
            budget.closeNow()
            assertTrue(plain.directory.resolve("group.sqlite").isFile)
        }
}
