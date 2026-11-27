package app.tandemforactual.data.oracle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long

// actual-golden.json is what Actual reported for scenarios.json; regenerate with scripts/actual-fixtures/generate-budget-maths.mjs.
object BudgetMathsFixtures {
    private fun resource(name: String) = checkNotNull(javaClass.getResource("/actual/budget-maths/$name")).readText()

    val scenarios: List<BudgetScenario> by lazy { BudgetScenario.parseAll(resource("scenarios.json")) }

    val actual: Map<String, Map<String, Map<String, Any>>> by lazy {
        Json.parseToJsonElement(resource("actual-golden.json")).jsonObject.mapValues { (_, months) ->
            months.jsonObject.mapValues { (_, cells) -> cells(cells.jsonObject) }
        }
    }

    fun cells(o: JsonObject): Map<String, Any> =
        o
            .filterValues { it is JsonPrimitive && it !is JsonNull }
            .mapValues { (_, v) -> (v as JsonPrimitive).let { it.booleanOrNull ?: it.long } }
            .toSortedMap()

    fun differences(
        expected: Map<String, Map<String, Any>>,
        actual: Map<String, Map<String, Any>>,
    ): List<String> =
        (expected.keys + actual.keys).sorted().flatMap { month ->
            val want = expected[month].orEmpty()
            val got = actual[month].orEmpty()
            (want.keys + got.keys).sorted().filter { want[it] != got[it] }.map { "$month $it: expected ${want[it]}, got ${got[it]}" }
        }
}

class BudgetMathsOracleTest {
    @Test
    fun oracleAgreesWithActual() {
        val problems =
            BudgetMathsFixtures.scenarios.flatMap { s ->
                BudgetMathsFixtures
                    .differences(BudgetMathsFixtures.actual.getValue(s.name), BudgetMathsOracle.months(s))
                    .map { "${s.name} $it" }
            }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    @Test
    fun overspendingIsTakenFromNextMonthUnlessItRollsOver() {
        val s = BudgetMathsFixtures.scenarios.first { it.name == "envelope-overspending-rollover" }
        val months = BudgetMathsOracle.months(s)
        assertEquals(-5000L, months.getValue("2025-01")["category:Groceries:balance"])
        assertEquals(-5000L, months.getValue("2025-02")["lastMonthOverspent"])
        assertEquals(10000L, months.getValue("2025-02")["category:Groceries:balance"])
        assertEquals(-2000L, months.getValue("2025-03")["category:Groceries:balance"])
        assertEquals(0L, months.getValue("2025-04")["lastMonthOverspent"])
        assertEquals(-3000L, months.getValue("2025-04")["category:Groceries:balance"])
    }

    @Test
    fun aHoldByHandReplacesTheAutomaticIncomeHold() {
        val s = BudgetMathsFixtures.scenarios.first { it.name == "envelope-holds-and-income-rollover" }
        val months = BudgetMathsOracle.months(s)
        assertEquals(15000L, months.getValue("2025-06")["forNextMonth"])
        assertEquals(455000L + 15000L, months.getValue("2025-07")["fromLastMonth"])
    }

    @Test
    fun scenariosMustStartWithTheirEarliestTransaction() {
        val s = BudgetMathsFixtures.scenarios.first()
        assertFailsWith<IllegalArgumentException> { BudgetMathsOracle.months(s.copy(months = listOf("2024-12") + s.months)) }
    }
}
