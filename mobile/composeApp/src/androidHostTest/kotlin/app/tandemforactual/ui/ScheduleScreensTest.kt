package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleScreensTest {
    @Test
    fun validationMessageAppearsOnlyUnderItsOwningField() {
        val message = "Choose a valid start date."
        val messageField = "startDate"

        assertEquals(message, scheduleFieldErrorMessage(message, messageField, "startDate"))
        assertNull(scheduleFieldErrorMessage(message, messageField, "endDate"))
        assertNull(scheduleFieldErrorMessage(message, null, "startDate"))
    }
}
