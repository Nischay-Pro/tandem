@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.currency

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.Lucide

/** Picks the currency an account is kept in. */
@Composable
internal fun AccountCurrencyDialog(
    target: LiveAccountSummary?,
    budgetCurrency: String,
    currencies: Map<String, String>,
    onDismiss: () -> Unit,
    onPick: (LiveAccountSummary, String) -> Unit,
) {
    TandemChoiceDialog(visible = target != null, onDismissRequest = onDismiss) {
        SheetCard(
            scrollBody = false,
            spacing = TandemSpacing.small,
            header = {
                TandemModalHeader(target?.name.orEmpty(), onClose = onDismiss, leadingIcon = Lucide.Coins)
            },
        ) {
            Column(Modifier.weight(1f, fill = false).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                (listOf(budgetCurrency) + COMMON_CURRENCIES.filter { it != budgetCurrency }).forEachIndexed { index, code ->
                    if (index > 0) DividerLine()
                    val selected = (currencies[target?.id] ?: budgetCurrency) == code
                    ListRow(
                        code,
                        if (code == budgetCurrency) "The budget’s currency" else null,
                        if (selected) "Current" else null,
                        leading = RowLeading.None,
                        options = ListRowOptions(dense = true, showChevron = false),
                        onClick = {
                            val account = target ?: return@ListRow
                            onPick(account, code)
                        },
                    )
                }
            }
        }
    }
}
