@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.feedback.AnimatedReveal
import app.tandemforactual.ui.design.templates.AttentionBanner
import kotlinx.coroutines.flow.first

internal fun LazyListScope.budgetPendingChangeBannerItems(
    state: AppState,
    navigation: AppNavigation,
    pendingWrites: List<OutboxOperationEntity>,
) {
    val reviewableWrites = pendingWrites.filter { it.recordType in setOf("transaction", "budget-mutation") }
    val conflictCount = reviewableWrites.count { it.status == "conflict" }
    val rejectedWrites = reviewableWrites.filter { it.status == "rejected" }
    val uncertainWrites = reviewableWrites.filter { it.status == "uncertain" }

    if (conflictCount > 0) {
        item(key = "budget-conflict-alert") {
            AnimatedReveal(visible = true) {
                AttentionBanner(
                    "Needs review",
                    if (conflictCount == 1) "Choose which version to keep." else "Review $conflictCount changes.",
                    "Review",
                ) {
                    state.selectedConflictOperationId = ""
                    navigation.open(AppRoute.CONFLICT_INBOX)
                }
            }
        }
    }
    if (rejectedWrites.isNotEmpty()) {
        item(key = "budget-rejected-alert") {
            AnimatedReveal(visible = true) {
                AttentionBanner(
                    "Rejected",
                    "${rejectedWrites.size} ${if (rejectedWrites.size == 1) "change" else "changes"} were rejected.",
                    "Review",
                ) {
                    state.selectedConflictOperationId = rejectedWrites.firstOrNull()?.operationId.orEmpty()
                    navigation.open(AppRoute.UNCERTAIN_WRITE)
                }
            }
        }
    }
    if (uncertainWrites.isNotEmpty()) {
        item(key = "budget-status-unknown-alert") {
            AnimatedReveal(visible = true) {
                AttentionBanner(
                    "Status unknown",
                    "Tandem hasn’t confirmed these edits. Check their status.",
                    "Check status",
                ) {
                    state.selectedConflictOperationId = uncertainWrites.firstOrNull()?.operationId.orEmpty()
                    navigation.open(AppRoute.UNCERTAIN_WRITE)
                }
            }
        }
    }
}

internal fun suggestedHoldAmount(readyToAssignMinor: Long): Long = readyToAssignMinor.coerceAtLeast(0)

internal fun holdAmountAllowed(
    amountMinor: Long,
    readyToAssignMinor: Long,
): Boolean = amountMinor > 0 && amountMinor <= readyToAssignMinor

/** Scroll movement between two list positions in pixels; a change of item counts as a full threshold in that direction. */
internal fun scrollStep(
    previous: Pair<Int, Int>,
    current: Pair<Int, Int>,
    thresholdPx: Float,
): Float =
    when {
        current.first > previous.first -> thresholdPx
        current.first < previous.first -> -thresholdPx
        else -> (current.second - previous.second).toFloat()
    }

/**
 * Floating actions slide away after a deliberate scroll forward so they don't cover amounts, and return after a
 * deliberate scroll back or at the top. Smaller movements keep the current state so the buttons don't flicker.
 */
internal fun floatingActionsHidden(
    hidden: Boolean,
    travel: Float,
    current: Pair<Int, Int>,
    thresholdPx: Float,
): Boolean =
    when {
        current.first == 0 && current.second < thresholdPx -> false
        travel >= thresholdPx -> true
        travel <= -thresholdPx -> false
        else -> hidden
    }
