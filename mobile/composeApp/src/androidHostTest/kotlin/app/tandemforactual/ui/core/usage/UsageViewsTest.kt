package app.tandemforactual.ui.core.usage

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.store.RecordUsage
import app.tandemforactual.ui.core.state.RulesReferenceFilter
import app.tandemforactual.ui.core.state.matches
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UsageViewsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun deleteDialogSaysCheckingAndHoldsConfirmUntilCountsArrive() {
        var usage by mutableStateOf<UsageState>(UsageState.Checking)
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                DestructiveConfirmationDialog(
                    title = "Delete Coffee?",
                    message = "Its transactions lose this payee.",
                    confirmLabel = "Delete",
                    dismissLabel = "Keep payee",
                    enabled = !usage.isChecking,
                    content = { UsageSummary(usage, UsageSubject.PAYEE) },
                    onDismissRequest = {},
                    onConfirm = {},
                )
            }
        }

        composeTestRule.onNodeWithText(USAGE_CHECKING_LABEL).assertExists()
        composeTestRule.onNodeWithText("Delete").assertIsNotEnabled()

        usage = UsageState.Ready(RecordUsage(transactions = 1_204, rules = 0))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(USAGE_CHECKING_LABEL).assertDoesNotExist()
        composeTestRule.onNodeWithText("Transactions").assertExists()
        composeTestRule.onNodeWithText("1,204").assertExists()
        composeTestRule.onNodeWithText("None").assertExists()
        composeTestRule.onNodeWithText("Delete").assertIsEnabled()
    }

    @Test
    fun unavailableCountsSayWhyInsteadOfABareQuestion() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.LIGHT, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                UsageSummary(UsageState.Unavailable, UsageSubject.RULE)
            }
        }

        composeTestRule.onNodeWithText("Couldn’t count where this is used on this device.").assertExists()
    }

    @Test
    fun whereUsedLinksOnlyRowsWithSomethingToShow() {
        var openedTransactions = 0
        var openedRules = 0
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                Column {
                    WhereUsedBody(
                        "Groceries",
                        UsageState.Ready(RecordUsage(transactions = 3, rules = 0)),
                        UsageSubject.CATEGORY,
                        links = UsageLinks(transactions = { openedTransactions += 1 }, rules = { openedRules += 1 }),
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Everything in this budget that uses Groceries.").assertExists()
        composeTestRule.onNodeWithText("Transactions").assertHasClickAction().performClick()
        composeTestRule.onNodeWithText("Rules").assertHasNoClickAction()

        assertEquals(1, openedTransactions)
        assertEquals(0, openedRules)
    }

    @Test
    fun linesFollowWhatEachKindOfItemCanAffect() {
        val usage = RecordUsage(transactions = 2, rules = 1, childCategories = 4, schedules = 1, balanceMinor = -1_500)
        val balance: (Long) -> String = { "€$it" }

        assertEquals(
            listOf("Transactions" to "2", "Rules" to "1"),
            usageLines(usage, UsageSubject.PAYEE, balance).map { it.title to it.value },
        )
        assertEquals(
            listOf("Transactions" to "2", "Rules" to "1"),
            usageLines(usage, UsageSubject.CATEGORY, balance).map { it.title to it.value },
        )
        assertEquals(
            listOf("Categories" to "4", "Transactions" to "2", "Rules" to "1"),
            usageLines(usage, UsageSubject.CATEGORY_GROUP, balance).map { it.title to it.value },
        )
        assertEquals(
            listOf("Current balance" to "€-1500", "Transactions" to "2", "Rules" to "1"),
            usageLines(usage, UsageSubject.ACCOUNT, balance).map { it.title to it.value },
        )
        assertEquals(
            listOf("Transactions" to "2", "Rules" to "1"),
            usageLines(usage.copy(balanceMinor = null), UsageSubject.ACCOUNT, balance).map { it.title to it.value },
        )
        assertEquals(listOf("Schedules" to "1"), usageLines(usage, UsageSubject.RULE, balance).map { it.title to it.value })
        assertEquals(listOf("Schedules" to "None"), usageLines(RecordUsage(), UsageSubject.RULE, balance).map { it.title to it.value })
    }

    @Test
    fun countsAreUnavailableWithoutALocalBudget() =
        runBlocking {
            assertEquals(UsageState.Unavailable, loadRecordUsage(null, "budget", UsageSubject.PAYEE, "payee"))
        }

    @Test
    fun rulesFilterMatchesIdsInConditionsListsAndActions() {
        val filter = RulesReferenceFilter(setOf("coffee"), "Using Coffee")

        assertTrue(filter.matches(rule("""{"conditions":[{"field":"payee","value":"coffee"}],"actions":[]}""")))
        assertTrue(filter.matches(rule("""{"conditions":[{"field":"payee","value":["rent","coffee"]}]}""")))
        assertTrue(filter.matches(rule("""{"conditions":[],"actions":[{"field":"payee","value":"coffee"}]}""")))
        assertFalse(filter.matches(rule("""{"conditions":[{"field":"payee","value":"rent"}],"actions":["coffee"]}""")))
        assertFalse(filter.matches(rule("""{"conditions":[{"field":"amount","value":{"num1":1}}]}""")))
        assertFalse(filter.matches(rule("not json")))
    }

    private fun rule(json: String) =
        BudgetRecordEntity("b", "rule", "r", json, "rev", null, false, false, "g", null, null, null, null, null, null)
}
