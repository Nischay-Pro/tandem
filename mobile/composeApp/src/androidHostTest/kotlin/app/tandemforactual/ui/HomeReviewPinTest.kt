package app.tandemforactual.ui

import app.tandemforactual.ui.core.state.HomeEntry
import app.tandemforactual.ui.core.state.HomeModule
import app.tandemforactual.ui.core.state.HomePlan
import app.tandemforactual.ui.feature.home.pinReviewInbox
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeReviewPinTest {
    @Test
    fun takesTheReviewInboxOutOfTheArrangedModules() {
        val plan =
            HomePlan(
                listOf(HomeEntry.Ready(HomeModule.ACCOUNTS), HomeEntry.Ready(HomeModule.REVIEW_INBOX)),
                listOf(HomeEntry.Ready(HomeModule.REVIEW_INBOX), HomeEntry.Ready(HomeModule.RECENT_ACTIVITY)),
            )
        assertEquals(
            HomePlan(listOf(HomeEntry.Ready(HomeModule.ACCOUNTS)), listOf(HomeEntry.Ready(HomeModule.RECENT_ACTIVITY))),
            pinReviewInbox(plan),
        )
    }
}
