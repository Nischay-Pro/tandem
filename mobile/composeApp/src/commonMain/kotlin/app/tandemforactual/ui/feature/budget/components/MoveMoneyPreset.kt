package app.tandemforactual.ui.feature.budget.components

import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.MoveMoneyRequest

/** From, to and amount the move-money sheet opens with; covering overspending draws on the category with the most left. */
internal data class MoveMoneyPreset(
    val fromId: String,
    val toId: String,
    val amountMinor: Long?,
)

internal fun moveMoneyPreset(
    request: MoveMoneyRequest,
    category: LiveCategorySummary,
    candidates: List<LiveCategorySummary>,
): MoveMoneyPreset {
    val others = candidates.filter { it.id != category.id }
    if (request.coverMinor <= 0L) return MoveMoneyPreset(category.id, others.firstOrNull()?.id.orEmpty(), null)
    val source = others.filter { it.balanceMinor > 0L }.maxByOrNull { it.balanceMinor }
    return MoveMoneyPreset(source?.id.orEmpty(), category.id, request.coverMinor)
}
