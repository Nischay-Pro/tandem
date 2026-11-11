package app.tandemforactual.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

        composeTestRule.onNode(hasClickAction()).assertHeightIsAtLeast(TandemLayout.listRowMinHeight)
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

    @Test
    fun accountCardGivesLongNamesRoomAtDoubleTextSizeInANarrowLayout() {
        val longName = "Household Checking for Everyday Bills and Monthly Payments"
        val balance = "-€123,456,789.00"
        composeTestRule.setContent {
            val density = LocalDensity.current
            TandemTheme(ThemeMode.LIGHT, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    Box(Modifier.width(260.dp)) {
                        AccountSummaryCard(
                            account =
                                AccountListItemUi(
                                    id = "checking",
                                    name = longName,
                                    typeLabel = "Checking",
                                    status = "Off budget · Last synced today",
                                    balance = balance,
                                    offBudget = true,
                                    accountType = "checking",
                                    balanceMinor = -123_456_789_00L,
                                ),
                            merchantArtwork = null,
                            onClick = {},
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithText(longName).assertExists().assertWidthIsAtLeast(150.dp)
        composeTestRule.onNodeWithContentDescription("Negative balance, $balance").assertExists()
    }
}
