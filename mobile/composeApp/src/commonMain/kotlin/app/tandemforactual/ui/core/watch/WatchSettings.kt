package app.tandemforactual.ui.core.watch

import kotlinx.coroutines.flow.MutableStateFlow

/** The phone's watch settings: which categories the watch shows, whether it may add, and whether it waits for an unlock. */
data class WatchSettings(
    val pinnedCategoryIds: List<String> = emptyList(),
    val quickAdd: Boolean = true,
    val summaryAfterUnlock: Boolean = false,
) {
    fun toPreferences(): Map<String, String> =
        mapOf(
            PINNED to pinnedCategoryIds.joinToString(","),
            QUICK_ADD to quickAdd.toString(),
            AFTER_UNLOCK to summaryAfterUnlock.toString(),
        )

    fun togglePin(categoryId: String): WatchSettings =
        copy(
            pinnedCategoryIds =
                if (categoryId in pinnedCategoryIds) {
                    pinnedCategoryIds - categoryId
                } else {
                    (pinnedCategoryIds + categoryId).takeLast(MAX_PINNED)
                },
        )

    companion object {
        const val PINNED = "watchPinnedCategoryIds"
        const val QUICK_ADD = "watchQuickAdd"
        const val AFTER_UNLOCK = "watchSummaryAfterUnlock"
        const val MAX_PINNED = 6

        fun from(saved: Map<String, String>): WatchSettings =
            WatchSettings(
                pinnedCategoryIds = saved[PINNED].orEmpty().split(',').filter(String::isNotBlank),
                quickAdd = saved[QUICK_ADD]?.toBooleanStrictOrNull() ?: true,
                summaryAfterUnlock = saved[AFTER_UNLOCK]?.toBooleanStrictOrNull() ?: false,
            )
    }
}

/** What this device knows about a paired watch; each platform keeps it current. */
object WatchPairing {
    /** Whether a paired watch has Tandem installed, so Settings offers the Watch page. */
    val available = MutableStateFlow(false)

    /** Asks the platform to send the watch a fresh summary, such as after a watch setting changed. */
    var refresh: () -> Unit = {}
}
