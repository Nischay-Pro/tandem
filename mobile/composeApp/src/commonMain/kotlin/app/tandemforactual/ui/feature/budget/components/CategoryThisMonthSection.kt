@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.setBudgetCategoryCarryover
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.categoryIconChoice
import app.tandemforactual.ui.core.state.categoryIconFor
import app.tandemforactual.ui.core.state.currencyFractionDigitsForDisplay
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.design.money.amountAnnotatedString
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.budget.openCategoryActivity
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.WalletCards
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** "This month" card with the category's planned amount, activity, rollover and icon. */
@Composable
internal fun CategoryThisMonthSection(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    category: LiveCategorySummary,
    sheets: CategoryDetailSheetState,
    moveMoneyCategories: List<Pair<String, LiveCategorySummary>>,
    isLiveBudget: Boolean,
    isTrackingBudget: Boolean,
    scope: CoroutineScope,
    isIncome: Boolean = false,
) {
    SectionCard("This month") {
        val planTitle = categoryPlanTitle(isIncome, isTrackingBudget)
        if (planTitle != null) {
            AssignedAmountRow(state, mobileSession, category, isLiveBudget, planTitle) {
                sheets.assignedAmountDraft = state.formatAmount(category.assignedMinor)
                mobileSession.feedback.clearBudgetActionMessage()
                sheets.showAssignedAmountDialog = true
            }
            DividerLine()
        }
        ListRow(
            "Activity",
            "${state.selectedMonth} · ${state.formatCurrency(category.activityMinor)}",
            leading = RowLeading.Icon(Lucide.Receipt),
            onClick = { openCategoryActivity(state, navigation, category.id, category.name) },
        )
        if (isLiveBudget) {
            DividerLine()
            RolloverRow(state, mobileSession, category, scope, isTrackingBudget)
        }
        if (isLiveBudget && !isTrackingBudget) {
            DividerLine()
            ListRow(
                "Move money",
                leading = RowLeading.Icon(Lucide.WalletCards),
                onClick = {
                    sheets.moveMoneyFromId = category.id
                    sheets.moveMoneyToId =
                        moveMoneyCategories
                            .firstOrNull { it.second.id != category.id }
                            ?.second
                            ?.id
                            .orEmpty()
                    sheets.moveMoneyAmountDraft = ""
                    mobileSession.feedback.clearBudgetActionMessage()
                    sheets.showMoveMoneyDialog = true
                },
            )
        }
        DividerLine()
        ListRow(
            "Icon",
            state.categoryIconChoice(category.id) ?: "Automatic",
            leading = RowLeading.Icon(state.categoryIconFor(category.id, category.name)),
        )
        if (!isIncome) {
            DividerLine()
            if (isTrackingBudget) {
                ListRow("Remaining", "Budgeted plus activity and any rollover", state.formatCurrency(category.balanceMinor))
            } else {
                ListRow("Available", "Assigned plus activity", state.formatCurrency(category.balanceMinor))
            }
        }
    }
}

/** The name of a category's monthly plan; envelope income has none because income isn't assigned. */
internal fun categoryPlanTitle(
    isIncome: Boolean,
    isTrackingBudget: Boolean,
): String? =
    when {
        isIncome && isTrackingBudget -> "Expected"
        isIncome -> null
        isTrackingBudget -> "Budgeted"
        else -> "Assigned"
    }

@Composable
internal fun RolloverRow(
    state: AppState,
    mobileSession: TandemMobileSession,
    category: LiveCategorySummary,
    scope: CoroutineScope,
    isTrackingBudget: Boolean = false,
) {
    ToggleRow(
        if (isTrackingBudget) "Roll the balance into next month" else "Roll overspending into next month",
        if (isTrackingBudget) {
            "What’s left over, or overspent, carries into next month’s balance for this category"
        } else {
            "When this category goes over, the shortfall carries into next month’s balance instead of coming out of money to budget"
        },
        checked = category.carryover,
        enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
        leadingIcon = Lucide.RefreshCw,
    ) { enabled ->
        scope.launch {
            mobileSession.budgetEdits.setBudgetCategoryCarryover(
                state.selectedBudgetId,
                category.id,
                state.selectedMonthId,
                enabled,
                state.encryptedPassword.takeIf { it.isNotBlank() },
            )
        }
    }
}

@Composable
private fun AssignedAmountRow(
    state: AppState,
    mobileSession: TandemMobileSession,
    category: LiveCategorySummary,
    isLiveBudget: Boolean,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TandemLayout.listRowMinHeight)
            .clickable(
                enabled = isLiveBudget && !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                role = Role.Button,
                onClickLabel = "Edit ${title.lowercase()} amount",
                onClick = onClick,
            ).padding(
                horizontal = TandemLayout.listRowHorizontalInset,
                vertical = TandemLayout.listRowVerticalInset,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemLayout.listRowGap),
    ) {
        Box(
            Modifier
                .size(TandemLayout.listRowLeadingSize)
                .clip(RoundedCornerShape(TandemShapes.medium))
                .background(tandemPalette.raised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Lucide.WalletCards,
                contentDescription = null,
                modifier = Modifier.size(TandemLayout.listRowIconSize),
                tint = tandemPalette.accent,
            )
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.body,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            amountAnnotatedString(
                state.formatCurrency(category.assignedMinor),
                state.currencyFractionDigitsForDisplay,
            ),
            color = tandemPalette.text,
            fontSize = TandemTypography.compactBody,
        )
        if (isLiveBudget) {
            Icon(
                Lucide.Pencil,
                contentDescription = "Edit ${title.lowercase()} amount",
                modifier = Modifier.size(19.dp),
                tint = if (state.readOnlyMode) tandemPalette.muted else tandemPalette.accent,
            )
        }
    }
}
