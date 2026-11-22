@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.categoryCountLabel
import app.tandemforactual.ui.core.state.categoryIconFor
import app.tandemforactual.ui.core.state.currencyFractionDigitsForDisplay
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.toggleBudgetSection
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.money.PrivacyAmountText
import app.tandemforactual.ui.design.rows.CollapsibleSectionCard
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.design.theme.tandemRevealEnter
import app.tandemforactual.ui.design.theme.tandemRevealExit

/** Collapsible section listing the pinned budget categories. */
@Composable
internal fun BudgetPinnedCategoriesSection(
    state: AppState,
    navigation: AppNavigation,
    pinnedCategories: List<Pair<LiveCategoryGroupSummary, LiveCategorySummary>>,
    pinnedSectionKey: String,
    pinnedExpanded: Boolean,
    sectionSpacing: Dp,
) {
    Column {
        CollapsibleSectionCard(
            "Pinned",
            categoryCountLabel(pinnedCategories.size),
            expanded = pinnedExpanded,
            onToggleExpanded = { state.toggleBudgetSection(pinnedSectionKey) },
        ) {}
        AnimatedVisibility(
            visible = pinnedExpanded,
            enter = tandemRevealEnter(tandemReduceMotion),
            exit = tandemRevealExit(tandemReduceMotion),
        ) {
            Column(
                Modifier.padding(top = sectionSpacing),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing),
            ) {
                pinnedCategories.forEach { (group, category) ->
                    PinnedCategoryCard(state, navigation, group, category)
                }
            }
        }
    }
}

@Composable
private fun PinnedCategoryCard(
    state: AppState,
    navigation: AppNavigation,
    group: LiveCategoryGroupSummary,
    category: LiveCategorySummary,
) {
    SurfaceCard {
        ListRow(
            category.name,
            group.name,
            leading = RowLeading.Icon(state.categoryIconFor(category.id, category.name)),
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
                ) {
                    if (category.pendingChanges) PendingBudgetSyncIcon()
                    PrivacyAmountText(
                        state.formatCurrency(category.balanceMinor),
                        privacyModeEnabled = state.privacyModeEnabled,
                        fractionDigits = state.currencyFractionDigitsForDisplay,
                        color = tandemPalette.text,
                        fontSize = TandemTypography.compactBody,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            onClick = {
                state.selectedCategoryGroupId = group.id
                state.selectedCategoryId = category.id
                navigation.open(AppRoute.CATEGORY_DETAIL)
            },
        )
    }
}
