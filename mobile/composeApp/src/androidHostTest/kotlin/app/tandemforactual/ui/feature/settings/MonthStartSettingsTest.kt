package app.tandemforactual.ui.feature.settings

import app.tandemforactual.data.planning.MonthStart
import app.tandemforactual.ui.core.state.AppState
import kotlin.test.Test
import kotlin.test.assertEquals

class MonthStartSettingsTest {
    @Test
    fun daysReadAsOrdinals() {
        assertEquals(
            listOf("The 1st (calendar month)", "The 2nd", "The 3rd", "The 4th", "The 11th", "The 21st", "The 22nd", "The 23rd"),
            listOf(1, 2, 3, 4, 11, 21, 22, 23).map(::monthStartLabel),
        )
    }

    @Test
    fun theMonthStartIsKeptWithTheOtherSettings() {
        val state = AppState()
        assertEquals(MonthStart(1, previousWorkingDay = false), state.monthStart)
        state.monthStartDay = 25
        state.monthStartPreviousWorkingDay = true
        val restored = AppState().apply { restorePersistentSettings(state.persistentSettings()) }
        assertEquals(MonthStart(25, previousWorkingDay = true), restored.monthStart)
        restored.restorePersistentSettings(mapOf("monthStartDay" to "31"))
        assertEquals(MonthStart.MAX_DAY, restored.monthStartDay)
    }
}
