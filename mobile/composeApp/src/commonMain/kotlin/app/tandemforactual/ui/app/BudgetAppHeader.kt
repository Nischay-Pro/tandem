@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.reportMonthLabel
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.MainCloudStatus
import app.tandemforactual.ui.design.buttons.MIN_TOUCH_TARGET
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.reports.shiftReportMonthId
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus

/** Budget tab header: budget name, sync caption, add and sync buttons, options menu, and the month switcher. */
@Composable
internal fun BudgetAppHeader(
    route: AppRoute,
    state: AppState,
    headerTitle: String,
    headerMinHeight: Dp,
    syncStatus: MainCloudStatus,
    pendingWriteStatuses: Collection<String>,
    onSync: () -> Unit,
    onBudget: () -> Unit,
    syncCaption: String?,
    onAddTransaction: (() -> Unit)?,
    addTransactionEnabled: Boolean,
    onOpenMenu: (() -> Unit)?,
) {
    val palette = tandemPalette
    var headerMenuExpanded by rememberSaveable(route) { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = headerMinHeight)
            .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            Row(
                modifier = Modifier.padding(bottom = 4.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderMenuButton(route, onOpenMenu)
                BudgetHeaderTitle(headerTitle, syncCaption, syncStatus)
                if (onAddTransaction != null) {
                    TandemIconButton(
                        Lucide.Plus,
                        if (addTransactionEnabled) "Add transaction" else "Read-only mode is on",
                        enabled = addTransactionEnabled,
                        iconSizeDp = 20,
                        onClick = onAddTransaction,
                    )
                }
                MainCloudStatusButton(syncStatus, pendingWriteStatuses, onSync)
                Box {
                    TandemIconButton(
                        Lucide.EllipsisVertical,
                        "Budget options",
                        onClick = { headerMenuExpanded = true },
                    )
                    BudgetHeaderMenu(
                        expanded = headerMenuExpanded,
                        state = state,
                        onDismiss = { headerMenuExpanded = false },
                        onSwitchBudget = {
                            headerMenuExpanded = false
                            onBudget()
                        },
                    )
                }
            }
            BudgetMonthSwitcher(state)
        }
    }
}

/** Budget name with the sync caption below it. */
@Composable
private fun RowScope.BudgetHeaderTitle(
    headerTitle: String,
    syncCaption: String?,
    syncStatus: MainCloudStatus,
) {
    val palette = tandemPalette
    Column(Modifier.weight(1f)) {
        Text(
            headerTitle,
            color = palette.text,
            fontSize = TandemTypography.periodTitle,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        syncCaption?.let { caption ->
            Text(
                caption,
                color = if (syncStatus == MainCloudStatus.ERROR) palette.negative else palette.muted,
                fontSize = TandemTypography.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Selected month with the month picker and previous and next month buttons. */
@Composable
private fun BudgetMonthSwitcher(state: AppState) {
    val palette = tandemPalette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .heightIn(min = MIN_TOUCH_TARGET)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "Choose month",
                    onClick = { state.isBudgetMonthPickerOpen = true },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
        ) {
            Text(
                state.selectedMonth,
                color = palette.text,
                fontSize = TandemTypography.action,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(Lucide.ChevronDown, contentDescription = "Choose month", modifier = Modifier.size(20.dp), tint = palette.muted)
        }
        Spacer(Modifier.weight(1f))
        TandemIconButton(
            Lucide.ChevronLeft,
            "Previous month",
            sizeDp = 36,
            iconSizeDp = 20,
            onClick = { state.shiftBudgetMonth(-1) },
        )
        TandemIconButton(
            Lucide.ChevronRight,
            "Next month",
            sizeDp = 36,
            iconSizeDp = 20,
            onClick = { state.shiftBudgetMonth(1) },
        )
    }
}

private fun AppState.shiftBudgetMonth(offset: Int) {
    val monthId = shiftReportMonthId(selectedMonthId, offset) ?: return
    selectedMonthId = monthId
    selectedMonth = reportMonthLabel(monthId)
}
