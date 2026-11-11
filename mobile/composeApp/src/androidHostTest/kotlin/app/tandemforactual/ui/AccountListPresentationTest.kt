package app.tandemforactual.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AccountListPresentationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun accountCardKeepsLongNameAccessibleAndOpensAccountOnTap() {
        val longName = "Household Checking for Everyday Bills and Monthly Payments"
        var opened = false
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                AccountSummaryCard(
                    account =
                        AccountListItemUi(
                            id = "checking",
                            name = longName,
                            typeLabel = "Checking",
                            status = "Last synced today",
                            balance = "-\$3,200.00",
                            offBudget = false,
                            accountType = "checking",
                            balanceMinor = -320000,
                        ),
                    merchantArtwork = null,
                    onClick = { opened = true },
                )
            }
        }

        composeTestRule.onNodeWithText(longName).assertExists().performClick()
        composeTestRule.onNodeWithContentDescription("Negative balance, -\$3,200.00").assertExists()
        assertTrue(opened)
    }

    @Test
    fun closedAccountCardShowsItsClosedStateAndSignedAmount() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.LIGHT, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                AccountSummaryCard(
                    account =
                        AccountListItemUi(
                            id = "closed",
                            name = "Closed Rewards Card",
                            typeLabel = "Credit",
                            status = "Closed",
                            balance = "-\$4.00",
                            offBudget = false,
                            closed = true,
                            accountType = "credit",
                            balanceMinor = -400,
                        ),
                    merchantArtwork = null,
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Credit · Closed").assertExists()
        composeTestRule.onNodeWithContentDescription("Negative balance, -\$4.00").assertExists()
    }
}
