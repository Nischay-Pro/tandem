package app.tandemforactual.ui.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.ServerCapabilities

internal const val CAPABILITIES_REFRESH_MILLIS = 10 * 60_000L

internal fun requiredServerFeature(kind: BudgetReorderKind): String? =
    when (kind) {
        BudgetReorderKind.HOLD_BUDGET_NEXT_MONTH -> "hold-total"
        BudgetReorderKind.SET_ORDER -> "budget-order-merge"
        else -> null
    }

internal fun missingServerFeature(
    kind: BudgetReorderKind,
    capabilities: ServerCapabilities?,
): String? {
    val feature = requiredServerFeature(kind) ?: return null
    return feature.takeUnless { capabilities == null || capabilities.supports(it) }
}
