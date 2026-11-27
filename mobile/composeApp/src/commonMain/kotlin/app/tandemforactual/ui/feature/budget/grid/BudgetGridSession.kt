package app.tandemforactual.ui.feature.budget.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import app.tandemforactual.ui.core.format.reportMonthLabel
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.setBudgetCategoryAmount
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The grid's link to the open budget: amounts go through the outbox, so they show as waiting until they sync. */
@Suppress("DEPRECATION")
@Composable
internal fun rememberBudgetGridIo(
    state: AppState,
    mobileSession: TandemMobileSession,
): BudgetGridIo {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    return remember(state, mobileSession, scope, clipboard) { SessionGridIo(state, mobileSession, scope, clipboard) }
}

@Suppress("DEPRECATION")
private class SessionGridIo(
    private val state: AppState,
    private val mobileSession: TandemMobileSession,
    private val scope: CoroutineScope,
    private val clipboard: ClipboardManager,
) : BudgetGridIo {
    override val editable: Boolean get() = !state.readOnlyMode

    override fun format(amountMinor: Long): String = state.formatAmount(amountMinor)

    override fun parse(text: String): Long? = state.parseAmountMinorUnits(text)

    override fun write(writes: List<GridWrite>) {
        val budgetId = state.selectedBudgetId
        val password = state.encryptedPassword.takeIf { it.isNotBlank() }
        scope.launch {
            writes.forEach { write ->
                mobileSession.budgetEdits.setBudgetCategoryAmount(budgetId, write.categoryId, write.monthId, write.amountMinor, password)
            }
        }
    }

    override fun showMonth(monthId: String) {
        state.selectedMonthId = monthId
        state.selectedMonth = reportMonthLabel(monthId)
    }

    override fun copy(text: String) = clipboard.setText(AnnotatedString(text))

    override fun paste(): String? = clipboard.getText()?.text
}
