package app.tandemforactual.api.client

import app.tandemforactual.data.actual.ActualEditException
import app.tandemforactual.data.actual.ActualSyncException
import app.tandemforactual.data.actual.UnsupportedRuleException
import app.tandemforactual.data.actual.createRule
import app.tandemforactual.data.regions.eu.bank.enablebanking.EnableBanking
import app.tandemforactual.data.regions.eu.bank.gocardless.GoCardless
import app.tandemforactual.data.regions.global.bank.BankInstitution
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.data.regions.global.bank.BrowserLink
import app.tandemforactual.data.regions.global.bank.BrowserLinkStatus
import app.tandemforactual.data.regions.us.bank.simplefin.SimpleFin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class TandemApiClientTest {
    @Test
    fun todayIsTheDevicesLocalDateNotUtc() {
        val original = java.util.TimeZone.getDefault()
        try {
            for (zone in listOf("Pacific/Kiritimati", "Pacific/Pago_Pago")) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone))
                assertEquals(
                    java.time.LocalDate
                        .now(java.time.ZoneId.of(zone))
                        .toString(),
                    TandemApiClient().today(),
                )
            }
        } finally {
            java.util.TimeZone.setDefault(original)
        }
    }

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
            val openIdServer = FakeActualServer()
            val restored = client(openIdServer, files = null)
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
            assertEquals("https://idp.example/authorize", restored.startActualOpenIdSignIn(url, "d", "c", "state"))
            assertEquals("$OPEN_ID_RETURN/state", openIdServer.openIdReturnUrl)
            assertEquals("actual:${java.net.URI(url).host}", restored.completeActualOpenIdSignIn(url, "session", "v").principalId)
            assertEquals(
                "invalid_credentials",
                assertFailsWith<TandemApiException> {
                    restored.completeActualOpenIdSignIn(url, "expired", "v")
                }.errorCode,
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
    fun anEditStillCountsWhenItsUploadFailsAndGoesUpWithTheNextSync(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val client = client(server, TempBudgetFiles())
            client.signInWithPassword(url, "test", "Phone")
            val budget = client.budget("group")
            budget.sync()
            server.failUploads = true

            val wallet = client.createBudgetAccount("group", "Wallet", false, null, null)

            server.failUploads = false
            budget.sync()
            val other = client(server, TempBudgetFiles())
            other.signInWithPassword(url, "test", "Phone")
            val otherBudget = other.budget("group").also { it.sync() }
            val accounts =
                otherBudget.snapshot("2026-11-21").records.filter {
                    it.type == "account" &&
                        it.body["name"] == JsonPrimitive("Wallet")
                }
            assertEquals(listOf(wallet), accounts.map { it.id })
        }

    @Test
    fun aRuleBankSyncCantRunOnlyStopsTheAccountItMatches(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val client = client(server, TempBudgetFiles())
            client.signInWithPassword(url, "test", "Phone")
            val budget = client.budget("group")
            budget.sync()
            val checking =
                budget
                    .snapshot("2026-11-21")
                    .records
                    .first { it.type == "account" && it.body["name"] == JsonPrimitive("Checking") }
                    .id
            val savings = client.createBudgetAccount("group", "Savings", false, null, null)
            client.edit("group") {
                it.linkBankAccount(checking, "simpleFin", "SF-1", "org", "Credit Union")
                it.linkBankAccount(savings, "simpleFin", "SF-2", "org", "Credit Union")
                it.createRule(
                    Json
                        .parseToJsonElement(
                            """{"conditionsOp":"and","conditions":[{"field":"account","op":"is","value":"$checking"}],""" +
                                """"actions":[{"op":"set","field":"notes","value":"","options":{"template":"{{payee_name}}"}}]}""",
                        ).jsonObject,
                )
            }
            server.bankDownload =
                """{"status":"ok","data":{"transactions":{"all":[{"transactionId":"sf-1","booked":true,""" +
                """"date":"2026-11-19","payeeName":"Gym","transactionAmount":{"amount":"-45.50"}}]},"startingBalance":100000}}"""

            assertFails { client.runBudgetBankSync("group", null) }

            assertEquals(
                setOf("sf-1"),
                client.previewBudgetAccountStatementImport("group", savings, listOf("sf-1"), null).existingImportedIds,
            )
            assertEquals(
                emptySet(),
                client.previewBudgetAccountStatementImport("group", checking, listOf("sf-1"), null).existingImportedIds,
            )
        }

    @Test
    fun anUnreadableBankDownloadOnlyFailsItsOwnAccount(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val client = client(server, TempBudgetFiles())
            client.signInWithPassword(url, "test", "Phone")
            val budget = client.budget("group")
            budget.sync()
            val checking =
                budget
                    .snapshot("2026-11-21")
                    .records
                    .first { it.type == "account" && it.body["name"] == JsonPrimitive("Checking") }
                    .id
            val savings = client.createBudgetAccount("group", "Savings", false, null, null)
            client.edit("group") {
                it.linkBankAccount(checking, "simpleFin", "SF-1", "org", "Credit Union")
                it.linkBankAccount(savings, "goCardless", "GC-1", "gc-bank", "Big Bank")
            }
            server.bankDownload =
                """{"status":"ok","data":{"transactions":[{"transactionId":"sf-1","booked":true,"payeeName":"Gym","amount":"-45.50"}]}}"""
            server.bankRoutes["/gocardless/transactions"] =
                """{"status":"ok","data":{"transactions":{"all":[{"transactionId":"gc-1","booked":true,""" +
                """"date":"2026-11-19","payeeName":"Shop","transactionAmount":{"amount":"-12.00"}}]},"startingBalance":5000}}"""

            assertEquals("bank_sync_failed", assertFailsWith<TandemApiException> { client.runBudgetBankSync("group", null) }.errorCode)

            assertEquals(
                setOf("gc-1"),
                client.previewBudgetAccountStatementImport("group", savings, listOf("gc-1"), null).existingImportedIds,
            )
            val linked = client.budgetLinkedAccounts("group", null).associateBy { it.id }
            assertEquals("unreadable_transaction", linked.getValue(checking).failure)
            assertEquals(null, linked.getValue(savings).failure)

            server.bankRoutes["/gocardless/transactions"] =
                """{"status":"ok","data":{"transactions":[{"transactionId":"gc-2","booked":true,"date":"2026-11-20","amount":"-1.00"}]}}"""
            assertEquals("bank_sync_failed", assertFailsWith<TandemApiException> { client.runBudgetBankSync("group", null) }.errorCode)
            assertTrue(client.budgetLinkedAccounts("group", null).all { it.failure == "unreadable_transaction" })

            server.bankDownload =
                """{"status":"ok","data":{"transactions":[{"transactionId":"sf-1","booked":true,"date":"2026-11-19","payeeName":"Gym","amount":"-45.50"}]}}"""
            server.bankRoutes["/gocardless/transactions"] = """{"status":"ok","data":{"transactions":[]}}"""
            client.runBudgetBankSync("group", null)
            assertTrue(client.budgetLinkedAccounts("group", null).all { it.failure == null })
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
            assertEquals(32, client.budgetSecret("locked")?.size)
            assertNull(client.budgetSecret("group"))
            assertNull(client.planningTransport("locked"))
            server.extension = true
            val planning = client(server, TempBudgetFiles())
            assertNull(planning.planningTransport("group"))
            planning.signInWithPassword(url, "test", "Phone")
            assertTrue(planning.planningTransport("group") is ExtensionSyncClient)
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
            client.edit("group") { it.linkBankAccount(checking, "simpleFin", "SF-1", "org", "Credit Union") }
            server.bankDownload =
                """{"status":"ok","data":{"transactions":{"all":[{"transactionId":"sf-1","booked":true,""" +
                """"date":"2026-11-19","payeeName":"Gym",""" +
                """"transactionAmount":{"amount":"-45.50"}}]},"startingBalance":100000}}"""
            client.runBudgetBankSync("group", null)
            assertEquals(
                setOf("sf-1"),
                client.previewBudgetAccountStatementImport("group", checking, listOf("sf-1"), null).existingImportedIds,
            )
            server.bankDownload = """{"status":"ok","data":{"error_type":"ITEM_ERROR","error_code":"ITEM_LOGIN_REQUIRED"}}"""
            assertEquals("bank_sync_failed", assertFailsWith<TandemApiException> { client.runBudgetBankSync("group", null) }.errorCode)
            client.budgetLinkedAccounts("group", null).first { it.id == checking }.let { linked ->
                assertEquals("item_login_required", linked.failure)
                kotlin.test.assertNotNull(linked.lastSyncMillis)
            }
            val providers = client.bankProviderAccounts(SimpleFin)!!
            assertEquals(BankProviderAccount("SF-9", "Rewards Card", "Credit Union", "cu.example", "-42.10"), providers.first())
            assertEquals("org-1", providers[1].bankId)
            assertEquals("", providers[2].bankId)
            client.linkBudgetBankAccount("group", checking, SimpleFin, providers.first(), null)
            assertEquals("SF-9", client.budgetLinkedAccounts("group", null).first { it.id == checking }.providerAccountId)
            client.unlinkBudgetBankAccount("group", checking, null)
            assertEquals(0, client.budgetBankSyncInterval("group", null))
            client.setBudgetBankSyncInterval("group", 720, null)
            assertEquals(720, client.budgetBankSyncInterval("group", null))
            assertFalse(client.automaticBankSyncDue("group", null, System.currentTimeMillis(), null))
            client.linkBudgetBankAccount("group", checking, SimpleFin, providers.first(), null)
            assertTrue(client.automaticBankSyncDue("group", null, System.currentTimeMillis() + 2 * 86_400_000L, null))
            client.unlinkBudgetBankAccount("group", checking, null)
            val card = client.linkBudgetBankAccountToNewAccount("group", true, SimpleFin, providers.first(), null)
            assertEquals("SF-9", client.budgetLinkedAccounts("group", null).first { it.id == card }.providerAccountId)
            client.unlinkBudgetBankAccount("group", card, null)
            assertTrue(client.budgetLinkedAccounts("group", null).none { it.id == checking })
            server.simpleFinAccounts = """{"status":"ok","data":{"error_code":"INVALID_ACCESS_TOKEN"}}"""
            assertEquals("bank_sync_failed", assertFailsWith<TandemApiException> { client.bankProviderAccounts(SimpleFin) }.errorCode)
            server.simpleFinConfigured = false
            assertNull(client.bankProviderAccounts(SimpleFin))
            val statement = listOf(StatementImportItem("2026-11-20", -1_234, "corner shop", "first", "stmt-1"))
            assertEquals(0, client.previewBudgetAccountStatementImport("group", checking, listOf("stmt-1"), null).existingImportedIds.size)
            assertEquals(StatementImportSummary(1, 0, 0), client.importBudgetAccountTransactions("group", checking, statement, null))
            assertEquals(
                setOf("stmt-1"),
                client.previewBudgetAccountStatementImport("group", checking, listOf("stmt-1", "stmt-2"), null).existingImportedIds,
            )
            assertEquals(StatementImportSummary(0, 0, 0), client.importBudgetAccountTransactions("group", checking, statement, null))
            assertEquals(3, StatementImportPreview(setOf("a")).newRowCount(4))
            assertTrue(client.budgetDataHealth("group", null).isNotEmpty())
            assertTrue(
                client
                    .previewBudgetRule(
                        "group",
                        kotlinx.serialization.json.Json
                            .parseToJsonElement(
                                """{"conditionsOp":"or","conditions":[{"op":"gt","field":"amount","type":"number","value":-100000000}]}""",
                            ).jsonObject,
                        null,
                    ).isNotEmpty(),
            )
            assertTrue(client.exportBudgetTransactions("group", "2000-01-01", "2099-12-31", null).startsWith("Date,Account"))
            assertTrue(
                client
                    .budgetAccountMovements("group", listOf(checking), null)
                    .getValue(checking)
                    .all { it.amountMinor != 0L },
            )
            val history = client.budgetChangeHistory("group", null)
            val latest = history.changes.first()
            assertEquals(history.deviceNode, latest.node)
            assertTrue(client.undoBudgetChange("group", latest.id, null).reverted > 0)
            assertEquals(
                "change_not_found",
                assertFailsWith<TandemApiException> { client.undoBudgetChange("group", "missing", null) }.errorCode,
            )
            assertEquals(emptyList(), client.budgetTaggedPayments("group", "no-such-tag", "2000-01-01", null))
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
    fun linksBanksThroughTheBrowser(): Unit =
        runBlocking {
            val server = FakeActualServer()
            val client = client(server, TempBudgetFiles())
            client.signInWithPassword(url, "test", "Phone")
            server.bankRoutes["/gocardless/get-banks"] = """{"status":"ok","data":[{"id":"BANK_X","name":"Bank X"}]}"""
            val bank = client.bankInstitutions(GoCardless, "GB").single()
            assertEquals(BankInstitution("BANK_X", "Bank X", "GB"), bank)
            server.bankRoutes["/gocardless/create-web-token"] = """{"status":"ok","data":{"link":"https://bank/x","requisitionId":"req"}}"""
            assertEquals(BrowserLink("https://bank/x", "req"), client.startBankLink(GoCardless, bank))
            assertEquals("https://budget.example", server.lastOrigin)
            server.bankRoutes["/gocardless/get-accounts"] = """{"status":"ok","requisitionStatus":"CR"}"""
            assertEquals(BrowserLinkStatus.Pending, client.bankLinkStatus(GoCardless, "req"))

            server.bankRoutes["/gocardless/get-banks"] = """{"status":"ok","data":{"error_code":"GOCARDLESS_NOT_CONFIGURED"}}"""
            assertEquals(
                "bank_sync_failed",
                assertFailsWith<TandemApiException> { client.bankInstitutions(GoCardless, "GB") }.errorCode,
            )
            server.bankRoutes["/enablebanking/aspsps"] = """{"status":"ok","data":{"error":"bad"}}"""
            assertFailsWith<TandemApiException> { client.bankInstitutions(EnableBanking, "FI") }
            server.bankRoutes["/enablebanking/aspsps"] = """{"status":"ok","data":{"aspsps":[{"name":"Nordea","country":"FI"}]}}"""
            assertEquals("Nordea", client.bankInstitutions(EnableBanking, "FI").single().id)
            server.bankRoutes["/enablebanking/aspsps"] = """{"status":"ok"}"""
            assertTrue(client.bankInstitutions(EnableBanking, "FI").isEmpty())
            server.bankRoutes["/enablebanking/start-auth"] = """{"status":"ok","data":{"error_code":"INVALID_INPUT"}}"""
            assertFailsWith<TandemApiException> { client.startBankLink(EnableBanking, bank) }
            server.bankRoutes["/enablebanking/start-auth"] = """{"status":"ok","data":{"error":"nope"}}"""
            assertFailsWith<TandemApiException> { client.startBankLink(EnableBanking, bank) }
            server.bankRoutes["/enablebanking/start-auth"] = """{"status":"ok"}"""
            assertFailsWith<TandemApiException> { client.startBankLink(EnableBanking, bank) }
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
                "server-unavailable" to "server_unavailable",
                "rate-limited" to "rate_limited",
                "something-else" to "something_else",
            )) {
                assertEquals(expected, code(ActualServerException(reason)).errorCode)
            }
            assertFailsWith<CancellationException> { client.call { throw CancellationException("stop") } }
            assertTrue(TandemApiException("unknown").message!!.isNotBlank())
            assertEquals(JsonObject(emptyMap()), JsonObject(emptyMap()))
        }
}
