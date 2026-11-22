package app.tandemforactual.ui

import app.tandemforactual.ui.core.state.MainCloudStatus
import app.tandemforactual.ui.core.state.headerSyncCaption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeaderSyncCaptionTest {
    private val now = 10_000_000L

    private fun caption(
        status: MainCloudStatus = MainCloudStatus.SYNCED,
        writes: List<String> = emptyList(),
        last: Long? = now,
    ) = headerSyncCaption(status, writes, last, now) { _, _ -> "today at 9:41" }

    @Test
    fun explainsSyncStateInPlainWords() {
        assertEquals("Synced just now", caption(last = now - 30_000))
        assertEquals("Synced 5 min ago", caption(last = now - 5 * 60_000))
        assertEquals("Synced today at 9:41", caption(last = now - 2 * 3_600_000))
        assertEquals("Syncing…", caption(MainCloudStatus.SYNCING))
        assertEquals("Couldn’t sync", caption(MainCloudStatus.ERROR))
        assertEquals("Saved on this device", caption(MainCloudStatus.OFF))
        assertNull(caption(last = null))
    }

    @Test
    fun countsChangesThatAreWaitingOrNeedReview() {
        assertEquals("1 change waiting to sync", caption(writes = listOf("queued", "committed")))
        assertEquals("2 changes waiting to sync", caption(MainCloudStatus.OFF, listOf("queued", "sending")))
        assertEquals("1 change needs review", caption(MainCloudStatus.ERROR, listOf("conflict", "queued")))
        assertEquals("2 changes need review", caption(writes = listOf("uncertain", "rejected")))
    }
}
