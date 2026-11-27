package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Replays each budget built by `generate-goal-templates.mjs` and checks Tandem writes what Actual wrote. */
class GoalTemplateParityTest {
    private val golden = Json.parseToJsonElement(resource("actual-golden.json").decodeToString()).jsonObject
    private val today = golden.getValue("today").jsonPrimitive.content

    private fun resource(name: String) = checkNotNull(javaClass.getResource("/actual/goal-templates/$name")) { name }.readBytes()

    internal class Fixture(
        val db: ActualDatabase,
        val edits: ActualBudgetEdits,
        val today: String,
    ) {
        val tracking get() = db.first("SELECT value FROM preferences WHERE id = 'budgetType'")?.get("value") == "tracking"

        fun id(name: String) = db.first("SELECT id FROM categories WHERE name = ? AND tombstone = 0", name)!!["id"] as String

        fun engine() = GoalTemplateEngine(db, today)

        fun cell(
            month: String,
            name: String,
        ): List<Any?> {
            val table = if (tracking) "reflect_budgets" else "zero_budgets"
            val row =
                db.first(
                    "SELECT amount, goal, long_goal FROM $table WHERE month = ? AND category = ?",
                    ActualMonths.dbMonth(month),
                    id(name),
                )
            return listOf(
                (row?.get("amount") as Number?)?.toLong() ?: 0L,
                (row?.get("goal") as Number?)?.toLong(),
                (row?.get("long_goal") as Number?)?.toLong(),
            )
        }
    }

    internal fun fixture(name: String): Fixture {
        val file =
            File.createTempFile("goal-templates", ".sqlite").apply {
                writeBytes(ZipArchive.read(resource("$name.zip")).getValue("db.sqlite"))
                deleteOnExit()
            }
        val db = ActualDatabase(BundledSQLiteDriver().open(file.path))
        val store = ActualMessageStore(db)
        val clock = store.loadClock()
        store.resetNode(clock)
        val writer = ActualWriter(store, clock, System::currentTimeMillis) { UUID.randomUUID().toString() }
        return Fixture(db, ActualBudgetEdits(writer, { today }), today)
    }

    @Test
    fun envelopeBudgetMatchesActual() = replay("envelope")

    @Test
    fun trackingBudgetMatchesActual() = replay("tracking")

    @Test
    fun wholeAmountsAndPrioritiesMatchActual() = replay("scarce-rounded")

    @Test
    fun aCurrencyWithoutCentsMatchesActual() = replay("yen")

    @Test
    fun templateProblemsMatchActual() = replay("errors")

    @Test
    fun editorTemplatesAndScheduleKindsMatchActual() = replay("editor")

    @Test
    fun editorTemplateProblemsMatchActual() = replay("editor-errors")

    @Test
    fun everyLineParsesAsActualParsesIt() {
        val problems =
            golden.getValue("lines").jsonArray.mapNotNull { entry ->
                val line =
                    entry.jsonObject
                        .getValue("line")
                        .jsonPrimitive.content
                val expected = entry.jsonObject["template"]?.takeIf { it !is JsonNull }?.jsonObject
                val actual = goalTemplatesFromNote(line).firstOrNull()
                val same =
                    when {
                        expected == null -> actual == null
                        expected["type"]?.jsonPrimitive?.content == "error" -> actual?.type == "error"
                        else -> actual == GoalTemplate.fromJson(expected)
                    }
                if (same) null else "$line\n  expected $expected\n  got      $actual"
            }
        assertEquals(emptyList(), problems)
    }

    private fun replay(name: String) {
        val f = fixture(name)
        golden.getValue("scenarios").jsonObject.getValue(name).jsonArray.forEachIndexed { index, element ->
            val step = element.jsonObject
            val label = "$name step $index: $step".take(200)
            val result = step["result"]?.takeIf { it !is JsonNull }?.jsonObject
            when {
                step["check"] != null -> assertEquals(expectedErrors(result), f.engine().check(), label)
                step["note"] != null -> f.edits.setCategoryNote(f.id(step.text("note")), step.text("text"))
                step["set"] != null ->
                    f.edits.setBudgetAmount(
                        f.id(step.text("set")),
                        step.text("month"),
                        step.getValue("amount").jsonPrimitive.longOrNull!!,
                    )
                else -> apply(f, step, result!!, label)
            }
        }
    }

    private fun apply(
        f: Fixture,
        step: JsonObject,
        result: JsonObject,
        label: String,
    ) {
        val month = step.text("apply").ifEmpty { step.text("overwrite").ifEmpty { step.text("month") } }
        val ids =
            when {
                step["single"] != null -> listOf(f.id(step.text("single")))
                step["multiple"] != null -> step.getValue("multiple").jsonArray.map { f.id(it.jsonPrimitive.content) }
                else -> null
            }
        val force = step["overwrite"] != null
        val expectedErrors = expectedErrors(result)
        if (expectedErrors.isNotEmpty()) {
            val error = assertFailsWith<ActualEditException>(label) { f.edits.applyGoalTemplates(month, force, ids) }
            assertEquals(expectedErrors.joinToString("\n\n"), error.message, label)
        } else {
            val outcome = f.edits.applyGoalTemplates(month, force, ids)
            assertEquals(result["count"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0, outcome.applied, label)
        }
        val cells = step.getValue("cells").jsonObject
        val mismatches =
            cells.mapNotNull { (category, expected) ->
                val want = expected.jsonArray.map(::number)
                val got = f.cell(month, category)
                if (want == got) null else "$category: Actual $want, Tandem $got"
            }
        assertEquals(emptyList(), mismatches, label)
    }

    private fun number(element: JsonElement): Long? = (element as? JsonPrimitive)?.longOrNull

    private fun expectedErrors(result: JsonObject?): List<String> =
        if (result?.get("message")?.jsonPrimitive?.content == "template-errors") {
            result
                .getValue("pre")
                .jsonPrimitive.content
                .split("\n\n")
        } else {
            emptyList()
        }

    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
}
