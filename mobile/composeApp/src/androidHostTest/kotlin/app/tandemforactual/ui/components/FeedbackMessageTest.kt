package app.tandemforactual.ui.components

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tandemforactual.ui.AccentChoice
import app.tandemforactual.ui.TandemTheme
import app.tandemforactual.ui.ThemeMode
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun fieldErrorAndLoadFailureExposeAccessibleKindAndAnnouncement() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                FieldErrorMessage("Choose a valid start date.", owner = FeedbackMessageOwner.Field("startDate"))
                FeedbackEmptyState(
                    FeedbackMessage(
                        title = "Tandem isn’t responding",
                        text = "Check the server address, then try again.",
                        kind = InlineMessageKind.ERROR,
                        owner = FeedbackMessageOwner.Screen("sign-in"),
                        actionLabel = "Try again",
                        onAction = {},
                    ),
                )
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
        composeTestRule.onNodeWithText("Tandem isn’t responding").assertExists()
        composeTestRule
            .onNodeWithText("Tandem isn’t responding")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Assertive,
                ),
            )
        composeTestRule.onNodeWithText("Check the server address, then try again.").assertExists()
        composeTestRule.onAllNodesWithContentDescription("Error").assertCountEquals(2)
        composeTestRule.onNodeWithText("Try again").assertHasClickAction()
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
}
