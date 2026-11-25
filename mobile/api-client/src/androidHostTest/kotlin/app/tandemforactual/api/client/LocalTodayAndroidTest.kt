package app.tandemforactual.api.client

import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalTodayAndroidTest {
    @Test
    fun todayIsTheDevicesLocalDate() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            assertEquals(LocalDate.now(ZoneId.of("Pacific/Kiritimati")).toString(), localTodayIsoDate())
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
