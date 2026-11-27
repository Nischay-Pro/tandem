package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class ActualRuleDiagnosticsTest {
    private class Budget(
        val db: ActualDatabase,
        val writer: ActualWriter,
    ) {
        val market = id("SELECT id FROM payees WHERE name = 'Corner Market'")
        val cafe = id("SELECT id FROM payees WHERE name = 'Cafe'")
        val income = id("SELECT id FROM categories WHERE name = 'Income'")
        val group = id("SELECT id FROM category_groups")
        val account = id("SELECT id FROM accounts")

        private fun id(sql: String) = db.first(sql)!!["id"] as String

        fun rule(
            id: String,
            conditions: String,
            actions: String = """[{"op":"set","field":"notes","value":"x"}]""",
            op: String = "and",
            stage: String? = null,
        ) = db.execute(
            "INSERT INTO rules (id, stage, conditions_op, conditions, actions, tombstone) VALUES (?, ?, ?, ?, ?, 0)",
            id,
            stage,
            op,
            conditions,
            actions,
        )

        fun schedule(rule: String) = db.execute("INSERT INTO schedules (id, rule, tombstone) VALUES (?, ?, 0)", "schedule-$rule", rule)

        fun findings() = ActualRuleDiagnostics(db).check()

        fun of(kind: RuleFindingKind) = findings().filter { it.kind == kind }
    }

    private fun budget(): Budget {
        val resource = checkNotNull(javaClass.getResource("/actual/import-parity/budget.zip")).readBytes()
        val file = File.createTempFile("rule-diagnostics", ".sqlite").apply { writeBytes(ZipArchive.read(resource).getValue("db.sqlite")) }
        file.deleteOnExit()
        val db = ActualDatabase(BundledSQLiteDriver().open(file.path))
        val store = ActualMessageStore(db)
        return Budget(db, ActualWriter(store, store.loadClock(), System::currentTimeMillis) { UUID.randomUUID().toString() })
    }

    private fun cond(
        field: String,
        op: String,
        value: String,
        options: String? = null,
    ) = """{"op":"$op","field":"$field","value":$value${options?.let { ""","options":$it""" } ?: ""}}"""

    private fun all(vararg conditions: String) = conditions.joinToString(",", "[", "]")

    @Test
    fun findsReferencesToMissingPayeesCategoriesAccountsAndGroups() {
        val b = budget()
        b.db.execute("UPDATE payees SET tombstone = 1 WHERE id = ?", b.cafe)
        b.rule("missing-condition", all(cond("description", "is", "\"gone\"")))
        b.rule("missing-cafe", all(cond("description", "notOneOf", "[\"${b.cafe}\"]")))
        b.rule(
            "missing-action",
            all(cond("acct", "oneOf", "[\"${b.account}\",\"gone\"]")),
            """[{"op":"set","field":"category","value":"gone"}]""",
        )
        b.rule("missing-group", all(cond("category_group", "isNot", "\"gone\""), cond("category_group", "contains", "\"x\"")))
        b.rule(
            "present",
            all(cond("description", "is", "\"${b.market}\""), cond("category_group", "is", "\"${b.group}\"")),
            """[{"op":"set","field":"category","value":"${b.income}"},{"op":"set","field":"category","value":null}]""",
        )
        val found = b.of(RuleFindingKind.MISSING_REFERENCE).associate { it.ruleId to (it.severity to it.fields) }
        assertEquals(
            mapOf(
                "missing-action" to (RuleSeverity.PROBLEM to listOf("account", "category")),
                "missing-cafe" to (RuleSeverity.WARNING to listOf("payee")),
                "missing-condition" to (RuleSeverity.WARNING to listOf("payee")),
                "missing-group" to (RuleSeverity.WARNING to listOf("category_group")),
            ),
            found,
        )
    }

    @Test
    fun findsAllRulesWhoseConditionsCannotHoldTogether() {
        val b = budget()
        val (m, c) = "\"${b.market}\"" to "\"${b.cafe}\""
        val contradictory =
            mapOf(
                "c-is-is" to all(cond("description", "is", m), cond("description", "is", c)),
                "c-is-isnot" to all(cond("description", "isNot", m), cond("description", "is", m)),
                "c-is-oneof" to all(cond("description", "is", m), cond("description", "oneOf", "[$c]")),
                "c-is-notoneof" to all(cond("description", "is", m), cond("description", "notOneOf", "[$m]")),
                "c-oneof-oneof" to all(cond("description", "oneOf", "[$m]"), cond("description", "oneOf", "[$c]")),
                "c-oneof-notoneof" to all(cond("description", "oneOf", "[$m,$c]"), cond("description", "notOneOf", "[$c,$m]")),
                "c-text-contains" to
                    all(cond("imported_description", "is", "\"Coffee Shop\""), cond("imported_description", "contains", "\"tea\"")),
                "c-text-excludes" to
                    all(cond("imported_description", "doesNotContain", "\"COF\""), cond("imported_description", "is", "\"coffee\"")),
                "c-contains-excludes" to all(cond("notes", "contains", "\"coffee\""), cond("notes", "doesNotContain", "\"fee\"")),
                "c-gt-lt" to all(cond("amount", "gt", "100"), cond("amount", "lt", "50")),
                "c-between-gte" to all(cond("amount", "isbetween", """{"num1":20,"num2":10}"""), cond("amount", "gte", "21")),
                "c-is-is-number" to all(cond("amount", "is", "5"), cond("amount", "is", "6")),
                "c-approx-lt" to all(cond("amount", "isapprox", "100"), cond("amount", "lt", "50")),
                "c-open-edge" to all(cond("amount", "gt", "100"), cond("amount", "lte", "100")),
                "c-closed-open" to all(cond("amount", "lt", "100"), cond("amount", "gte", "100")),
                "c-day-after" to all(cond("date", "is", "\"2026-11-02\""), cond("date", "gt", "\"2026-12-01\"")),
                "c-year-month" to all(cond("date", "is", "\"2025\""), cond("date", "is", "\"2026-11\"")),
                "c-cleared" to all(cond("cleared", "is", "true"), cond("cleared", "is", "false")),
            )
        val consistent =
            mapOf(
                "ok-point" to all(cond("amount", "gte", "100"), cond("amount", "lte", "100")),
                "ok-day-month" to all(cond("date", "is", "\"2026-11-02\""), cond("date", "is", "\"2026-11\"")),
                "ok-month-year" to all(cond("date", "is", "\"2026-10\""), cond("date", "is", "\"2026\"")),
                "ok-recur" to
                    all(
                        cond("date", "is", """{"start":"2026-01-01","frequency":"monthly","interval":1}"""),
                        cond("date", "is", "\"2026-11\""),
                    ),
                "ok-approx-date" to all(cond("date", "isapprox", "\"2026-11-02\""), cond("date", "gte", "\"2026-11-01\"")),
                "ok-short-date" to all(cond("date", "gt", "\"2026-11\""), cond("date", "lt", "\"2026-10-01\"")),
                "ok-flow" to
                    all(cond("amount", "gt", "100", """{"outflow":true}"""), cond("amount", "lt", "50", """{"inflow":true}""")),
                "ok-fields" to all(cond("description", "is", m), cond("acct", "is", "\"${b.account}\"")),
                "ok-isnot" to all(cond("description", "isNot", m), cond("description", "isNot", c)),
                "ok-number-isnot" to all(cond("amount", "is", "5"), cond("amount", "isNot", "6")),
                "ok-id-contains" to all(cond("description", "is", m), cond("description", "contains", "\"zz\"")),
            )
        (contradictory + consistent).forEach { (id, conditions) -> b.rule(id, conditions) }
        b.rule("ok-or", all(cond("description", "is", m), cond("description", "is", c)), op = "or")
        val found = b.of(RuleFindingKind.CONTRADICTORY)
        assertEquals(contradictory.keys, found.map { it.ruleId }.toSet())
        assertTrue(found.all { it.severity == RuleSeverity.PROBLEM })
        assertEquals(listOf("payee"), found.first { it.ruleId == "c-is-is" }.fields)
    }

    @Test
    fun findsRulesWithoutConditionsOrActionsAndActionsThatChangeNothing() {
        val b = budget()
        val m = "\"${b.market}\""
        b.rule("no-conditions", "[]")
        b.rule("no-actions", all(cond("description", "is", m)), "[]")
        b.rule("same-payee", all(cond("description", "is", m)), """[{"op":"set","field":"description","value":$m}]""")
        b.rule(
            "empty-note",
            all(cond("description", "is", m)),
            """[{"op":"append-notes","value":""},{"op":"set","field":"category","value":"${b.income}"}]""",
        )
        b.rule(
            "set-twice",
            all(cond("description", "is", m)),
            """[{"op":"set","field":"category","value":"${b.income}"},{"op":"set","field":"category","value":null},""" +
                """{"op":"prepend-notes","value":"a"},{"op":"link-schedule","value":"s"}]""",
        )
        b.rule(
            "or-payee",
            all(cond("description", "is", m), cond("description", "is", "\"${b.cafe}\"")),
            """[{"op":"set","field":"description","value":$m}]""",
            op = "or",
        )
        b.rule(
            "formula",
            all(cond("notes", "is", "\"a\"")),
            """[{"op":"set","field":"notes","value":"a","options":{"formula":"=1"}}]""",
        )
        val found = b.findings().filter { it.ruleId != "formula" && it.kind != RuleFindingKind.SHADOWED }
        assertEquals(
            listOf(
                Triple(RuleFindingKind.NO_CONDITIONS, RuleSeverity.WARNING, "no-conditions"),
                Triple(RuleFindingKind.NO_ACTIONS, RuleSeverity.WARNING, "no-actions"),
                Triple(RuleFindingKind.NO_OP_ACTIONS, RuleSeverity.WARNING, "same-payee"),
                Triple(RuleFindingKind.NO_OP_ACTIONS, RuleSeverity.SUGGESTION, "empty-note"),
                Triple(RuleFindingKind.NO_OP_ACTIONS, RuleSeverity.SUGGESTION, "set-twice"),
            ),
            found.map { Triple(it.kind, it.severity, it.ruleId) },
        )
        assertEquals(listOf("notes"), found.first { it.ruleId == "empty-note" }.fields)
        assertEquals(listOf("category"), found.first { it.ruleId == "set-twice" }.fields)
        assertTrue(b.findings().none { it.ruleId == "formula" })
    }

    @Test
    fun findsRulesWhoseEveryChangeALaterRuleInTheStageOverwrites() {
        val b = budget()
        val (m, c) = "\"${b.market}\"" to "\"${b.cafe}\""
        val category = """[{"op":"set","field":"category","value":"${b.income}"}]"""
        b.rule("a-narrow", all(cond("description", "is", m), cond("amount", "gt", "100")), category)
        b.rule("a-wide", all(cond("description", "is", m)), """[{"op":"set","field":"category","value":null}]""")
        b.rule("b-first", all(cond("notes", "is", "\"x\"")), category)
        b.rule("b-second", all(cond("notes", "is", "\"X\"")), """[{"op":"set","field":"category","value":null}]""")
        b.rule("c-1-or", all(cond("amount", "gt", "100"), cond("amount", "gt", "200")), category, op = "or")
        b.rule("c-2-and", all(cond("amount", "gt", "50"), cond("amount", "gt", "0")), category)
        b.rule("d-and", all(cond("description", "is", c), cond("notes", "contains", "\"x\"")), category)
        b.rule("d-or", all(cond("description", "is", c), cond("description", "is", m)), category, op = "or")
        b.rule(
            "e-ors",
            all(cond("imported_description", "contains", "\"ab\""), cond("imported_description", "contains", "\"abc\"")),
            category,
            op = "or",
        )
        b.rule(
            "e-ors-wide",
            all(cond("imported_description", "contains", "\"a\""), cond("imported_description", "is", "\"zz\"")),
            category,
            op = "or",
        )
        b.rule(
            "f-renames",
            all(cond("description", "is", c), cond("amount", "lt", "-5")),
            """[{"op":"set","field":"description","value":$m},{"op":"set","field":"category","value":null}]""",
            stage = "post",
        )
        b.rule(
            "f-after",
            all(cond("description", "is", c)),
            """[{"op":"set","field":"description","value":$c},{"op":"set","field":"category","value":"${b.income}"}]""",
            stage = "post",
        )
        b.rule("g-pre", all(cond("notes", "is", "\"pre\"")), category, stage = "pre")
        b.rule("g-default", all(cond("notes", "is", "\"pre\"")), """[{"op":"set","field":"category","value":null}]""")
        b.rule("h-scheduled-later", all(cond("notes", "is", "\"sched\"")), category)
        b.rule("h-early", all(cond("notes", "is", "\"sched\""), cond("amount", "lt", "0")), category)
        b.schedule("h-scheduled-later")
        val found = b.of(RuleFindingKind.SHADOWED).associate { it.ruleId to it.related.single() }
        assertEquals(
            mapOf(
                "a-narrow" to "a-wide",
                "a-wide" to "d-or",
                "b-first" to "b-second",
                "c-1-or" to "c-2-and",
                "d-and" to "d-or",
                "e-ors" to "e-ors-wide",
            ),
            found,
        )
        assertEquals(listOf("category"), b.of(RuleFindingKind.SHADOWED).first().fields)
    }

    @Test
    fun findsExactAndNearDuplicatesAndMergesThemThroughRuleEdits() {
        val b = budget()
        val m = "\"${b.market}\""
        val category = """[{"op":"set","field":"category","value":"${b.income}"}]"""
        b.rule("dup-1", all(cond("description", "is", m), cond("amount", "lt", "0")), category)
        b.rule("dup-2", all(cond("amount", "lt", "0"), cond("description", "is", m)), category)
        b.rule("dup-3", all(cond("description", "is", m), cond("amount", "lt", "0")), category, op = "or")
        b.rule("one-or", all(cond("description", "is", m)), category, op = "or")
        b.rule("one-and", all(cond("description", "is", m)), category)
        b.rule("near-coffee", all(cond("imported_description", "is", "\"Coffee\"")), category)
        b.rule("near-tea", all(cond("imported_description", "oneOf", """["Tea","coffee"]""")), category)
        b.rule("near-juice", all(cond("imported_description", "is", "\"Juice\"")), category)
        b.rule("note-a", all(cond("notes", "is", "\"a\"")), category)
        b.rule("note-b", all(cond("notes", "is", "\"b\"")), category)
        b.rule("sched-a", all(cond("imported_description", "is", "\"Milk\"")), category)
        b.schedule("sched-a")
        val exact = b.of(RuleFindingKind.DUPLICATE).map { listOf(it.ruleId) + it.related }
        assertEquals(listOf(listOf("dup-1", "dup-2"), listOf("one-and", "one-or")), exact)
        val near = b.of(RuleFindingKind.NEAR_DUPLICATE).single()
        assertEquals("near-tea", near.ruleId)
        assertEquals(listOf("near-coffee", "near-juice"), near.related)
        assertEquals(RuleSeverity.SUGGESTION, near.severity)
        assertEquals(
            listOf("Tea", "coffee", "Juice"),
            near.merged!!["conditions"]!!
                .jsonArray
                .single()
                .jsonObject["value"]!!
                .jsonArray
                .map { (it as JsonPrimitive).content },
        )

        val edits = ActualBudgetEdits(b.writer, { "2026-11-27" })
        edits.updateRule(near.ruleId, near.merged!!)
        near.related.forEach { assertTrue(edits.deleteRule(it)) }
        assertTrue(b.of(RuleFindingKind.NEAR_DUPLICATE).isEmpty())
        val stored = b.db.first("SELECT conditions FROM rules WHERE id = 'near-tea'")!!["conditions"] as String
        assertEquals(JsonPrimitive("oneOf"), (Json.parseToJsonElement(stored) as JsonArray).single().jsonObject["op"])
    }

    @Test
    fun flagsRulesMatchingALargeShareOfRecentTransactions() {
        val b = budget()
        b.rule("spending", all(cond("amount", "lt", "0")))
        b.rule("spending-scheduled", all(cond("amount", "lt", "0")), """[{"op":"set","field":"notes","value":"y"}]""")
        b.schedule("spending-scheduled")
        b.rule("rare", all(cond("amount", "is", "-1")))
        assertTrue(b.of(RuleFindingKind.BROAD).isEmpty())
        repeat(30) { index ->
            b.db.execute(
                "INSERT INTO transactions (id, acct, amount, date, isParent, isChild, tombstone, cleared, sort_order) " +
                    "VALUES (?, ?, ?, 20261120, 0, 0, 0, 1, ?)",
                "broad-$index",
                b.account,
                -100L - index,
                index,
            )
        }
        val broad = b.of(RuleFindingKind.BROAD).single()
        assertEquals("spending", broad.ruleId)
        assertEquals(35, broad.checked)
        assertTrue(broad.matched >= 30)
    }

    @Test
    fun sameRulesGiveTheSameFindingsInTheSameOrder() {
        val rules =
            listOf(
                Triple("z-dup", all(cond("notes", "is", "\"n\"")), """[{"op":"set","field":"cleared","value":true}]"""),
                Triple("a-dup", all(cond("notes", "is", "\"n\"")), """[{"op":"set","field":"cleared","value":true}]"""),
                Triple("m-missing", all(cond("description", "is", "\"gone\"")), """[{"op":"set","field":"category","value":"gone"}]"""),
                Triple("b-never", all(cond("amount", "gt", "5"), cond("amount", "lt", "1")), "[]"),
                Triple("k-empty", "[]", """[{"op":"set","field":"notes","value":"x"}]"""),
            )
        val forward = budget().apply { rules.forEach { (id, c, a) -> rule(id, c, a) } }
        val backward = budget().apply { rules.reversed().forEach { (id, c, a) -> rule(id, c, a) } }
        val first = forward.findings()
        assertEquals(first, forward.findings())
        assertEquals(first, backward.findings())
        assertEquals(
            listOf(
                "RULE_MISSING_REFERENCE:m-missing",
                "RULE_CONTRADICTORY:b-never",
                "RULE_NO_CONDITIONS:k-empty",
                "RULE_NO_ACTIONS:b-never",
                "RULE_DUPLICATE:a-dup,z-dup",
            ),
            first.map { it.key },
        )
    }
}
