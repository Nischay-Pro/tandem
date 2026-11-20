package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceWriteHistoryTest {
    @Test
    fun labelsDescribeWhatHappenedToEachChange() {
        assertEquals("Added", deviceWriteActionLabel("CREATE"))
        assertEquals("Edited", deviceWriteActionLabel("UPDATE"))
        assertEquals("Deleted", deviceWriteActionLabel("DELETE"))
        assertEquals("Sent", deviceWriteStatusLabel("committed", null))
        assertEquals("Undone", deviceWriteStatusLabel("discarded", "discarded-by-user"))
        assertEquals("Discarded", deviceWriteStatusLabel("discarded", "remote-deleted"))
        assertEquals("Replaced by a later edit", deviceWriteStatusLabel("superseded", null))
        assertEquals("Needs review", deviceWriteStatusLabel("conflict", null))
        assertEquals("Waiting", deviceWriteStatusLabel("queued", null))
        assertEquals("Sending", deviceWriteStatusLabel("sending", null))
        assertEquals("Rejected", deviceWriteStatusLabel("rejected", null))
        assertEquals("Unconfirmed", deviceWriteStatusLabel("uncertain", null))
        assertEquals("Applying", deviceWriteStatusLabel("applying", null))
    }
}
