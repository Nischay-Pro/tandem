package app.tandemforactual.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ConflictPresentationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun conflictComparisonListsEveryChangedTransactionField() {
        val edited =
            Json
                .parseToJsonElement(
                    """{"amount":1250,"payee":"new-payee","category":"medical","date":"2026-11-02","account":"savings","notes":"new note","cleared":true}""",
                ).jsonObject
        val actual =
            Json
                .parseToJsonElement(
                    """{"amount":1000,"payee":"old-payee","category":"food","date":"2026-11-01","account":"checking","notes":"old note","cleared":false}""",
                ).jsonObject

        val differences =
            conflictFieldComparisons(
                edited = edited,
                actual = actual,
                names =
                    ConflictFieldNameLookup(
                        payees = mapOf("new-payee" to "Target", "old-payee" to "Costco"),
                        accounts = mapOf("savings" to "Savings", "checking" to "Checking"),
                        categories = mapOf("medical" to "Medical", "food" to "Groceries"),
                    ),
                formatAmount = { "minor:$it" },
                formatDate = { it },
            )

        assertEquals(listOf("Amount", "Payee", "Category", "Date", "Account", "Notes", "Cleared"), differences.map { it.label })
        assertEquals(ConflictFieldComparison("Payee", "Target", "Costco"), differences[1])
        assertEquals(ConflictFieldComparison("Category", "Medical", "Groceries"), differences[2])
        assertEquals(ConflictFieldComparison("Cleared", "Cleared", "Uncleared"), differences.last())
    }

    @Test
    fun conflictComparisonOmitsMatchingFieldsAndIdentifiesReconciliationState() {
        val snapshot =
            Json
                .parseToJsonElement(
                    """{"amount":1000,"payee":"market","category":"food","date":"2026-11-01","account":"checking","notes":"same note","cleared":"reconciled"}""",
                ).jsonObject
        val actual =
            Json
                .parseToJsonElement(
                    """{"amount":1000,"payee":"market","category":"food","date":"2026-11-01","account":"checking","notes":"same note","cleared":false}""",
                ).jsonObject

        val differences =
            conflictFieldComparisons(
                edited = snapshot,
                actual = actual,
                names =
                    ConflictFieldNameLookup(
                        payees = mapOf("market" to "Market"),
                        categories = mapOf("food" to "Groceries"),
                        accounts =
                            mapOf(
                                "checking" to "Checking",
                            ),
                    ),
                formatAmount = { "minor:$it" },
                formatDate = { it },
            )

        assertEquals(listOf(ConflictFieldComparison("Cleared", "Reconciled", "Uncleared")), differences)
    }

    @Test
    fun actualIsNeverShownAsDeletedBeforeTheServerConfirmsIt() {
        val formatAmount: (Long) -> String = { "minor:$it" }

        assertEquals("Not checked", conflictActualAmountText(false, false, true, null, formatAmount))
        assertEquals("Checking…", conflictActualAmountText(false, true, true, null, formatAmount))
        assertEquals("Deleted", conflictActualAmountText(true, false, true, null, formatAmount))
        assertEquals("minor:1250", conflictActualAmountText(true, false, false, 1250, formatAmount))
    }

    @Test
    fun conflictDetailsShowBothVersionsForEachChangedField() {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                ConflictComparisonCard(
                    listOf(
                        ConflictFieldComparison("Payee", "Target", "Costco"),
                        ConflictFieldComparison("Notes", "Hardware", "Groceries"),
                        ConflictFieldComparison("Cleared", "Cleared", "Uncleared"),
                    ),
                )
            }
        }

        listOf("Changed details", "Payee", "Target", "Costco", "Notes", "Hardware", "Groceries", "Uncleared")
            .forEach { composeTestRule.onNodeWithText(it).assertExists() }
    }
}
