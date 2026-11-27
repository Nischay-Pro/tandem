package app.tandemforactual.data.store

/** What Settings › Automation & connections › Watch decides about the paired watch. */
data class WatchSettings(
    val pinnedCategoryIds: List<String> = emptyList(),
    val quickAddEnabled: Boolean = true,
    val summaryAfterUnlock: Boolean = true,
    val perDayFirst: Boolean = true,
    val nextBillOnGlance: Boolean = false,
    val categoriesOnGlance: Boolean = true,
) {
    fun toPreferences(): Map<String, String> =
        mapOf(
            PINNED to pinnedCategoryIds.joinToString(","),
            QUICK_ADD to quickAddEnabled.toString(),
            AFTER_UNLOCK to summaryAfterUnlock.toString(),
            PER_DAY_FIRST to perDayFirst.toString(),
            BILL_ON_GLANCE to nextBillOnGlance.toString(),
            CATEGORIES_ON_GLANCE to categoriesOnGlance.toString(),
        )

    fun pinning(
        categoryId: String,
        pinned: Boolean,
    ): WatchSettings =
        copy(
            pinnedCategoryIds =
                if (pinned) {
                    (pinnedCategoryIds - categoryId + categoryId).take(MAX_WATCH_CATEGORIES)
                } else {
                    pinnedCategoryIds - categoryId
                },
        )

    companion object {
        const val PINNED = "watchPinnedCategoryIds"
        const val QUICK_ADD = "watchQuickAddEnabled"
        const val AFTER_UNLOCK = "watchSummaryAfterUnlock"
        const val PER_DAY_FIRST = "watchPerDayFirst"
        const val BILL_ON_GLANCE = "watchNextBillOnGlance"
        const val CATEGORIES_ON_GLANCE = "watchCategoriesOnGlance"

        fun from(preferences: Map<String, String>): WatchSettings =
            WatchSettings(
                pinnedCategoryIds =
                    preferences[PINNED]
                        .orEmpty()
                        .split(',')
                        .filter(String::isNotBlank)
                        .distinct()
                        .take(MAX_WATCH_CATEGORIES),
                quickAddEnabled = preferences[QUICK_ADD]?.toBooleanStrictOrNull() ?: true,
                summaryAfterUnlock = preferences[AFTER_UNLOCK]?.toBooleanStrictOrNull() ?: true,
                perDayFirst = preferences[PER_DAY_FIRST]?.toBooleanStrictOrNull() ?: true,
                nextBillOnGlance = preferences[BILL_ON_GLANCE]?.toBooleanStrictOrNull() ?: false,
                categoriesOnGlance = preferences[CATEGORIES_ON_GLANCE]?.toBooleanStrictOrNull() ?: true,
            )
    }
}

const val MAX_WATCH_CATEGORIES = 6
