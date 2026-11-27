@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.ui.DatePickerDialog
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.sheets.AmountConfirmationDialog
import app.tandemforactual.ui.design.sheets.ChoiceSheet
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.sheets.FutureTransactionDateConfirmationDialog
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.currentWindowSizeInfo
import app.tandemforactual.ui.feature.transactions.components.NewTransactionForm
import app.tandemforactual.ui.feature.transactions.components.TransactionFormPickerState
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Sheets, dialogs, pickers and the amount keypad keep every control inside narrow and floating windows. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NarrowWindowSheetsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val enterAmount =
        SemanticsMatcher("opens the amount keypad") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Enter amount" }

    @Test
    @Config(qualifiers = WindowPresets.TABLET_THIRD)
    fun tabletThird() = sweep()

    @Test
    @Config(qualifiers = WindowPresets.TALL_TABLET_THIRD)
    fun tallTabletThird() = sweep()

    @Test
    @Config(qualifiers = WindowPresets.FOLD_HALF)
    fun foldableHalf() = sweep()

    @Test
    @Config(qualifiers = WindowPresets.FLOATING)
    fun floatingWindow() = sweep()

    @Test
    @Config(qualifiers = WindowPresets.VERY_NARROW)
    fun veryNarrowWindow() = sweep()

    private fun sweep() {
        val shown = mutableIntStateOf(0)
        val session = TandemMobileSession(TandemApiClient(), null, { 0L })
        var size = ""
        composeTestRule.setContent {
            val window = currentWindowSizeInfo()
            size = "${window.widthDp}x${window.heightDp}dp"
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                key(shown.intValue) { surfaces[shown.intValue].content(session) }
            }
        }
        val problems =
            surfaces.indices.flatMap { index ->
                composeTestRule.runOnIdle { shown.intValue = index }
                composeTestRule.waitForIdle()
                val name = surfaces[index].name
                if (name == KEYPAD) {
                    composeTestRule.onNode(enterAmount).performClick()
                    composeTestRule.waitForIdle()
                }
                composeTestRule.windowFitProblems(name)
            }
        assertTrue(problems.isEmpty(), "At $size:\n" + problems.joinToString("\n"))
    }
}

private const val KEYPAD = "Amount keypad"
private val surfaces =
    listOf(
        Surface("Task sheet") { TaskSheet() },
        Surface("Choice sheet") {
            ChoiceSheet(
                title = "Accounts to include in this report",
                options = (1..12).map { "id$it" to "Everyday joint chequing account number $it at the credit union" },
                initial = setOf("id1"),
                onDismiss = {},
                onDone = {},
            )
        },
        Surface("Erase confirmation") {
            DestructiveConfirmationDialog(
                title = "Unlink this device and erase its data?",
                message = "Goals, shared bills and saved reports on this device are erased. Your budgets in Actual stay unchanged.",
                confirmLabel = "Unlink and erase data",
                dismissLabel = "Keep everything",
                onDismissRequest = {},
                onConfirm = {},
            )
        },
        Surface("Amount confirmation") {
            AmountConfirmationDialog(visible = true, formattedAmount = "-$98,765,432,109.99", onDismissRequest = {}, onConfirm = {})
        },
        Surface("Future date confirmation") {
            FutureTransactionDateConfirmationDialog(
                visible = true,
                formattedDate = "Wednesday, 30 December 2026",
                onDismissRequest = {},
                onConfirm = {},
            )
        },
        Surface("Date picker") { DatePickerDialog(visible = true, initialDate = "2026-11-26", onDismiss = {}, onSelect = {}) },
        Surface(KEYPAD) { session -> NewTransaction(session) },
    )

private class Surface(
    val name: String,
    val content: @Composable (TandemMobileSession) -> Unit,
)

@Composable
private fun TaskSheet() {
    TandemTaskSheet(visible = true, onDismissRequest = {}, onDismissed = {}) {
        SheetCard(
            header = { TandemModalHeader("Move money between the household categories", onClose = {}) },
            actions = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    SecondaryButton("Cancel", Modifier.weight(1f), onClick = {})
                    PrimaryButton("Move money", modifier = Modifier.weight(1f), onClick = {})
                }
            },
        ) {
            repeat(8) { Text("Groceries and household supplies for the family, line $it") }
        }
    }
}

@Composable
private fun NewTransaction(session: TandemMobileSession) {
    val navigation =
        AppNavigation(open = {}, reset = {}, replaceTop = {}, newTransaction = {}, selectTab = {}, back = {}, backToList = {})
    NewTransactionForm(
        AppState(),
        navigation,
        session,
        emptyList(),
        TransactionFormPickerState(),
        onCreateTransaction = {},
    )
}
