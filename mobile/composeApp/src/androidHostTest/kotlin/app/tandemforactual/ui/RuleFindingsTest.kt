package app.tandemforactual.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tandemforactual.data.actual.RuleFinding
import app.tandemforactual.data.actual.RuleFindingKind
import app.tandemforactual.data.actual.RuleSeverity
import app.tandemforactual.ui.core.format.RuleText
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.feature.checkup.RuleFindingDialog
import app.tandemforactual.ui.feature.checkup.RuleFindingsSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonObject
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RuleFindingsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val rules =
        mapOf(
            "coffee" to RuleText("Imported payee is Coffee", "Set category to Eating out", null),
            "tea" to RuleText("Imported payee is Tea", "Set category to Eating out", "Runs last"),
            "never" to RuleText("Amount is more than 5 · Amount is less than 1", "Add “x” to end of notes", null),
        )

    @Test
    fun listsFindingsWorstFirstWithTheRuleAsTheEditorShowsIt() {
        val opened = mutableListOf<RuleFinding>()
        val never = RuleFinding(RuleFindingKind.CONTRADICTORY, RuleSeverity.PROBLEM, "never", fields = listOf("amount"))
        val near = RuleFinding(RuleFindingKind.NEAR_DUPLICATE, RuleSeverity.SUGGESTION, "coffee", listOf("tea"), listOf("imported_payee"))
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                Column { RuleFindingsSection(listOf(never, near), rules) { opened += it } }
            }
        }
        composeTestRule.onNodeWithText("2 rules to check").assertExists()
        composeTestRule.onNodeWithText("1 won’t work as written").assertExists()
        composeTestRule.onNodeWithText("Needs fixing").assertExists()
        composeTestRule.onNodeWithText("Amount is more than 5 · Amount is less than 1 → Add “x” to end of notes").assertExists()
        composeTestRule.onNodeWithText("Could be one rule").performClick()
        assertEquals(listOf(near), opened)
    }

    @Test
    fun aFindingExplainsItselfAndLinksToEditMergeAndIgnore() {
        val edited = mutableListOf<String>()
        val merged = mutableListOf<RuleFinding>()
        val ignored = mutableListOf<RuleFinding>()
        val near =
            RuleFinding(
                RuleFindingKind.NEAR_DUPLICATE,
                RuleSeverity.SUGGESTION,
                "coffee",
                listOf("tea"),
                listOf("imported_payee"),
                merged = JsonObject(emptyMap()),
            )
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                RuleFindingDialog(near, rules, canEdit = true, {}, { edited += it }, { merged += it }, { ignored += it })
            }
        }
        composeTestRule.onNodeWithText("Suggestion").assertExists()
        composeTestRule
            .onNodeWithText(
                "These rules make the same changes and differ only in the imported payee they look for. " +
                    "One rule that matches any of them does the same job.",
            ).assertExists()
        composeTestRule.onNodeWithText("Rule 2 · Runs last").assertExists()
        composeTestRule.onNodeWithText("Imported payee is Tea").performClick()
        composeTestRule.onNodeWithText("Merge into one rule").performClick()
        composeTestRule.onNodeWithText("Ignore").performClick()
        assertEquals(listOf("tea"), edited)
        assertEquals(listOf(near), merged)
        assertEquals(listOf(near), ignored)
    }
}
