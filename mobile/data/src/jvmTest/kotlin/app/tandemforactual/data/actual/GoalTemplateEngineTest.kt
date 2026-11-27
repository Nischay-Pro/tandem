package app.tandemforactual.data.actual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalTemplateEngineTest {
    private val parity = GoalTemplateParityTest()

    @Test
    fun aPreviewChangesNothing() {
        val f = parity.fixture("envelope")
        val before = f.cell("2026-09", "Rent")
        val preview = f.engine().preview("2026-09", force = false)
        assertEquals(26, preview.applied)
        assertEquals(120_000L, preview.values.first { it.categoryId == f.id("Rent") }.budgeted)
        assertEquals(before, f.cell("2026-09", "Rent"))
        val single = f.engine().preview("2026-09", force = true, listOf(f.id("Emergency")))
        assertEquals(listOf(GoalTemplateValues(f.id("Emergency"), 20_000, 500_000, true)), single.values)
    }

    @Test
    fun aPreviewReportsProblemsInsteadOfAmounts() {
        val f = parity.fixture("errors")
        val preview = f.engine().preview("2026-11", force = false)
        assertEquals(0, preview.applied)
        assertTrue(preview.errors.any { it.startsWith("Past: ") })
        assertEquals(emptyList(), preview.values)
    }

    @Test
    fun anUnknownRepeatPeriodStopsTheRun() {
        val f = parity.fixture("editor")
        f.db.execute(
            "UPDATE categories SET goal_def = ? WHERE id = ?",
            """[{"type":"periodic","amount":5,"period":{"period":"fortnight","amount":1},"starting":"2026-01-01","priority":0,"directive":"template"}]""",
            f.id("Odd"),
        )
        val error = assertFailsWith<GoalTemplateException> { f.edits.applyGoalTemplates("2026-10", force = false) }
        assertEquals("Unrecognized periodic period: fortnight", error.message)
    }

    @Test
    fun aBadMonthIsRefused() {
        val error = assertFailsWith<ActualEditException> { parity.fixture("yen").edits.applyGoalTemplates("2026-13", force = false) }
        assertEquals("Choose a valid budget month", error.message)
    }

    @Test
    fun editorTemplatesThatAreNotJsonAreIgnored() {
        assertNull(GoalTemplate.fromGoalDef("not json"))
        assertNull(GoalTemplate.fromGoalDef(null))
        assertEquals(listOf(GoalTemplate("error", priority = null)), GoalTemplate.fromGoalDef("""[{}]"""))
    }

    @Test
    fun descriptionsAndCleanupLinesFollowActualsRules() {
        val templates = goalTemplatesFromNote("Rent for the flat\nPaid on the 1st\n#template 1200\n\nnote\n#cleanup source\n#goal 50")
        assertEquals("Rent for the flat\nPaid on the 1st", templates[0].description)
        assertNull(templates[1].description)
        assertEquals(
            "Invalid adjustment percentage (1000.5%). Must be between -100% and 1000%",
            goalTemplatesFromNote("#template average 2 months [increase 1000.5%]")[0].error,
        )
    }

    @Test
    fun aScheduleWithoutItsRuleOrAmountIsReported() {
        val f = parity.fixture("editor")
        val rule = f.db.first("SELECT rule FROM schedules WHERE name = 'Phone bill'")!!["rule"]
        f.db.execute("UPDATE rules SET conditions = ? WHERE id = ?", """[{"op":"isapprox","field":"date","value":"2026-12-10"}]""", rule)
        assertEquals("Schedule Phone bill has no amount", assertFailsWith<GoalTemplateException> { phonePreview(f) }.message)
        f.db.execute("DELETE FROM rules WHERE id = ?", rule)
        assertEquals("Schedule Phone bill has no rule", assertFailsWith<GoalTemplateException> { phonePreview(f) }.message)
    }

    @Test
    fun aScheduleWithoutADateAddsNothing() {
        val f = parity.fixture("editor")
        val rule = f.db.first("SELECT rule FROM schedules WHERE name = 'Phone bill'")!!["rule"]
        f.db.execute("UPDATE rules SET conditions = ? WHERE id = ?", """[{"op":"is","field":"amount","value":-4500}]""", rule)
        assertEquals(0L, phonePreview(f).values.single().budgeted)
    }

    @Test
    fun aYearlyScheduleIsSpreadOverItsYear() {
        val f = parity.fixture("editor")
        val rule = f.db.first("SELECT rule FROM schedules WHERE name = 'Phone bill'")!!["rule"]
        f.db.execute(
            "UPDATE rules SET conditions = ? WHERE id = ?",
            """[{"op":"isapprox","field":"date","value":{"frequency":"yearly","start":"2027-05-20","interval":1,"endMode":"never"}},""" +
                """{"op":"is","field":"amount","value":-1200}]""",
            rule,
        )
        f.edits.setBudgetAmount(f.id("Phone"), "2026-10", 100_000)
        assertEquals(100L, phonePreview(f).values.single().budgeted)
    }

    private fun phonePreview(f: GoalTemplateParityTest.Fixture) = f.engine().preview("2026-11", force = true, listOf(f.id("Phone")))
}
