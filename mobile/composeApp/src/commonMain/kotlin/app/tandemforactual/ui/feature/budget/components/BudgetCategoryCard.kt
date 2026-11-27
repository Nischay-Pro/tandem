@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.categoryIconFor
import app.tandemforactual.ui.core.state.currencyFractionDigitsForDisplay
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.ProgressBar
import app.tandemforactual.ui.design.money.AvailableAmountPill
import app.tandemforactual.ui.design.money.PrivacyAmountText
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.budget.BudgetItemReorderActions
import app.tandemforactual.ui.feature.budget.categoryFundsMinor
import app.tandemforactual.ui.feature.budget.categoryProgress
import app.tandemforactual.ui.feature.budget.categorySpentMinor

/** Reorder callbacks shared by both budget category card styles. */
internal class BudgetCategoryReorder(
    val isReordering: Boolean = false,
    val canMoveUp: Boolean = false,
    val canMoveDown: Boolean = false,
    val onMoveUp: () -> Unit = {},
    val onMoveDown: () -> Unit = {},
    val onDragStarted: (() -> Unit)? = null,
    val onDragBy: ((Float) -> Unit)? = null,
    val onDragStopped: (() -> Unit)? = null,
)

/** The words a category row uses: its plan ("Assigned", "Budgeted", "Expected", none for envelope income) and what happened. */
internal class BudgetCategoryWords(
    val isIncome: Boolean,
    val plan: String?,
    val actual: String,
    val left: String = "available",
)

internal fun budgetCategoryWords(
    isIncome: Boolean,
    isTrackingBudget: Boolean,
): BudgetCategoryWords =
    when {
        isIncome -> BudgetCategoryWords(true, "Expected".takeIf { isTrackingBudget }, "Received")
        isTrackingBudget -> BudgetCategoryWords(false, "Budgeted", "Spent", "remaining")
        else -> BudgetCategoryWords(false, "Assigned", "Spent")
    }

/** A budget category row, drawn with or without a progress bar. */
@Composable
internal fun BudgetCategoryCard(
    state: AppState,
    category: LiveCategorySummary,
    compact: Boolean,
    modifier: Modifier = Modifier,
    reorder: BudgetCategoryReorder = BudgetCategoryReorder(),
    words: BudgetCategoryWords = budgetCategoryWords(isIncome = false, isTrackingBudget = false),
    onClick: () -> Unit,
) {
    if (!state.budgetShowProgressBars) {
        BudgetCategoryListCard(state, category, compact, modifier, reorder, words, onClick)
    } else if (words.isIncome) {
        IncomeCategoryProgressCard(state, category, modifier, reorder, words, onClick)
    } else {
        BudgetCategoryProgressCard(state, category, modifier, reorder, onClick)
    }
}

@Composable
private fun CategoryReorderActions(
    state: AppState,
    category: LiveCategorySummary,
    reorder: BudgetCategoryReorder,
) {
    BudgetItemReorderActions(
        itemName = category.name,
        canMoveUp = reorder.canMoveUp,
        canMoveDown = reorder.canMoveDown,
        enabled = !state.readOnlyMode,
        onMoveUp = reorder.onMoveUp,
        onMoveDown = reorder.onMoveDown,
        onDragStarted = reorder.onDragStarted,
        onDragBy = reorder.onDragBy,
        onDragStopped = reorder.onDragStopped,
    )
}

@Composable
private fun BudgetCategoryListCard(
    state: AppState,
    category: LiveCategorySummary,
    compact: Boolean,
    modifier: Modifier,
    reorder: BudgetCategoryReorder,
    words: BudgetCategoryWords,
    onClick: () -> Unit,
) {
    SurfaceCard(modifier = modifier, animateSizeChanges = false) {
        ListRow(
            category.name,
            leading = RowLeading.Icon(state.categoryIconFor(category.id, category.name)),
            options = ListRowOptions(dense = compact),
            subtitleContent = { CategoryAssignedAndSpent(state, category, words) },
            onClick = onClick,
            trailingContent = {
                if (reorder.isReordering) {
                    CategoryReorderActions(state, category, reorder)
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
                    ) {
                        if (category.pendingChanges) PendingBudgetSyncIcon()
                        if (words.isIncome) {
                            PrivacyAmountText(
                                state.formatCurrency(category.activityMinor),
                                privacyModeEnabled = state.privacyModeEnabled,
                                fractionDigits = state.currencyFractionDigitsForDisplay,
                                color = tandemPalette.positive,
                                fontSize = TandemTypography.compactBody,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else {
                            AvailableAmountPill(
                                state.formatCurrency(category.balanceMinor),
                                isOverspent = category.balanceMinor < 0,
                                privacyModeEnabled = state.privacyModeEnabled,
                                fractionDigits = state.currencyFractionDigitsForDisplay,
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun CategoryAssignedAndSpent(
    state: AppState,
    category: LiveCategorySummary,
    words: BudgetCategoryWords,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
    ) {
        words.plan?.let { plan ->
            Text(plan, color = tandemPalette.muted, fontSize = TandemTypography.caption)
            PrivacyAmountText(
                state.formatCurrency(category.assignedMinor),
                privacyModeEnabled = state.privacyModeEnabled,
                fractionDigits = state.currencyFractionDigitsForDisplay,
                color = tandemPalette.muted,
                fontSize = TandemTypography.caption,
            )
        }
        Text(
            if (words.plan == null) words.actual else "· ${words.actual}",
            color = tandemPalette.muted,
            fontSize = TandemTypography.caption,
        )
        PrivacyAmountText(
            state.formatCurrency(kotlin.math.abs(category.activityMinor)),
            privacyModeEnabled = state.privacyModeEnabled,
            fractionDigits = state.currencyFractionDigitsForDisplay,
            color = tandemPalette.muted,
            fontSize = TandemTypography.caption,
        )
    }
}

@Composable
private fun BudgetCategoryProgressCard(
    state: AppState,
    category: LiveCategorySummary,
    modifier: Modifier,
    reorder: BudgetCategoryReorder,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val overspent = category.balanceMinor < 0L
    val statusColor = if (overspent) palette.negative else palette.positive
    SurfaceCard(
        modifier = modifier,
        onClick = onClick,
        animateSizeChanges = false,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TandemLayout.listRowHorizontalInset, vertical = TandemSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemLayout.listRowGap),
        ) {
            Box(
                Modifier
                    .size(TandemLayout.listRowLeadingSize)
                    .clip(RoundedCornerShape(TandemShapes.medium))
                    .background(palette.raised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    state.categoryIconFor(category.id, category.name),
                    contentDescription = null,
                    modifier = Modifier.size(TandemLayout.listRowIconSize),
                    tint = palette.accent,
                )
            }
            CategoryProgressDetails(state, category, overspent)
            if (reorder.isReordering) {
                CategoryReorderActions(state, category, reorder)
            } else {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                    PrivacyAmountText(
                        state.formatCurrency(category.balanceMinor),
                        privacyModeEnabled = state.privacyModeEnabled,
                        fractionDigits = state.currencyFractionDigitsForDisplay,
                        color = statusColor,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (overspent) "over" else "remaining",
                        color = statusColor,
                        fontSize = TandemTypography.micro,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.CategoryProgressDetails(
    state: AppState,
    category: LiveCategorySummary,
    overspent: Boolean,
) {
    val palette = tandemPalette
    val spentMinor = categorySpentMinor(category.activityMinor)
    val progress = categoryProgress(category.balanceMinor, category.activityMinor)
    Column(
        Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                category.name,
                modifier = Modifier.weight(1f),
                color = palette.text,
                fontSize = TandemTypography.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (category.pendingChanges) PendingBudgetSyncIcon()
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
        ) {
            PrivacyAmountText(
                state.formatCurrency(spentMinor),
                privacyModeEnabled = state.privacyModeEnabled,
                fractionDigits = state.currencyFractionDigitsForDisplay,
                color = palette.muted,
                fontSize = TandemTypography.caption,
            )
            Text("spent of", color = palette.muted, fontSize = TandemTypography.caption)
            PrivacyAmountText(
                state.formatCurrency(categoryFundsMinor(category.balanceMinor, category.activityMinor)),
                privacyModeEnabled = state.privacyModeEnabled,
                fractionDigits = state.currencyFractionDigitsForDisplay,
                color = palette.muted,
                fontSize = TandemTypography.caption,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = 0.16f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (overspent) palette.negative else palette.accent),
            )
        }
    }
}

@Composable
private fun IncomeCategoryProgressCard(
    state: AppState,
    category: LiveCategorySummary,
    modifier: Modifier,
    reorder: BudgetCategoryReorder,
    words: BudgetCategoryWords,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val received = category.activityMinor
    val expected = category.assignedMinor
    val progress =
        when {
            expected > 0 -> (received.toDouble() / expected).toFloat().coerceIn(0f, 1f)
            received > 0 -> 1f
            else -> 0f
        }
    SurfaceCard(modifier = modifier, onClick = onClick, animateSizeChanges = false) {
        ListRow(
            category.name,
            if (state.privacyModeEnabled) {
                null
            } else if (words.plan == null) {
                "${state.formatCurrency(received)} received"
            } else {
                "${state.formatCurrency(received)} received of ${state.formatCurrency(expected)} expected"
            },
            leading = RowLeading.Icon(state.categoryIconFor(category.id, category.name)),
            trailingContent = {
                if (reorder.isReordering) {
                    CategoryReorderActions(state, category, reorder)
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
                    ) {
                        if (category.pendingChanges) PendingBudgetSyncIcon()
                        PrivacyAmountText(
                            state.formatCurrency(received),
                            privacyModeEnabled = state.privacyModeEnabled,
                            fractionDigits = state.currencyFractionDigitsForDisplay,
                            color = palette.positive,
                            fontSize = TandemTypography.compactBody,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            },
        )
        if (words.plan != null) {
            Box(
                Modifier.padding(
                    start = TandemLayout.listRowHorizontalInset,
                    end = TandemLayout.listRowHorizontalInset,
                    bottom = TandemSpacing.medium,
                ),
            ) {
                ProgressBar(progress, palette.positive)
            }
        }
    }
}
