package app.tandemforactual.ui

import app.tandemforactual.ui.core.session.crashReportText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrashReportTextTest {
    @Test
    fun reportHoldsVersionsDeviceAndTraceOnly() {
        val report = crashReportText("1.4.0", "Android 16 (API 36)", "Google Pixel 9", "main", "2026-11-20T10:00:00Z", "boom\n\tat A.b")
        assertEquals(
            "Tandem crash report\nApp version: 1.4.0\nPlatform: Android 16 (API 36)\nDevice: Google Pixel 9\nThread: main\n" +
                "Time: 2026-11-20T10:00:00Z\n\nboom\n\tat A.b",
            report,
        )
        assertTrue(crashReportText("v", "p", "d", "t", "now", "x".repeat(50_000)).length < 20_200)
    }
}
