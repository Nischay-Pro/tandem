package app.tandemforactual.data.actual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ActualRecurrenceTest {
    @Test
    fun nextDatesMatchActual() {
        val cases = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/actual/schedule-dates.json")).readText()).jsonArray
        var checked = 0
        for (case in cases) {
            val config = RecurConfig.parse(case.jsonObject.getValue("config").jsonObject)
            for (result in case.jsonObject.getValue("results").jsonArray) {
                val row = result.jsonObject
                val day = Day.parse(row.getValue("day").jsonPrimitive.content)
                val label = "${case.jsonObject["config"]} on $day"
                assertEquals(row["next"]?.jsonPrimitive?.contentOrNull, ActualRecurrence.nextDate(config, day)?.toString(), "next $label")
                assertEquals(
                    row["from"]?.jsonPrimitive?.contentOrNull,
                    ActualRecurrence.nextDateFrom(config, day)?.toString(),
                    "from $label",
                )
                assertEquals(
                    row["after"]?.jsonPrimitive?.contentOrNull,
                    ActualRecurrence.nextDateAfter(config, day)?.toString(),
                    "after $label",
                )
                checked++
            }
        }
        assertEquals(16 * 56, checked)
    }

    @Test
    fun conditionValuesCoverFixedAndUnknownDates() {
        val today = Day.parse("2026-11-21")
        assertEquals("2026-12-01", ActualRecurrence.nextDateFrom(JsonPrimitive("2026-12-01"), today).toString())
        assertEquals("2026-12-01", ActualRecurrence.nextDateAfter(JsonPrimitive("2026-12-01"), today).toString())
        assertEquals(null, ActualRecurrence.nextDateFrom(JsonPrimitive("2026-12"), today))
        assertEquals(null, ActualRecurrence.nextDateAfter(JsonNull, today))
        assertEquals(7, Day.parse("2026-11-22").weekday)

        fun config(json: String) = RecurConfig.parse(Json.parseToJsonElement(json).jsonObject)
        assertEquals(
            null,
            ActualRecurrence.nextDate(
                config("""{"frequency":"monthly","start":"2026-02-01","interval":12,"patterns":[{"type":"day","value":30}]}"""),
                today,
            ),
        )
        assertEquals(
            null,
            ActualRecurrence.nextDate(
                config("""{"frequency":"monthly","start":"2026-01-01","patterns":[{"type":"friday","value":0}]}"""),
                today,
            ),
        )
        assertEquals(
            null,
            ActualRecurrence.nextDateAfter(
                config("""{"frequency":"monthly","start":"2026-01-01","patterns":[{"type":"friday","value":0}]}"""),
                today,
            ),
        )
    }
}
