package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class ActualBudgetEditsTest {
    private val today = "2026-11-21"
    private val month = "2026-11"

    private class Fixture(
        val db: ActualDatabase,
        val edits: ActualBudgetEdits,
    ) {
        fun one(
            sql: String,
            vararg args: Any?,
        ) = db.first(sql, *args)

        fun id(
            table: String,
            name: String,
        ) = one("SELECT id FROM $table WHERE name = ? AND tombstone = 0", name)!!["id"] as String

        fun month(
            month: String,
            today: String = "2026-11-21",
        ) = ActualBudgetReader(db)
            .read(today)
            .records
            .first { it.type == "budget-month" && it.id == month }
            .body

        fun category(
            month: String,
            id: String,
        ) = month(
            month,
        ).getValue("categoryGroups").jsonArray.flatMap { it.jsonObject["categories"]!!.jsonArray }.map { it.jsonObject }.first {
            it.value("id") ==
                id
        }
    }

    private fun fixture(name: String = "parity-envelope"): Fixture {
        val entries = ZipArchive.read(checkNotNull(javaClass.getResource("/actual/$name/budget.zip")).readBytes())
        val file =
            File.createTempFile("edits", ".sqlite").apply {
                writeBytes(entries.getValue("db.sqlite"))
                deleteOnExit()
            }
        val db = ActualDatabase(BundledSQLiteDriver().open(file.path))
        val store = ActualMessageStore(db)
        val clock = store.loadClock()
        store.resetNode(clock)
        val server = SyncProtocol.decodeResponse(checkNotNull(javaClass.getResource("/actual/$name/sync-response.bin")).readBytes())
        runBlocking {
            ActualSyncEngine(
                store,
                { request -> SyncResponse(server.messages.filter { it.timestamp > request.since }, server.merkle) },
                "g",
                "f",
            ) {
                System.currentTimeMillis()
            }.fullSync(clock, null)
        }
        val writer = ActualWriter(store, clock, System::currentTimeMillis) { UUID.randomUUID().toString() }
        return Fixture(db, ActualBudgetEdits(writer, { today }))
    }

    private fun Fixture.rejects(
        message: String,
        block: ActualBudgetEdits.() -> Unit,
    ) = assertEquals(message, assertFailsWith<ActualEditException> { edits.block() }.message)

    @Test
    fun accountsFollowActual() {
        val f = fixture()
        val checking = f.id("accounts", "Checking")
        val savings = f.id("accounts", "Savings")
        val brokerage = f.id("accounts", "Brokerage")
        val wallet = f.edits.createAccount("Wallet", false, null)
        assertEquals(1L, f.one("SELECT count(*) AS n FROM payees WHERE transfer_acct = ?", wallet)!!["n"])
        f.db.execute("INSERT INTO account_groups (id, name, sort_order, tombstone) VALUES ('g1', 'Group', 1, 0)")
        val grouped = f.edits.createAccount("Grouped", true, "g1")
        assertEquals("g1", f.one("SELECT account_group_id FROM accounts WHERE id = ?", grouped)!!["account_group_id"])
        f.rejects("Choose an account group in this budget") { createAccount("X", false, "missing") }
        f.edits.renameAccount(wallet, "Pocket")
        f.rejects("Account does not exist in this budget") { renameAccount("missing", "x") }
        f.edits.moveAccount(wallet, checking)
        assertTrue((f.one("SELECT sort_order FROM accounts WHERE id = ?", wallet)!!["sort_order"] as Number).toDouble() < 16384.0)
        f.edits.moveAccount(wallet, null)
        f.rejects("Account does not exist in this budget") { moveAccount("missing", null) }
        f.rejects("Choose an account in this budget") { moveAccount(wallet, "missing") }
        f.rejects("Choose a different account position") { moveAccount(wallet, wallet) }
        f.rejects("Accounts can only be reordered within the same section") { moveAccount(wallet, brokerage) }
        f.db.execute("UPDATE accounts SET sort_order = 1 WHERE id = ?", checking)
        f.db.execute("UPDATE accounts SET sort_order = 2 WHERE id = ?", savings)
        f.edits.moveAccount(wallet, savings)
        assertEquals(16386.0, (f.one("SELECT sort_order FROM accounts WHERE id = ?", savings)!!["sort_order"] as Number).toDouble())

        f.rejects("Account does not exist in this budget") { closeAccount("missing", null, null) }
        f.rejects("Choose an account to move this balance before closing it") { closeAccount(checking, null, null) }
        f.rejects("Choose a different open account in this budget") { closeAccount(checking, checking, null) }
        f.rejects("Choose a different open account in this budget") { closeAccount(checking, "missing", null) }
        f.rejects("Choose a category in this budget") { closeAccount(checking, savings, "missing") }
        f.edits.closeAccount(wallet, null, null)
        assertEquals(1L, f.one("SELECT tombstone FROM accounts WHERE id = ?", wallet)!!["tombstone"])
        f.db.execute("UPDATE accounts SET bank = 'b1' WHERE id = ?", savings)
        f.edits.closeAccount(savings, checking, f.id("categories", "Groceries"))
        assertEquals(1L, f.one("SELECT closed FROM accounts WHERE id = ?", savings)!!["closed"])
        assertNull(f.one("SELECT bank FROM accounts WHERE id = ?", savings)!!["bank"])
        assertEquals(0L, f.edits.accountBalance(savings, "2100-01-01"))
        f.rejects("Choose a different open account in this budget") { closeAccount(checking, savings, null) }
        f.rejects("This account is already open") { reopenAccount(checking) }
        f.rejects("Account does not exist in this budget") { reopenAccount("missing") }
        f.edits.reopenAccount(savings)
        assertEquals(0L, f.one("SELECT closed FROM accounts WHERE id = ?", savings)!!["closed"])
        f.db.execute("UPDATE accounts SET closed = 1 WHERE id = ?", savings)
        f.edits.closeAccount(savings, null, null)
        val old = f.id("accounts", "Old wallet")
        f.edits.moveAccount(old, null)
        f.db.execute("UPDATE accounts SET tombstone = 1 WHERE id = ?", grouped)
    }

    @Test
    fun closeAccountUsesTheWholeBalanceLikeActual() {
        val f = fixture()
        val checking = f.id("accounts", "Checking")
        val wallet = f.edits.createAccount("Wallet", false, null)
        val card = f.edits.createAccount("Card", false, null)
        f.edits.transactions.batchUpdate(
            added =
                listOf(
                    linkedMapOf("id" to UUID.randomUUID().toString(), "account" to wallet, "amount" to 5000L, "date" to today),
                    linkedMapOf("id" to UUID.randomUUID().toString(), "account" to wallet, "amount" to -5000L, "date" to "2026-12-15"),
                    linkedMapOf("id" to UUID.randomUUID().toString(), "account" to card, "amount" to 2500L, "date" to "2026-12-15"),
                ),
        )
        assertEquals(0L, f.edits.accountBalance(card, today))
        f.rejects("Choose an account to move this balance before closing it") { closeAccount(card, null, null) }
        assertEquals(0L, f.one("SELECT closed FROM accounts WHERE id = ?", card)!!["closed"])
        f.edits.closeAccount(card, checking, null)
        assertEquals(1L, f.one("SELECT closed FROM accounts WHERE id = ?", card)!!["closed"])
        assertEquals(0L, f.edits.accountBalance(card, "2100-01-01"))
        f.edits.closeAccount(wallet, null, null)
        assertEquals(1L, f.one("SELECT closed FROM accounts WHERE id = ?", wallet)!!["closed"])
        assertEquals(5000L, f.edits.accountBalance(wallet, today))
    }

    @Test
    fun payeesFollowActual() {
        val f = fixture()
        val payee = f.edits.createPayee("Bakery")
        f.rejects("A payee with this name already exists") { createPayee(" bakery ") }
        f.edits.renamePayee(payee, " Bread shop ")
        assertEquals("Bread shop", f.one("SELECT name FROM payees WHERE id = ?", payee)!!["name"])
        f.rejects("Payee names must be between 1 and 100 characters") { renamePayee(payee, " ") }
        f.rejects("Choose a payee in this budget") { renamePayee("missing", "x") }
        val other = f.edits.createPayee("Cafe")
        f.rejects("Choose a different payee to merge into") { mergePayee(other, other) }
        f.rejects("Choose two payees in this budget") { mergePayee(other, "missing") }
        val alias = f.edits.createPayee("Alias")
        f.edits.mergePayee(alias, other)
        f.edits.mergePayee(other, payee)
        assertEquals(payee, f.one("SELECT targetId FROM payee_mapping WHERE id = ?", alias)!!["targetId"])
        f.rejects("Choose a payee in this budget") { deletePayee("missing") }
        val gone = f.edits.createPayee("Gone")
        f.edits.deletePayee(gone)

        val groceries = f.id("categories", "Groceries")
        val dining = f.id("categories", "Dining")
        f.rejects("Choose a category or clear the default") { setPayeeDefaultCategory(payee, " ") }
        f.rejects("Choose a regular payee in this budget") { setPayeeDefaultCategory("missing", null) }
        f.rejects("Choose an active category in this budget") { setPayeeDefaultCategory(payee, "missing") }
        f.edits.setPayeeDefaultCategory(payee, null)
        f.edits.setPayeeDefaultCategory(payee, groceries)
        f.edits.setPayeeDefaultCategory(payee, dining)

        fun rules() = f.db.query("SELECT actions FROM rules WHERE tombstone = 0 AND conditions LIKE ?", "%$payee%")
        assertTrue(rules().single()["actions"].toString().contains(dining))
        f.edits.setPayeeDefaultCategory(payee, null)
        assertTrue(rules().isEmpty())
        val ruleId =
            f.edits.createRule(
                buildJsonObject {
                    put("conditionsOp", "and")
                    put(
                        "conditions",
                        JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("field", "payee")
                                    put("op", "is")
                                    put("value", payee)
                                },
                            ),
                        ),
                    )
                    put(
                        "actions",
                        JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("field", "category")
                                    put("op", "set")
                                    put("value", groceries)
                                },
                                buildJsonObject {
                                    put("field", "notes")
                                    put("op", "set")
                                    put("value", "x")
                                },
                            ),
                        ),
                    )
                },
            )
        f.edits.setPayeeDefaultCategory(payee, dining)
        assertTrue(rules().single()["actions"].toString().contains("notes"))
        f.edits.setPayeeDefaultCategory(payee, null)
        assertFalse(rules().single()["actions"].toString().contains(groceries))
        f.edits.updateRule(
            ruleId,
            buildJsonObject {
                put(
                    "conditions",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("field", "payee")
                                put("op", "is")
                                put("value", payee)
                            },
                        ),
                    ),
                )
                put(
                    "actions",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("field", "category")
                                put("op", "set")
                                put("value", groceries)
                            },
                            buildJsonObject {
                                put("field", "category")
                                put("op", "set")
                                put("value", dining)
                            },
                        ),
                    ),
                )
            },
        )
        f.rejects("This payee has multiple category actions. Edit the rule in Actual first.") { setPayeeDefaultCategory(payee, dining) }
        f.edits.createRule(
            buildJsonObject {
                put("stage", "default")
                put(
                    "conditions",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("field", "payee")
                                put("op", "is")
                                put("value", payee)
                            },
                        ),
                    ),
                )
                put(
                    "actions",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("field", "category")
                                put("op", "set")
                                put("value", dining)
                            },
                        ),
                    ),
                )
            },
        )
        f.rejects("This payee has multiple category rules. Edit them in Actual first.") { setPayeeDefaultCategory(payee, dining) }
    }

    @Test
    fun rulesFollowActual() {
        val f = fixture()

        fun rule(
            stage: String? = null,
            op: String? = "and",
            conditions: JsonArray? =
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("field", "notes")
                            put("op", "contains")
                            put("value", "x")
                        },
                    ),
                ),
            actions: JsonArray? =
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("field", "cleared")
                            put("op", "set")
                            put("value", true)
                        },
                    ),
                ),
        ) = JsonObject(
            buildMap {
                if (stage != null) put("stage", JsonPrimitive(stage))
                if (op != null) put("conditionsOp", JsonPrimitive(op))
                if (conditions != null) put("conditions", conditions)
                if (actions != null) put("actions", actions)
            },
        )
        val id = f.edits.createRule(rule(stage = "post", op = null))
        f.edits.updateRule(id, rule(stage = "pre"))
        assertEquals("pre", f.one("SELECT stage FROM rules WHERE id = ?", id)!!["stage"])
        f.rejects("Invalid rule stage: later") { createRule(rule(stage = "later")) }
        f.rejects("Invalid rule conditionsOp: xor") { createRule(rule(op = "xor")) }
        f.rejects("A rule needs conditions") { createRule(rule(conditions = null)) }
        f.rejects("A rule needs actions") { createRule(rule(actions = null)) }
        f.rejects("This rule has an invalid condition or action") {
            createRule(rule(actions = JsonArray(listOf(buildJsonObject { put("op", "explode") }))))
        }
        f.rejects("Rule does not exist in this budget") { updateRule("missing", rule()) }
        f.rejects("A rule needs conditions") { updateRule(id, rule(conditions = null)) }
        f.rejects("A rule needs actions") { updateRule(id, rule(actions = null)) }
        val wire =
            kotlinx.serialization.json.Json
                .parseToJsonElement(
                    """{"conditionsOp":"and","conditions":[{"field":"notes","op":"contains","text":"coffee"},""" +
                        """{"field":"amount","op":"gt","amountMinor":500,"outflow":true}],""" +
                        """"actions":[{"field":"cleared","op":"set","cleared":true},{"field":"notes","op":"append-notes","text":" #coffee"}]}""",
                ).jsonObject
        val fromWire = f.edits.createRule(wire)
        val stored = f.one("SELECT conditions, actions FROM rules WHERE id = ?", fromWire)!!
        assertTrue("\"value\":\"coffee\"" in (stored["conditions"] as String))
        assertTrue("\"outflow\":true" in (stored["conditions"] as String))
        assertTrue("\"value\":true" in (stored["actions"] as String))
        assertEquals(RuleWire.toActual(JsonObject(emptyMap())), JsonObject(emptyMap()))
        assertTrue(f.edits.deleteRule(fromWire))
        assertTrue(f.edits.deleteRule(id))
        val scheduled = f.one("SELECT rule FROM schedules WHERE tombstone = 0")!!["rule"] as String
        assertFalse(f.edits.deleteRule(scheduled))
    }

    @Test
    fun categoriesFollowActual() {
        val f = fixture()
        val bills = f.id("category_groups", "Bills")
        val everyday = f.id("category_groups", "Everyday")
        val archive = f.id("category_groups", "Archive")
        val income = f.id("category_groups", "Income")
        val groceries = f.id("categories", "Groceries")
        val dining = f.id("categories", "Dining")
        val salary = f.id("categories", "Income")
        val group = f.edits.createCategoryGroup(" Travel ", false)
        f.rejects("Category group names must be between 1 and 100 characters") { createCategoryGroup(" ", false) }
        f.rejects("A category group with this name already exists") { createCategoryGroup("travel", false) }
        val flights = f.edits.createCategory(group, "Flights")
        val hotels = f.edits.createCategory(group, "Hotels")
        assertTrue((f.one("SELECT sort_order FROM categories WHERE id = ?", hotels)!!["sort_order"] as Number).toDouble() < 16384.0)
        f.rejects("Choose an active category group") { createCategory(archive, "x") }
        f.rejects("A category with this name already exists in that group") { createCategory(group, " flights ") }
        f.edits.renameCategory(flights, "Air")
        f.edits.renameCategory(flights, "Air")
        f.rejects("Category does not exist in this budget") { renameCategory("missing", "x") }

        f.rejects("Category group does not exist in this budget") { manageCategoryGroup("missing", "hide", null, null) }
        f.edits.manageCategoryGroup(group, "rename", " Trips ", null)
        f.rejects("Category group names must be between 1 and 100 characters") { manageCategoryGroup(group, "rename", null, null) }
        f.rejects("A category group with this name already exists") { manageCategoryGroup(group, "rename", "bills", null) }
        f.edits.manageCategoryGroup(group, "hide", null, null)
        f.edits.manageCategoryGroup(group, "show", null, null)
        f.edits.manageCategoryGroup(group, "move", null, bills)
        f.rejects("Choose a different category group position") { manageCategoryGroup(group, "move", null, group) }
        f.rejects("Choose a category group in this budget") { manageCategoryGroup(group, "move", null, "missing") }

        f.rejects("Category does not exist in this budget") { manageCategory("missing", "hide", null, null) }
        f.edits.manageCategory(flights, "hide", null, null)
        f.edits.manageCategory(flights, "show", null, null)
        f.edits.manageCategory(hotels, "move", flights, null)
        f.edits.manageCategory(hotels, "move", null, group)
        f.rejects("Choose a different category position") { manageCategory(hotels, "move", hotels, null) }
        f.rejects("Choose a category in the same group") { manageCategory(hotels, "move", groceries, null) }
        f.rejects("Choose a visible category group in this budget") { manageCategory(hotels, "move", null, archive) }
        f.rejects("Income categories must stay in an income group") { manageCategory(hotels, "move", null, income) }
        f.rejects(
            "A category moved to another group starts at the end of that group",
        ) { manageCategory(hotels, "move", groceries, everyday) }
        f.edits.manageCategory(hotels, "move", null, everyday)
        assertEquals(everyday, f.one("SELECT cat_group FROM categories WHERE id = ?", hotels)!!["cat_group"])

        f.edits.setBudgetAmount(flights, month, 700)
        f.edits.setBudgetAmount(dining, month, 10_500)
        f.rejects("Choose a different category in this budget") { deleteCategory(flights, flights) }
        f.rejects("Choose a different category in this budget") { deleteCategory(flights, "missing") }
        f.rejects("Transactions can only move to a category of the same type") { deleteCategory(flights, salary) }
        f.edits.deleteCategory("missing", null)
        f.edits.deleteCategory(flights, dining)
        assertEquals(11_200L, f.category(month, dining).value("budgeted"))
        assertEquals(dining, f.one("SELECT transferId FROM category_mapping WHERE id = ?", flights)!!["transferId"])
        f.edits.deleteCategory(hotels, null)

        val doomed = f.edits.createCategoryGroup("Doomed", false)
        val inside = f.edits.createCategory(doomed, "Inside")
        f.rejects(
            "Choose a visible category outside this group with the same income type",
        ) { manageCategoryGroup(doomed, "delete", null, inside) }
        f.rejects(
            "Choose a visible category outside this group with the same income type",
        ) { manageCategoryGroup(doomed, "delete", null, salary) }
        f.rejects("Choose a visible category outside this group with the same income type") {
            manageCategoryGroup(doomed, "delete", null, "missing")
        }
        f.edits.manageCategoryGroup(doomed, "delete", null, groceries)
        assertEquals(1L, f.one("SELECT tombstone FROM categories WHERE id = ?", inside)!!["tombstone"])
        f.edits.manageCategoryGroup(group, "delete", null, null)
        assertEquals(1L, f.one("SELECT tombstone FROM category_groups WHERE id = ?", group)!!["tombstone"])
    }

    @Test
    fun budgetPlanningFollowsActual() {
        val f = fixture()
        val groceries = f.id("categories", "Groceries")
        val dining = f.id("categories", "Dining")
        val salary = f.id("categories", "Salary")
        val income = f.id("categories", "Income")
        f.edits.setBudgetAmount(groceries, month, 50_000)
        f.edits.setBudgetAmount(f.id("categories", "Old category"), month, 1)
        assertEquals(50_000L, f.category(month, groceries).value("budgeted"))
        f.rejects("Enter a budget amount up to 1,000,000,000 major units") { setBudgetAmount(groceries, month, -1) }
        f.rejects("Category does not exist in this budget") { setBudgetAmount("missing", month, 1) }
        f.rejects("No budget exists for month: 1990-01") { setBudgetAmount(groceries, "1990-01", 1) }

        f.edits.setBudgetCarryover(dining, month, true)
        assertEquals(true, f.category("2027-03", dining).value("carryover"))
        f.edits.setBudgetCarryover(dining, month, false)
        f.rejects("Choose a category in this budget month") { setBudgetCarryover("missing", month, true) }
        f.rejects("budget-set-carryover: category is an income category") { setBudgetCarryover(income, month, true) }
        f.rejects("No budget exists for month: 1990-01") { setBudgetCarryover(dining, "1990-01", true) }

        f.edits.copyBudgetMonth("2026-12")
        assertEquals(50_000L, f.category("2026-12", groceries).value("budgeted"))
        f.rejects("Choose a valid budget month") { copyBudgetMonth("2026-13") }
        f.rejects("Choose a valid budget month") { copyBudgetMonth("0001-01") }

        f.edits.applyBudgetTemplate("2027-01", listOf(groceries to 1_000L, dining to 2_000L))
        assertEquals(2_000L, f.category("2027-01", dining).value("budgeted"))
        f.rejects("Choose a valid budget month") { applyBudgetTemplate("bad", listOf(groceries to 1L)) }
        f.rejects("Choose a template with 1 to 500 categories") { applyBudgetTemplate(month, emptyList()) }
        f.rejects(
            "The template contains an invalid category amount",
        ) { applyBudgetTemplate(month, listOf(groceries to 1L, groceries to 2L)) }
        f.rejects("The template contains an invalid category amount") { applyBudgetTemplate(month, listOf(" " to 1L)) }
        f.rejects("The template contains an invalid category amount") { applyBudgetTemplate(month, listOf(groceries to -1L)) }
        f.rejects("A template category is no longer active in this month") { applyBudgetTemplate(month, listOf("missing" to 1L)) }

        val ready = f.month(month).value("toBudget") as Long
        f.edits.holdBudgetForNextMonth(month, 1_000)
        assertEquals(1_000L, f.month(month).value("forNextMonth"))
        f.edits.holdBudgetForNextMonth(month, 1_000)
        f.edits.holdBudgetForNextMonth(month, 2_000)
        assertEquals(2_000L, f.month(month).value("forNextMonth"))
        f.rejects("The hold cannot exceed the amount ready to assign") { holdBudgetForNextMonth(month, ready + 5_000) }
        f.rejects("Enter a positive amount to hold for next month") { holdBudgetForNextMonth(month, 0) }
        f.rejects("Choose a valid budget month") { holdBudgetForNextMonth("x", 1) }
        f.edits.resetBudgetHold(month)
        assertEquals(0L, f.month(month).value("forNextMonth"))
        f.rejects("Choose a valid budget month") { resetBudgetHold("x") }

        f.edits.setBudgetAmount(dining, month, 5_000)
        f.edits.setBudgetAmount(groceries, month, 500_000)
        f.edits.moveMoney(month, groceries, dining, 500)
        assertEquals(5_500L, f.category(month, dining).value("budgeted"))
        f.rejects("Enter a positive move amount up to 1,000,000,000 major units") { moveMoney(month, groceries, dining, 0) }
        f.rejects("Choose two different categories") { moveMoney(month, dining, dining, 1) }
        f.rejects("Choose categories in this budget month") { moveMoney(month, "missing", dining, 1) }
        f.rejects("Move money between categories of the same type") { moveMoney(month, salary, income, 1) }
        f.rejects("The source category does not have enough available money") { moveMoney(month, dining, groceries, 999_999_999) }
        f.rejects("No budget exists for month: 1990-01") { moveMoney("1990-01", dining, groceries, 1) }

        val tracking = fixture("parity-tracking")
        val trackingGroceries = tracking.id("categories", "Groceries")
        tracking.edits.setBudgetAmount(trackingGroceries, month, 7)
        assertEquals(7L, tracking.category(month, trackingGroceries).value("budgeted"))
        tracking.rejects("Hold for next month is only available in an envelope budget") { holdBudgetForNextMonth(month, 1) }
        tracking.rejects("Money can only be moved in an envelope budget") {
            moveMoney(month, trackingGroceries, tracking.id("categories", "Dining"), 1)
        }
        tracking.db.execute("DELETE FROM transactions")
        tracking.edits.setBudgetAmount(trackingGroceries, month, 8)
    }

    @Test
    fun theLastIncomeGroupCannotBeDeleted() {
        val f = fixture()
        val income = f.id("category_groups", "Income")
        f.rejects("A budget needs at least one income category group") { manageCategoryGroup(income, "delete", null, null) }
        assertEquals(0L, f.one("SELECT tombstone FROM category_groups WHERE id = ?", income)!!["tombstone"])

        val extra = f.edits.createCategoryGroup("Side income", true)
        f.edits.manageCategoryGroup(income, "delete", null, null)
        assertEquals(1L, f.one("SELECT tombstone FROM category_groups WHERE id = ?", income)!!["tombstone"])
        f.rejects("A budget needs at least one income category group") { manageCategoryGroup(extra, "delete", null, null) }
    }

    @Test
    fun budgetsWithoutAnIncomeGroupStillOpen() {
        for (name in listOf("parity-envelope", "parity-tracking")) {
            val f = fixture(name)
            f.db.execute("UPDATE category_groups SET tombstone = 1 WHERE is_income = 1")
            f.db.execute("UPDATE categories SET tombstone = 1 WHERE is_income = 1")
            assertEquals(0L, f.month(month).value("totalIncome"))
        }
    }
}
