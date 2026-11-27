package app.tandemforactual.data.actual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GoalTemplateLinesTest {
    private val parity = GoalTemplateParityTest()

    @Test
    fun linesAreWrittenAsActualRendersThem() {
        val golden =
            Json
                .parseToJsonElement(checkNotNull(javaClass.getResource("/actual/goal-templates/actual-golden.json")).readText())
                .jsonObject
        val problems =
            golden.getValue("lines").jsonArray.map { it.jsonObject }.filter { it["rendered"] != null }.mapNotNull { entry ->
                val template = GoalTemplate.fromJson(entry.getValue("template") as JsonObject)
                val expected = entry.getValue("rendered").jsonPrimitive.content
                val written = goalTemplateLine(template)
                if (written == expected) null else "$template: Actual \"$expected\", Tandem \"$written\""
            }
        assertEquals(emptyList(), problems)
        assertNull(goalTemplateLine(GoalTemplate("error", directive = "error")))
    }

    @Test
    fun templateLinesAreAddedAndRemovedWithoutTouchingTheRest() {
        assertEquals("#template 10", noteWithTemplateLine(" ", "#template 10"))
        assertEquals("Rent\n#template 10", noteWithTemplateLine("Rent\n\n", "#template 10"))
        val note = "Rent for the flat\n#template 1200\nPaid on the 1st #goal 50\n#template up to 10"
        assertEquals("Rent for the flat\n#template 1200\n#template up to 10", noteWithoutTemplateLine(note, 1))
        assertEquals("Rent for the flat\nPaid on the 1st #goal 50\n#template up to 10", noteWithoutTemplateLine(note, 0))
    }

    @Test
    fun statusComesFromTheTemplatesUntilActualHasAGoal() {
        val f = parity.fixture("envelope")
        val rent = f.engine().status("2026-09", f.id("Rent"))
        assertEquals(120_000L, rent.needed)
        assertEquals(120_000L, rent.goal)
        assertEquals(120_000L, rent.shortfall(budgeted = 0, balance = 0))
        assertEquals(0L, rent.shortfall(budgeted = 130_000, balance = 0))
        f.edits.applyGoalTemplates("2026-09", force = false)
        val emergency = f.engine().status("2026-09", f.id("Emergency"))
        assertEquals(true, emergency.longGoal)
        assertEquals(400_000L, emergency.shortfall(budgeted = 0, balance = 100_000))
        val bonus = f.engine().status("2026-09", f.id("Bonus"))
        assertEquals(emptyList(), bonus.templates)
        assertNull(bonus.shortfall(0, 0))
    }
}
