package app.tandemforactual.api.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class TandemBudgetFilesTest {
    @Test
    fun deletesTheBudgetsFileFromTheServer(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val client = TandemApiClient(http = server.http, budgetFiles = TempBudgetFiles())
            client.signInWithPassword("https://budget.example", "test", "Phone")
            client.deleteBudgetFromServer("group")
            assertEquals(listOf("""{"fileId":"file"}"""), server.deletes)
            val missing = assertFailsWith<TandemApiException> { client.deleteBudgetFromServer("old") }
            assertEquals("budget_unavailable", missing.errorCode)
            assertEquals(1, server.deletes.size)
        }
}
