package app.tandemforactual.api.client

import app.tandemforactual.data.actual.ActualEditException
import app.tandemforactual.data.actual.ActualSyncException
import app.tandemforactual.data.actual.UnsupportedRuleException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TandemApiClientTest {
    private val url = "https://budget.example"

    private fun client(
        server: FakeActualServer = FakeActualServer(),
        files: TempBudgetFiles? = TempBudgetFiles(),
    ) = TandemApiClient(http = server.http, budgetFiles = files, today = { "2026-11-21" })

    @Test
    fun signsInAndKeepsTheSession(): Unit =
        runBlocking {
            val client = client()
            assertFalse(client.isAuthenticated)
            assertNull(client.readServerCapabilities())
            assertNull(client.exportStoredSession())
            assertFailsWith<TandemApiException> { client.listBudgets() }.also { assertEquals("sign_in_required", it.errorCode) }
            val methods = client.readActualLoginMethods(url)
            assertTrue(methods.bootstrapped)
            assertEquals("password", methods.loginMethod)
            assertEquals(
                "invalid_credentials",
                assertFailsWith<TandemApiException> { client.signInWithPassword(url, "wrong", "Phone") }.errorCode,
            )
            val device = client.signInWithPassword("$url/", "test", "Phone")
            assertEquals(listOf("group", "locked", "local").filter { it != "local" }, device.budgetIds.filter { it != "local" })
            assertEquals("actual:budget.example", device.principalId)
            assertEquals(url, client.currentServerUrl)
            assertEquals(url, client.activeServerUrl)
            assertFalse(client.isUsingLocalServer)
            assertTrue(client.readServerCapabilities()!!.supports("hold-total"))
            assertEquals(
                listOf(BudgetCatalogItem("group", "Direct", false), BudgetCatalogItem("locked", "Locked", true)),
                client.listBudgets(),
            )
            val stored = checkNotNull(client.exportStoredSession())
            client.forgetSession()
            assertFalse(client.isAuthenticated)
            val restored = client(files = null)
            assertEquals(device, restored.restoreStoredSession(stored))
            assertTrue(restored.isAuthenticated)
            assertFailsWith<TandemApiProtocolException> { restored.restoreStoredSession("nope") }
            assertFailsWith<TandemApiProtocolException> { restored.restoreStoredSession("{}") }
            assertFailsWith<TandemApiProtocolException> {
                restored.restoreStoredSession("""{"deviceId":"d","principalId":"p","serverUrl":"$url","token":"t"}""")
            }
            assertEquals("not_supported", assertFailsWith<TandemApiException> { restored.budget("group") }.errorCode)
            restored.setLocalServerUrl("https://lan")
            assertFalse(restored.refreshLocalServer())
            restored.useExternalServerAfterLocalFailure()
            assertEquals(1, restored.withExclusiveSession { 1 })
            assertEquals(
                "openid_unsupported",
                assertFailsWith<TandemApiException> { restored.startActualOpenIdSignIn(url, "d", "c", "s") }.errorCode,
            )
            assertEquals(
                "openid_unsupported",
                assertFailsWith<TandemApiException> { restored.completeActualOpenIdSignIn(url, "c", "v") }.errorCode,
            )
            restored.revokeCurrentDevice()
            assertFalse(restored.isAuthenticated)
            assertEquals(
                "invalid-server-url",
                assertFailsWith<TandemApiException> {
                    restored.readActualLoginMethods("http://budget.example")
                }.errorCode,
            )
        }

    @Test
    fun opensBudgetsAndEditsThem(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val files = TempBudgetFiles()
            val client = client(server, files)
            client.signInWithPassword(url, "test", "Phone")
            assertEquals("budget_password_required", assertFailsWith<TandemApiException> { client.budget("locked") }.errorCode)
            assertEquals("budget_password_incorrect", assertFailsWith<TandemApiException> { client.budget("locked", "nope") }.errorCode)
            val locked = client.budget("locked", "secret")
            locked.sync()
            client.createBudgetPayee("locked", "Sealed", "secret")
            assertTrue(locked.snapshot("2026-11-21").records.any { it.type == "payee" && it.body["name"] == JsonPrimitive("Sealed") })
            locked.closeNow()
            val unlocking = client(server, files)
            unlocking.signInWithPassword(url, "test", "Phone")
            assertEquals("budget_password_incorrect", assertFailsWith<TandemApiException> { unlocking.budget("locked", "nope") }.errorCode)
            assertTrue(
                unlocking
                    .budget("locked", "secret")
                    .snapshot("2026-11-21")
                    .records
                    .any { it.body["name"] == JsonPrimitive("Sealed") },
            )
            assertEquals("budget_unavailable", assertFailsWith<TandemApiException> { client.budget("missing") }.errorCode)
            val budget = client.budget("group")
            assertEquals(budget, client.budget("group"))
            budget.sync()
            val snapshot = budget.snapshot("2026-11-21")

            fun id(
                type: String,
                name: String,
            ) = snapshot.records.first { it.type == type && (it.body["name"] as JsonPrimitive).content == name }.id
            val checking = id("account", "Checking")
            val groceries = id("category", "Groceries")
            val dining = id("category", "Dining")
            val bills = id("category-group", "Bills")
            val wallet = client.createBudgetAccount("group", "Wallet", false, null, null)
            client.renameBudgetAccount("group", wallet, "Pocket", null)
            client.moveBudgetAccount("group", wallet, checking, null)
            client.closeBudgetAccount("group", wallet, null, null, null)
            val spare = client.createBudgetAccount("group", "Spare", false, null, null)
            client.closeBudgetAccount("group", spare, null, null, null)
            assertEquals(
                "invalid_request",
                assertFailsWith<TandemApiException> { client.reopenBudgetAccount("group", checking, null) }.errorCode,
            )
            val payee = client.createBudgetPayee("group", "Bakery", null)
            client.manageBudgetPayee("group", payee, "rename", "Bread", null, null)
            client.manageBudgetPayee("group", payee, "default-category", null, null, null, groceries)
            val other = client.createBudgetPayee("group", "Other", null)
            client.manageBudgetPayee("group", other, "merge", null, payee, null)
            val gone = client.createBudgetPayee("group", "Gone", null)
            client.manageBudgetPayee("group", gone, "delete", null, null, null)
            assertFailsWith<TandemApiException> { client.manageBudgetPayee("group", payee, "merge", null, null, null) }
            assertFailsWith<TandemApiException> { client.manageBudgetPayee("group", payee, "explode", null, null, null) }
            val group = client.createBudgetCategoryGroup("group", "Travel", false, null)
            val flights = client.createBudgetCategory("group", group, "Flights", null)
            client.renameBudgetCategory("group", flights, "Air", null)
            client.manageBudgetCategory("group", flights, "hide", null, null)
            client.manageBudgetCategoryGroup("group", group, "rename", "Trips", null, null)
            client.setBudgetCategoryAmount("group", groceries, "2026-11", 50_000, null)
            client.setBudgetCategoryCarryover("group", dining, "2026-11", true, null)
            client.copyBudgetMonth("group", "2026-12", null)
            client.applyBudgetTemplate("group", "2027-01", listOf(groceries to 1_000L), null)
            client.holdBudgetForNextMonth("group", "2026-11", 1_000, null)
            client.resetBudgetHold("group", "2026-11", null)
            client.setBudgetCategoryAmount("group", groceries, "2026-11", 900_000, null)
            client.moveMoneyBetweenCategories("group", "2026-11", groceries, dining, 100, null)
            client.deleteBudgetCategory("group", flights, null, null)
            client.setBudgetOrder("group", buildJsonObject { put("categories", JsonArray(emptyList())) }, null, "key")
            client.createBudgetRule(
                "group",
                buildJsonObject {
                    put(
                        "conditions",
                        JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("field", "notes")
                                    put("op", "contains")
                                    put("value", "x")
                                },
                            ),
                        ),
                    )
                    put(
                        "actions",
                        JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("field", "cleared")
                                    put("op", "set")
                                    put("value", true)
                                },
                            ),
                        ),
                    )
                },
                null,
            )
            val rule =
                budget
                    .snapshot("2026-11-21")
                    .records
                    .first { it.type == "rule" && it.body.toString().contains("\"cleared\"") }
                    .id
            client.manageBudgetRule(
                "group",
                rule,
                "edit",
                buildJsonObject {
                    put(
                        "conditions",
                        JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("field", "notes")
                                    put("op", "contains")
                                    put("value", "y")
                                },
                            ),
                        ),
                    )
                    put(
                        "actions",
                        JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("field", "cleared")
                                    put("op", "set")
                                    put("value", false)
                                },
                            ),
                        ),
                    )
                },
                null,
            )
            assertFailsWith<TandemApiException> { client.manageBudgetRule("group", rule, "edit", null, null) }
            assertFailsWith<TandemApiException> { client.manageBudgetRule("group", rule, "explode", null, null) }
            client.manageBudgetRule("group", rule, "delete", null, null)
            val schedule = snapshot.records.first { it.type == "schedule" }
            assertFailsWith<TandemApiException> {
                client.manageBudgetRule("group", schedule.body["rule"].let { (it as JsonPrimitive).content }, "delete", null, null)
            }
            client.manageBudgetSchedule("group", schedule.id, "rename", "Rent", null)
            client.manageBudgetSchedule(
                "group",
                schedule.id,
                "edit",
                "Rent again",
                null,
                buildJsonObject {
                    put("amountOp", "is")
                    put("amountMinor", -1)
                },
            )
            val next =
                budget
                    .snapshot(
                        "2026-11-21",
                    ).records
                    .first { it.id == schedule.id }
                    .body["next_date"]
                    .let { (it as JsonPrimitive).content }
            client.manageBudgetSchedule("group", schedule.id, "skip", null, null, expectedNextDate = next)
            val after =
                budget
                    .snapshot(
                        "2026-11-21",
                    ).records
                    .first { it.id == schedule.id }
                    .body["next_date"]
                    .let { (it as JsonPrimitive).content }
            client.manageBudgetSchedule("group", schedule.id, "post", null, null, expectedNextDate = after)
            assertFailsWith<IllegalArgumentException> { client.manageBudgetSchedule("group", schedule.id, "post", null, null) }
            assertFailsWith<TandemApiException> { client.manageBudgetSchedule("group", schedule.id, "explode", null, null) }
            client.manageBudgetSchedule("group", schedule.id, "delete", null, null)
            client.runBudgetBankSync("group", null)
            assertFailsWith<TandemApiException> { client.importBudgetAccountTransactions("group", checking, emptyList(), null) }
            assertFailsWith<TandemApiException> { client.previewBudgetAccountStatementImport("group", checking, emptyList(), null) }
            assertEquals(3, StatementImportPreview(setOf("a")).newRowCount(4))
            assertTrue(server.messages.size > 300)

            client.retireBudget("group")
            assertNull(files.open("group"))
            assertTrue(files.directory.listFiles()!!.any { it.name.startsWith("group-reset-") })
            client.budget("group")
            client.removeBudget("group")
            assertNull(files.open("group"))
            val redownloaded = client.budget("group")
            redownloaded.sync()
            assertTrue(redownloaded.snapshot("2026-11-21").records.any { it.type == "payee" && it.id == payee })
            redownloaded.closeNow()
            val reopened = TandemApiClient(http = server.http, budgetFiles = files, today = { "2026-11-21" })
            reopened.signInWithPassword(url, "test", "Phone")
            files.open("group")!!.apply { execute("DROP TABLE tandem_direct_sync") }.close()
            assertFailsWith<Exception> { reopened.budget("group") }
        }

    @Test
    fun reportsActualFailuresWithTandemCodes(): Unit =
        runBlocking {
            val client = client()

            suspend fun code(error: Exception) = assertFailsWith<TandemApiException> { client.call { throw error } }
            assertEquals("schedule_changed", code(ActualEditException("x", "schedule_changed")).errorCode)
            assertEquals("x", code(ActualEditException("x")).message)
            assertEquals("rule_unsupported", code(UnsupportedRuleException("x")).errorCode)
            assertEquals("out_of_sync", code(ActualSyncException("out-of-sync")).errorCode)
            assertEquals("address_unreachable", code(kotlinx.io.IOException("x")).errorCode)
            assertEquals("unexpected_error", code(IllegalStateException("x")).errorCode)
            assertEquals(
                "invalid_request",
                code(
                    app.tandemforactual.data.actual
                        .InvalidTransactionRequestException("x"),
                ).errorCode,
            )
            assertEquals("sign_in_required", code(TandemApiException("sign_in_required")).errorCode)
            for ((reason, expected) in listOf(
                "invalid-password" to "invalid_credentials",
                "unauthorized" to "sign_in_expired",
                "token-expired" to "sign_in_expired",
                "file-not-found" to "budget_unavailable",
                "encrypted-budget" to "encrypted_budget",
                "file-has-reset" to "file_has_reset",
                "file-has-new-key" to "file_has_new_key",
                "network-failure" to "address_unreachable",
                "something-else" to "something_else",
            )) {
                assertEquals(expected, code(ActualServerException(reason)).errorCode)
            }
            assertFailsWith<CancellationException> { client.call { throw CancellationException("stop") } }
            assertTrue(TandemApiException("unknown").message!!.isNotBlank())
            assertEquals(JsonObject(emptyMap()), JsonObject(emptyMap()))
        }
}
