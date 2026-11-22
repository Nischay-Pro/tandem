package app.tandemforactual.ui.core.session

/** Immutable queue state so ordering, recovery, and route-independent work can be tested without Compose. */
internal data class BudgetReorderQueueState(
    val moves: List<QueuedBudgetReorder> = emptyList(),
    val statuses: Map<String, BudgetReorderStatus> = emptyMap(),
    val categoryVisibilityOverrides: Map<String, Boolean> = emptyMap(),
    val categoryGroupNameOverrides: Map<String, String> = emptyMap(),
    val categoryGroupIdAliases: Map<String, String> = emptyMap(),
    val categoryNameOverrides: Map<String, String> = emptyMap(),
    val categoryIdAliases: Map<String, String> = emptyMap(),
    val categoryGroupIdOverrides: Map<String, String> = emptyMap(),
    val categoryGroupVisibilityOverrides: Map<String, Boolean> = emptyMap(),
    val deletedCategoryIds: Set<String> = emptySet(),
    val waitingForConnection: Boolean = false,
)
