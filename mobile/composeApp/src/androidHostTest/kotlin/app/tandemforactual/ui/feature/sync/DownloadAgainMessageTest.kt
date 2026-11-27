package app.tandemforactual.ui.feature.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadAgainMessageTest {
    @Test
    fun promisesToResendEditsOnlyWhenItCan() {
        val lead = "Tandem sets this device’s copy aside and downloads a fresh one from Actual."
        assertEquals("$lead Everything here is already in Actual.", downloadAgainMessage(0, blocked = false))
        assertEquals(
            "$lead The 1 change Actual doesn’t have yet will be sent again after the download.",
            downloadAgainMessage(1, blocked = false),
        )
        assertEquals(
            "$lead The 3 changes Actual doesn’t have yet will be sent again after the download.",
            downloadAgainMessage(3, blocked = false),
        )
        assertEquals(lead, downloadAgainMessage(3, blocked = true))
    }
}
