package app.tandemforactual.ui.feature.schedules

import app.tandemforactual.data.actual.DiscoveredSchedule
import app.tandemforactual.ui.feature.schedules.components.createFields
import app.tandemforactual.ui.feature.schedules.components.scheduleRepeatLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class ScheduleDiscoveryPresentationTest {
    @Test
    fun repeatRulesReadAsSentences() {
        assertEquals("Every week", scheduleRepeatLabel("""{"frequency":"weekly","start":"2026-10-26"}"""))
        assertEquals("Every 2 weeks", scheduleRepeatLabel("""{"frequency":"weekly","interval":2,"start":"2026-10-26"}"""))
        assertEquals("Every month on the 5th", scheduleRepeatLabel("""{"start":"2026-07-05","frequency":"monthly"}"""))
        assertEquals(
            "Every month on the last day",
            scheduleRepeatLabel("""{"frequency":"monthly","patterns":[{"type":"day","value":-1}],"start":"2026-07-31"}"""),
        )
        assertEquals(
            "Every month on the 1st Saturday and 3rd Saturday",
            scheduleRepeatLabel(
                """{"start":"2026-10-03","frequency":"monthly",""" +
                    """"patterns":[{"type":"SA","value":1},{"type":"SA","value":3}]}""",
            ),
        )
        assertEquals(
            "Every 3 months on the 12th and 22nd",
            scheduleRepeatLabel(
                """{"frequency":"monthly","interval":3,"start":"2026-01-01",""" +
                    """"patterns":[{"type":"day","value":12},{"type":"day","value":22}]}""",
            ),
        )
        assertEquals("Every year", scheduleRepeatLabel("""{"frequency":"yearly","start":"2026-01-01"}"""))
        assertEquals("Repeats", scheduleRepeatLabel("[]"))
    }

    @Test
    fun aFoundPaymentBecomesTheScheduleActualWouldCreate() {
        val found = DiscoveredSchedule("card", "coffee", -340, false, """{"frequency":"weekly","start":"2026-10-26"}""", true)
        assertEquals(
            Json.parseToJsonElement(
                """{"discovered":true,"accountId":"card","payeeId":"coffee","amountOp":"isapprox","amountMinor":-340,""" +
                    """"date":{"frequency":"weekly","start":"2026-10-26"},"dateOp":"is"}""",
            ),
            found.createFields(),
        )
    }
}
