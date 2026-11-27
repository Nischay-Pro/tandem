package app.tandemforactual.ui.feature.schedules

import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.ScheduleSeed
import app.tandemforactual.ui.feature.schedules.components.ScheduleDraftValues
import app.tandemforactual.ui.feature.schedules.components.ScheduleEditDraft
import app.tandemforactual.ui.feature.schedules.components.SchedulePattern
import app.tandemforactual.ui.feature.schedules.components.scheduleDraftValues
import app.tandemforactual.ui.feature.schedules.components.scheduleFields
import app.tandemforactual.ui.feature.schedules.components.validationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class ScheduleDraftMappingTest {
    private fun format(minor: Long) = "%d.%02d".format(minor / 100, minor % 100)

    @Test
    fun aSpendingRangeIsEnteredWithoutSignsAndSavedSmallestFirst() {
        val schedule =
            LiveScheduleSummary(
                "power",
                "Power",
                "2026-12-03",
                """{"frequency":"monthly","start":"2026-11-03","interval":1,"custom":"kept","patterns":[{"type":"FR","value":-1}]}""",
                "checking",
                "Checking",
                "utility",
                null,
                -9_000L to -6_000L,
                "isbetween",
                true,
                postsTransaction = true,
            )
        val date = Json.parseToJsonElement(schedule.dateValueJson).jsonObject
        val values = scheduleDraftValues(schedule, date, "bills", ::format)
        assertEquals("60.00", values.amount)
        assertEquals("90.00", values.amountMax)
        assertEquals(listOf(SchedulePattern("FR", -1)), values.patterns)
        assertEquals("bills", values.categoryId)

        val fields = ScheduleEditDraft(values).scheduleFields(date, 6_000, 9_000, creating = false)

        assertEquals(Json.parseToJsonElement("""{"num1":-9000,"num2":-6000}"""), fields["amountRangeMinor"])
        val rule = fields["date"] as JsonObject
        assertEquals("\"kept\"", rule["custom"].toString())
        assertEquals("""[{"type":"FR","value":-1}]""", rule["patterns"].toString())
        assertEquals("\"bills\"", fields["categoryId"].toString())
        assertEquals("true", fields["postsTransaction"].toString())
    }

    @Test
    fun aTransactionStartsAMonthlyScheduleThatLeavesOutAnEmptyNameAndCategory() {
        val values = scheduleDraftValues(ScheduleSeed("t1", "checking", "gym", "", 4_550, "2026-11-20", "Gym"), ::format)
        val draft = ScheduleEditDraft(values)
        assertNull(draft.validationError(4_550, null, nameRequired = false))
        assertEquals("name", draft.validationError(4_550, null, nameRequired = true)?.field)

        val fields = draft.scheduleFields(null, 4_550, null, creating = true)

        assertFalse("name" in fields || "categoryId" in fields)
        assertEquals("4550", fields["amountMinor"].toString())
        assertEquals("\"isapprox\"", fields["amountOp"].toString())
        assertEquals(
            Json.parseToJsonElement(
                """{"frequency":"monthly","interval":1,"start":"2026-11-20","patterns":[],"skipWeekend":false,""" +
                    """"weekendSolveMode":"after","endMode":"never"}""",
            ),
            fields["date"],
        )
    }

    @Test
    fun theDraftSurvivesBeingSavedAndRestored() {
        val values = ScheduleDraftValues(patterns = listOf(SchedulePattern("day", -1), SchedulePattern("MO", 2)), postsTransaction = true)
        assertEquals(values, ScheduleDraftValues.decode(values.encode()))
        val endsTooEarly =
            values.copy(accountId = "checking", payeeId = "gym", date = "2026-12-01", endMode = "on_date", endDate = "2026-11-01")
        assertEquals("endDate", ScheduleEditDraft(endsTooEarly).validationError(100, null, nameRequired = false)?.field)
    }
}
