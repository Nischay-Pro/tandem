@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.session.dragReorderItemModifier
import app.tandemforactual.ui.core.session.reorderAccessibilityAction
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.categoryCountLabel
import app.tandemforactual.ui.core.state.currencyFractionDigitsForDisplay
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.toggleBudgetSection
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.money.PrivacyAmountText
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.budget.BudgetItemReorderActions
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide

/** Draggable header card for a budget category group. */
@Composable
internal fun LazyItemScope.BudgetGroupCard(
    group: LiveCategoryGroupSummary,
    groupSectionKey: String,
    groupExpanded: Boolean,
    isReordering: Boolean,
    groupIndex: Int,
    displayGroups: List<LiveCategoryGroupSummary>,
    context: BudgetGroupListContext,
) {
    val state = context.state
    val reorder = context.reorder
    val actions = context.actions
    val isLiveBudget = context.isLiveBudget
    val groupDrag = reorder.groupDrag
    SurfaceCard(
        dragReorderItemModifier(
            itemId = group.id,
            dragState = groupDrag,
            reduceMotion = tandemReduceMotion,
            dragEnabled = isLiveBudget && !state.readOnlyMode && displayGroups.size > 1,
            onDragStarted = { actions.startGroupDrag(group.id) },
            onDragBy = { groupDrag.dragBy(it) },
            onDragStopped = { actions.dropGroupDrag(group.id) },
        ).combinedClickable(
            role = Role.Button,
            onClickLabel = "Open ${group.name}",
            onClick = {
                if (reorder.selectedGroupOrderId != null) {
                    reorder.selectedGroupOrderId = null
                } else {
                    state.selectedCategoryGroupId = group.id
                    context.navigation.open(AppRoute.CATEGORY_GROUP)
                }
            },
        ).then(
            if (isLiveBudget && !state.readOnlyMode && displayGroups.size > 1) {
                Modifier.reorderAccessibilityAction(group.name) { actions.selectGroup(group.id) }
            } else {
                Modifier
            },
        ),
        animateSizeChanges = false,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BudgetGroupHeading(state, group, isReordering, context.isTrackingBudget)
                if (isReordering && isLiveBudget) {
                    BudgetItemReorderActions(
                        canMoveUp = groupIndex > 0,
                        canMoveDown = groupIndex >= 0 && groupIndex < displayGroups.lastIndex,
                        enabled = !state.readOnlyMode,
                        itemName = group.name,
                        onDragStarted = { actions.startGroupDrag(group.id) },
                        onDragBy = { groupDrag.dragBy(it) },
                        onDragStopped = { actions.dropGroupDrag(group.id) },
                        onMoveUp = { actions.enqueueGroupMove(group.id, -1) },
                        onMoveDown = { actions.enqueueGroupMove(group.id, 1) },
                    )
                } else {
                    BudgetGroupBalance(state, group, context.isTrackingBudget, groupExpanded, groupSectionKey)
                }
            }
            DividerLine()
        }
    }
}

@Composable
private fun RowScope.BudgetGroupHeading(
    state: AppState,
    group: LiveCategoryGroupSummary,
    isReordering: Boolean,
    isTrackingBudget: Boolean,
) {
    val palette = tandemPalette
    Column(
        Modifier
            .weight(1f)
            .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
    ) {
        Text(
            group.name,
            color = palette.text,
            fontSize = TandemTypography.body,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        when {
            isReordering || isTrackingBudget -> {
                Text(
                    categoryCountLabel(group.categories.size),
                    color = palette.muted,
                    fontSize = TandemTypography.caption,
                    maxLines = 1,
                )
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                    PrivacyAmountText(
                        state.formatCurrency(group.assignedMinor),
                        privacyModeEnabled = state.privacyModeEnabled,
                        fractionDigits = state.currencyFractionDigitsForDisplay,
                        color = palette.muted,
                        fontSize = TandemTypography.caption,
                    )
                    Text(
                        "assigned · ${categoryCountLabel(group.categories.size)}",
                        color = palette.muted,
                        fontSize = TandemTypography.caption,
                        maxLines = 1,
                    )
                }
            }
        }
        if (state.budgetShowProgressBars && !isTrackingBudget) {
            BudgetGroupProgressBar(group)
        }
    }
}

@Composable
private fun BudgetGroupProgressBar(group: LiveCategoryGroupSummary) {
    val palette = tandemPalette
    val groupSpent = kotlin.math.abs(group.activityMinor)
    val groupRemaining = group.balanceMinor.coerceAtLeast(0L)
    val groupTotal = maxOf(group.assignedMinor, groupSpent + groupRemaining, groupSpent)
    val groupProgress =
        if (groupTotal > 0L) {
            (groupSpent.toDouble() / groupTotal.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(palette.accent.copy(alpha = 0.18f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(groupProgress)
                .height(4.dp)
                .clip(CircleShape)
                .background(
                    if (group.balanceMinor < 0L) palette.warning else palette.accent,
                ),
        )
    }
}

@Composable
private fun BudgetGroupBalance(
    state: AppState,
    group: LiveCategoryGroupSummary,
    isTrackingBudget: Boolean,
    groupExpanded: Boolean,
    groupSectionKey: String,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
    ) {
        PrivacyAmountText(
            state.formatCurrency(group.balanceMinor),
            privacyModeEnabled = state.privacyModeEnabled,
            fractionDigits = state.currencyFractionDigitsForDisplay,
            color = tandemPalette.accent,
            fontSize = TandemTypography.compactBody,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (isTrackingBudget) "Balance" else "Available",
            color = tandemPalette.muted,
            fontSize = TandemTypography.micro,
            maxLines = 1,
        )
    }
    TandemIconButton(
        if (groupExpanded) Lucide.ChevronDown else Lucide.ChevronRight,
        if (groupExpanded) "Collapse ${group.name}" else "Expand ${group.name}",
        onClick = { state.toggleBudgetSection(groupSectionKey) },
    )
}
