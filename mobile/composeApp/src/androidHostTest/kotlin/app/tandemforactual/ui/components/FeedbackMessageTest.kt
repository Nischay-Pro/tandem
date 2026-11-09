package app.tandemforactual.ui.components

import androidx.compose.ui.semantics.LiveRegionMode
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedbackMessageTest {
    @Test
    fun blockingErrorsUseAssertiveAnnouncements() {
        assertEquals(LiveRegionMode.Assertive, inlineMessageLiveRegion(InlineMessageKind.ERROR))
    }

    @Test
    fun nonBlockingMessagesUsePoliteAnnouncements() {
        assertEquals(LiveRegionMode.Polite, inlineMessageLiveRegion(InlineMessageKind.WARNING))
        assertEquals(LiveRegionMode.Polite, inlineMessageLiveRegion(InlineMessageKind.INFO))
        assertEquals(LiveRegionMode.Polite, inlineMessageLiveRegion(InlineMessageKind.SUCCESS))
    }
}
