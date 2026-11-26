package app.tandemforactual.ui.components

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.feedback.FeedbackMessage
import app.tandemforactual.ui.design.feedback.FeedbackMessageOwner
import app.tandemforactual.ui.design.feedback.FieldErrorMessage
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.feedback.inlineMessageLiveRegion
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FeedbackMessageTest {
    @get:Rule
    val composeTestRule = createComposeRule()

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

    @Test
    fun errorBannerExposesItsKindLiveRegionRetryAndDismissActions() {
        var retryRequested = false
        var dismissed = false
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                InlineMessage(
                    text = "Couldn’t save this edit. Try again.",
                    kind = InlineMessageKind.ERROR,
                    actionLabel = "Try again",
                    onAction = { retryRequested = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeTestRule
            .onNodeWithText("Couldn’t save this edit. Try again.")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Assertive,
                ),
            )
        composeTestRule.onNodeWithContentDescription("Error").assertExists()
        composeTestRule.onNodeWithText("Try again").assertHasClickAction().performClick()
        composeTestRule.onNodeWithContentDescription("Dismiss message").performClick()
        assertTrue(retryRequested)
        assertTrue(dismissed)
    }

    @Test
    fun fieldErrorExposesAccessibleKindAndAnnouncement() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                FieldErrorMessage("Choose a valid start date.", owner = FeedbackMessageOwner.Field("startDate"))
            }
        }

        composeTestRule
            .onNodeWithText("Choose a valid start date.")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Assertive,
                ),
            )
        composeTestRule.onAllNodesWithContentDescription("Error").assertCountEquals(1)
        assertEquals(
            FeedbackMessageOwner.Screen("sign-in"),
            FeedbackMessage(
                title = "Tandem isn’t responding",
                text = "Check the server address, then try again.",
                kind = InlineMessageKind.ERROR,
                owner = FeedbackMessageOwner.Screen("sign-in"),
            ).owner,
        )
    }

    @Test
    fun bannerActionKeepsAFullSizeTouchTarget() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.LIGHT, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                InlineMessage(text = "Sync paused.", kind = InlineMessageKind.INFO, actionLabel = "Retry", onAction = {})
            }
        }

        composeTestRule.onNodeWithText("Retry").assertHasClickAction().assertHeightIsAtLeast(48.dp)
    }
}
