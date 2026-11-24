package app.tandemforactual.api.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive

/** Runs only with ACTUAL_TEST_URL set: opens and edits a budget that Actual encrypted end to end. */
class ActualEncryptedRoundTripTest {
    private val url = System.getenv("ACTUAL_TEST_URL")
    private val budgetName = System.getenv("ACTUAL_TEST_ENCRYPTED_BUDGET") ?: "Encrypted household"
    private val budgetPassword = System.getenv("ACTUAL_TEST_BUDGET_PASSWORD") ?: "budget-secret"
    private val payee = System.getenv("ACTUAL_TEST_ENCRYPTED_PAYEE") ?: "Encrypted round trip"

    private suspend fun signedIn(files: TempBudgetFiles): Pair<TandemApiClient, String> {
        val client = TandemApiClient(allowInsecurePrivateNetwork = true, http = HttpClient(Java), budgetFiles = files)
        client.signInWithPassword(url!!, System.getenv("ACTUAL_TEST_PASSWORD") ?: "demo-password", "Test")
        val budget = client.listBudgets().first { it.name == budgetName }
        assertTrue(budget.isEncrypted)
        return client to budget.budgetId
    }

    @Test
    fun opensAndEditsAnEncryptedBudget(): Unit =
        runBlocking {
            if (url == null) return@runBlocking
            val (client, id) = signedIn(TempBudgetFiles())
            assertEquals("budget_password_required", assertFailsWith<TandemApiException> { client.budget(id) }.errorCode)
            assertEquals("budget_password_incorrect", assertFailsWith<TandemApiException> { client.budget(id, "wrong") }.errorCode)
            val budget = client.budget(id, budgetPassword)
            budget.sync()
            assertTrue(
                budget.snapshot("2026-11-21").records.any {
                    it.type == "transaction" &&
                        it.body["notes"] == JsonPrimitive("Weekly shop")
                },
            )
            client.createBudgetPayee(id, payee, budgetPassword)
            budget.sync()

            val files = TempBudgetFiles()
            val (fresh, freshId) = signedIn(files)
            val again = fresh.budget(freshId, budgetPassword)
            again.sync()
            assertTrue(again.snapshot("2026-11-21").records.any { it.type == "payee" && it.body["name"] == JsonPrimitive(payee) })
            again.closeNow()

            val reopened = TandemApiClient(allowInsecurePrivateNetwork = true, http = HttpClient(Java), budgetFiles = files)
            reopened.signInWithPassword(url, System.getenv("ACTUAL_TEST_PASSWORD") ?: "demo-password", "Test")
            assertEquals("budget_password_required", assertFailsWith<TandemApiException> { reopened.budget(freshId) }.errorCode)
            assertTrue(
                reopened
                    .budget(freshId, budgetPassword)
                    .snapshot("2026-11-21")
                    .records
                    .any { it.body["name"] == JsonPrimitive(payee) },
            )
        }
}
