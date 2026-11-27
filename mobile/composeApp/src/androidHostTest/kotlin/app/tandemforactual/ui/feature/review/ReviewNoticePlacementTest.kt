package app.tandemforactual.ui.feature.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.FeedbackMessage
import app.tandemforactual.ui.design.feedback.FeedbackMessageView
import app.tandemforactual.ui.design.feedback.FeedbackPresentation
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.feedback.LocalNoticeClearance
import app.tandemforactual.ui.design.feedback.NoticeClearance
import app.tandemforactual.ui.design.feedback.NoticeEdge
import app.tandemforactual.ui.design.feedback.NoticeHost
import app.tandemforactual.ui.design.feedback.reservesNoticeSpace
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ReviewNoticePlacementTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val navigation =
        AppNavigation(open = {}, reset = {}, replaceTop = {}, newTransaction = {}, selectTab = {}, back = {}, backToList = {})

    private fun showWithNotice(screen: @Composable () -> Unit) {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                val clearance = remember { NoticeClearance() }
                CompositionLocalProvider(LocalNoticeClearance provides clearance) {
                    Box(Modifier.fillMaxSize()) {
                        screen()
                        NoticeHost(visible = true) {
                            SurfaceCard(Modifier.widthIn(max = 560.dp).testTag(NOTICE)) {
                                FeedbackMessageView(
                                    FeedbackMessage(
                                        text = "Categorized Corner Shop → Groceries",
                                        kind = InlineMessageKind.SUCCESS,
                                        actionLabel = "Undo",
                                        onAction = {},
                                        onDismiss = {},
                                    ),
                                    FeedbackPresentation.BANNER,
                                )
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun showReview() =
        showWithNotice {
            TransactionReviewScreen(
                AppState(),
                navigation,
                TandemMobileSession(TandemApiClient(), null, { 0L }),
                listOf(
                    TransactionItem("Corner Shop", "", "Nov 19", -1250L, "C", recordId = "t1"),
                    TransactionItem("Book Nook", "", "Nov 20", -2300L, "B", recordId = "t2"),
                ),
                null,
            )
        }

    private fun button(label: String) = composeTestRule.onNode(hasText(label) and hasClickAction())

    private fun assertNoticeClearOfReviewActions() {
        val notice = composeTestRule.onNodeWithTag(NOTICE).assertIsDisplayed().getBoundsInRoot()
        listOf("Skip for now", "Categorize", "Open transaction").forEach { label ->
            val button = button(label).assertIsDisplayed().getBoundsInRoot()
            assertFalse(notice.overlaps(button), "notice $notice covers $label $button")
        }
    }

    private fun assertActionsTakeTaps() {
        composeTestRule.onNodeWithText("2 to go").assertIsDisplayed()
        button("Skip for now").performClick()
        composeTestRule.onNodeWithText("1 to go").assertIsDisplayed()
        button("Categorize").performClick()
        composeTestRule.onNodeWithText("Choose category").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun phoneNoticeSitsAboveTheReviewActionsAndLeavesThemTappable() {
        showReview()

        assertNoticeClearOfReviewActions()
        assertActionsTakeTaps()
    }

    @Test
    @Config(qualifiers = "w800dp-h400dp")
    fun shortWindowNoticeStaysClearOfTheReviewActionsAndLeavesThemTappable() {
        showReview()

        assertNoticeClearOfReviewActions()
        assertActionsTakeTaps()
    }

    @Test
    @Config(qualifiers = "w800dp-h400dp")
    fun noticeMovesUnderTheHeaderWhenTheActionsLeaveNoRoomAboveThem() {
        showWithNotice {
            Box(Modifier.fillMaxSize()) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag(HEADER)
                        .reservesNoticeSpace(NoticeEdge.TOP),
                )
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .align(Alignment.BottomCenter)
                        .testTag(ACTIONS)
                        .reservesNoticeSpace(),
                )
            }
        }

        val notice = composeTestRule.onNodeWithTag(NOTICE).getBoundsInRoot()
        val header = composeTestRule.onNodeWithTag(HEADER).getBoundsInRoot()
        val actions = composeTestRule.onNodeWithTag(ACTIONS).getBoundsInRoot()
        assertTrue(notice.top >= header.bottom, "notice $notice is not under the header $header")
        assertFalse(notice.overlaps(header))
        assertTrue(notice.top < actions.top)
    }

    private fun DpRect.overlaps(other: DpRect) =
        Rect(
            left.value,
            top.value,
            right.value,
            bottom.value,
        ).overlaps(Rect(other.left.value, other.top.value, other.right.value, other.bottom.value))

    private companion object {
        const val NOTICE = "notice"
        const val HEADER = "header"
        const val ACTIONS = "actions"
    }
}
