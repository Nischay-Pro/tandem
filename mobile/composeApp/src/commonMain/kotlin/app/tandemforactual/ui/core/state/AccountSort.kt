package app.tandemforactual.ui.core.state

/**
 * How the Accounts page orders accounts within each group; only [MANUAL] lets them be dragged. [ascending] is null for
 * the budget's own order, true for an up arrow and false for a down arrow.
 */
internal enum class AccountSort(
    val label: String,
    val ascending: Boolean?,
) {
    MANUAL("Your order", null),
    NAME_ASCENDING("A → Z", true),
    NAME_DESCENDING("Z → A", false),
    BALANCE_ASCENDING("Balance ↑", true),
    BALANCE_DESCENDING("Balance ↓", false),
}
