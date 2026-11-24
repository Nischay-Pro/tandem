package app.tandemforactual.ui.core.state

/** A one-time sort of the budget's accounts within each group; [ascending] picks the up or down arrow. */
internal enum class AccountSort(
    val label: String,
    val ascending: Boolean,
) {
    NAME_ASCENDING("A → Z", true),
    NAME_DESCENDING("Z → A", false),
    BALANCE_ASCENDING("Balance ↑", true),
    BALANCE_DESCENDING("Balance ↓", false),
}
