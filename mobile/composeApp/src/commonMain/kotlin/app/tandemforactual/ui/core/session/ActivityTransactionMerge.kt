package app.tandemforactual.ui.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.BudgetRecordEntity

internal const val ACTIVITY_TRANSACTION_PAGE_SIZE = 50

internal const val ACTIVITY_TRANSACTION_REFRESH_LIMIT = 200

internal fun activityTransactionRefreshLimit(loadedRecordCount: Int): Int =
    minOf(
        loadedRecordCount.coerceAtLeast(ACTIVITY_TRANSACTION_PAGE_SIZE),
        ACTIVITY_TRANSACTION_REFRESH_LIMIT,
    )

/** Rows missing from the refreshed window were deleted; only rows older than the window are kept from before. */
internal fun mergeRefreshedActivityTransactions(
    previouslyLoaded: List<BudgetRecordEntity>,
    refreshedPage: List<BudgetRecordEntity>,
    pageIsComplete: Boolean = false,
): List<BudgetRecordEntity> {
    val order = compareByDescending<BudgetRecordEntity> { it.recordDate ?: "" }.thenBy { it.recordId }
    val refreshed = refreshedPage.distinctBy(BudgetRecordEntity::recordId).sortedWith(order)
    if (pageIsComplete) return refreshed
    val refreshedIds = refreshed.mapTo(mutableSetOf(), BudgetRecordEntity::recordId)
    val oldestRefreshed = refreshed.lastOrNull()
    val older =
        previouslyLoaded.filter { record ->
            record.recordId !in refreshedIds && (oldestRefreshed == null || order.compare(record, oldestRefreshed) > 0)
        }
    return (refreshed + older).distinctBy(BudgetRecordEntity::recordId).sortedWith(order)
}
