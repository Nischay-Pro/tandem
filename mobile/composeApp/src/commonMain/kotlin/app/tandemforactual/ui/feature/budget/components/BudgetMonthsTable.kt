@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import app.tandemforactual.ui.core.format.reportMonthLabel
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.currencyFractionDigitsForDisplay
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.money.PrivacyAmountText
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.budget.BudgetMonthLabelWidth
import app.tandemforactual.ui.feature.budget.BudgetMonthsView
import kotlin.math.abs

/** The month headers, kept in view while scrolling, then one card per group with a row per category. */
@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.budgetMonthsTableItems(
    context: BudgetGroupListContext,
    months: BudgetMonthsView,
    groups: List<LiveCategoryGroupSummary>,
) {
    stickyHeader(key = "budget-months-header") { BudgetMonthsHeader(context.state, months) }
    groups.forEach { group ->
        item(key = "budget-months-group:${group.id}") { BudgetMonthsGroupCard(context, months, group) }
    }
}

/** Switches the budget between one month and several side by side. */
@Composable
internal fun BudgetMonthsToggle(months: BudgetMonthsView) {
    SurfaceCard {
        ToggleRow(
            "Months side by side",
            "Compare ${months.monthIds.size} months at once. Turn off to reorder groups and categories.",
            checked = months.enabled,
            onChange = months.onEnabledChange,
        )
    }
}

@Composable
private fun BudgetMonthsHeader(
    state: AppState,
    months: BudgetMonthsView,
) {
    val palette = tandemPalette
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.background)
            .monthBand(months.band, months.monthIds.size, palette.accentSurface)
            .padding(vertical = TandemSpacing.small),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            "Categories",
            Modifier.width(BudgetMonthLabelWidth).padding(horizontal = TandemLayout.listRowHorizontalInset),
            color = palette.muted,
            fontSize = TandemTypography.caption,
            fontWeight = FontWeight.SemiBold,
        )
        months.monthIds.forEach { monthId -> BudgetMonthHeaderCell(state, monthId, months.toBudget(monthId)) }
    }
}

@Composable
private fun RowScope.BudgetMonthHeaderCell(
    state: AppState,
    monthId: String,
    toBudgetMinor: Long?,
) {
    val palette = tandemPalette
    val label = reportMonthLabel(monthId)
    val selected = monthId == state.selectedMonthId
    val colorSpec = tandemMotionSpec<Color>(tandemReduceMotion, TandemMotion.COLOR_MILLIS)
    val titleColor by animateColorAsState(if (selected) palette.accent else palette.text, colorSpec, label = "budget month title")
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(TandemShapes.medium))
            .selectable(selected = selected, role = Role.Tab, onClick = { state.selectBudgetMonth(monthId) })
            .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
    ) {
        Text(label, color = titleColor, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall), verticalAlignment = Alignment.CenterVertically) {
            Text("To budget", color = palette.muted, fontSize = TandemTypography.caption, maxLines = 1)
            if (toBudgetMinor != null) {
                PrivacyAmountText(
                    state.formatCurrency(toBudgetMinor),
                    state.privacyModeEnabled,
                    color = amountColor(toBudgetMinor),
                    fontSize = TandemTypography.caption,
                    fontWeight = FontWeight.SemiBold,
                    fractionDigits = state.currencyFractionDigitsForDisplay,
                )
            }
        }
        Row {
            listOf("Budgeted", "Spent", "Balance").forEach { column ->
                Text(
                    column,
                    Modifier.weight(1f),
                    color = palette.muted,
                    fontSize = TandemTypography.caption,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BudgetMonthsGroupCard(
    context: BudgetGroupListContext,
    months: BudgetMonthsView,
    group: LiveCategoryGroupSummary,
) {
    val state = context.state
    val openGroup = { monthId: String ->
        state.selectBudgetMonth(monthId)
        state.selectedCategoryGroupId = group.id
        context.navigation.open(AppRoute.CATEGORY_GROUP)
    }
    SurfaceCard {
        BudgetMonthsRow(months, group.name, FontWeight.SemiBold, openGroup) { monthId ->
            val month = months.group(monthId, group.id)
            BudgetMonthAmounts(state, month?.assignedMinor, month?.activityMinor, month?.balanceMinor)
        }
        group.categories.forEach { category ->
            val openCategory = { monthId: String ->
                state.selectBudgetMonth(monthId)
                state.selectedCategoryGroupId = group.id
                state.selectedCategoryId = category.id
                context.navigation.open(AppRoute.CATEGORY_DETAIL)
            }
            DividerLine()
            BudgetMonthsRow(months, category.name, FontWeight.Normal, openCategory) { monthId ->
                val month = months.category(monthId, category.id)
                BudgetMonthAmounts(state, month?.assignedMinor, month?.activityMinor, month?.balanceMinor)
            }
        }
    }
}

/** A name, then one tappable cell per month; the name opens the selected month. */
@Composable
private fun BudgetMonthsRow(
    months: BudgetMonthsView,
    name: String,
    weight: FontWeight,
    onOpen: (monthId: String) -> Unit,
    cell: @Composable RowScope.(monthId: String) -> Unit,
) {
    val palette = tandemPalette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TandemLayout.compactListRowMinHeight)
            .monthBand(months.band, months.monthIds.size, palette.accentSurface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            Modifier
                .width(BudgetMonthLabelWidth)
                .padding(horizontal = TandemLayout.listRowHorizontalInset, vertical = TandemSpacing.small),
            color = palette.text,
            fontSize = TandemTypography.compactBody,
            fontWeight = weight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        months.monthIds.forEach { monthId ->
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = TandemLayout.compactListRowMinHeight)
                    .clickable(role = Role.Button, onClickLabel = "Open $name in ${reportMonthLabel(monthId)}") { onOpen(monthId) }
                    .padding(horizontal = TandemSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) { cell(monthId) }
        }
    }
}

/** Budgeted, spent and balance for one month, or dashes while the month loads. */
@Composable
private fun RowScope.BudgetMonthAmounts(
    state: AppState,
    assignedMinor: Long?,
    activityMinor: Long?,
    balanceMinor: Long?,
) {
    val palette = tandemPalette
    BudgetMonthAmount(state, assignedMinor, palette.muted, FontWeight.Normal)
    BudgetMonthAmount(state, activityMinor?.let { abs(it) }, palette.muted, FontWeight.Normal)
    BudgetMonthAmount(state, balanceMinor, if (balanceMinor != null) amountColor(balanceMinor) else palette.muted, FontWeight.SemiBold)
}

@Composable
private fun RowScope.BudgetMonthAmount(
    state: AppState,
    amountMinor: Long?,
    color: Color,
    weight: FontWeight,
) {
    if (amountMinor == null) {
        Text("–", Modifier.weight(1f), color = tandemPalette.muted, fontSize = TandemTypography.caption, textAlign = TextAlign.End)
        return
    }
    PrivacyAmountText(
        state.formatCurrency(amountMinor),
        state.privacyModeEnabled,
        Modifier.weight(1f),
        color = color,
        fontSize = TandemTypography.caption,
        fontWeight = weight,
        textAlign = TextAlign.End,
        fractionDigits = state.currencyFractionDigitsForDisplay,
    )
}

@Composable
private fun amountColor(amountMinor: Long): Color =
    when {
        amountMinor < 0 -> tandemPalette.negative
        amountMinor > 0 -> tandemPalette.positive
        else -> tandemPalette.muted
    }

private fun AppState.selectBudgetMonth(monthId: String) {
    selectedMonthId = monthId
    selectedMonth = reportMonthLabel(monthId)
}

/** Tints the selected month's column; the band slides as the selection changes. */
private fun Modifier.monthBand(
    index: State<Float>,
    count: Int,
    color: Color,
): Modifier =
    drawBehind {
        if (count < 2) return@drawBehind
        val label = BudgetMonthLabelWidth.toPx()
        val column = (size.width - label) / count
        val start = label + column * index.value
        val left = if (layoutDirection == LayoutDirection.Rtl) size.width - start - column else start
        drawRect(color, topLeft = Offset(left, 0f), size = Size(column, size.height))
    }
