@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.navigation.openTransaction
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.transactions.deleteTransactionsWithUndo
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.core.ui.TransactionRow
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.design.theme.TandemSpacing
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.launch

internal fun importReviewKey(
    budgetId: String,
    accountId: String,
): String = "internal:import-review:$budgetId:$accountId"

/** The imported transactions already let into the register, or null when the account doesn't hold imports for review. */
internal fun decodeSettledImports(stored: String?): Set<String>? =
    stored?.takeIf { it != REVIEW_OFF }?.let { text -> text.split(',').filter { it.isNotBlank() }.toSet() }

internal fun encodeSettledImports(ids: Set<String>): String = ids.sorted().joinToString(",")

/** Live transactions in [accountId] that came from a bank or file import. */
internal fun importedTransactionIds(
    records: List<BudgetRecordEntity>,
    accountId: String,
): Set<String> =
    records
        .filter { it.accountId == accountId && !it.localDeleted && !it.remoteDeleted }
        .filter { record -> record.recordBody()?.stringValue("importedId")?.isNotBlank() == true }
        .mapTo(HashSet()) { it.recordId }

/** Whether an account holds imports for review, which ones wait, and how to change either. */
internal class ImportReview(
    val enabled: Boolean,
    val waiting: Set<String>,
    val setEnabled: (Boolean) -> Unit,
    val approve: (Set<String>) -> Unit,
)

@Composable
internal fun rememberImportReview(
    state: AppState,
    mobileSession: TandemMobileSession,
    accountId: String?,
): ImportReview {
    val budgetId = state.selectedBudgetId
    val key = accountId?.let { importReviewKey(budgetId, it) }
    var settled by remember(key) { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(key) { settled = key?.let { decodeSettledImports(mobileSession.preferencesStore?.read()?.get(it)) } }
    val records = mobileSession.transactionRecords
    val imported = remember(records, accountId) { accountId?.let { importedTransactionIds(records, it) }.orEmpty() }
    val scope = rememberCoroutineScope()

    fun save(next: Set<String>?) {
        settled = next
        if (key == null) return
        scope.launch { mobileSession.preferencesStore?.putValue(key, next?.let(::encodeSettledImports) ?: REVIEW_OFF) }
    }
    val current = settled?.takeIf { key != null }
    return ImportReview(
        enabled = current != null,
        waiting = current?.let { imported - it }.orEmpty(),
        setEnabled = { on -> save(if (on) imported else null) },
        approve = { ids -> current?.let { save(it + ids) } },
    )
}

@Composable
internal fun ImportReviewToggle(
    state: AppState,
    mobileSession: TandemMobileSession,
    accountId: String,
) {
    val review = rememberImportReview(state, mobileSession, accountId)
    SurfaceCard {
        ToggleRow(
            "Review imports first",
            "New bank-synced transactions wait under To approve on the account until you approve them.",
            checked = review.enabled,
            enabled = !state.readOnlyMode,
        ) { review.setEnabled(it) }
    }
}

/** Imported transactions waiting for a look: each can be opened to edit, approved or rejected, or all approved at once. */
@Composable
internal fun ImportApprovalCard(
    review: ImportReview,
    waiting: List<TransactionItem>,
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
) {
    if (review.waiting.isEmpty()) return
    val scope = rememberCoroutineScope()
    val enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction
    SectionCard("To approve", detail = "${review.waiting.size} imported, not in the register yet") {
        TandemActionButton("Approve all", TandemActionButtonTone.PRIMARY, icon = Lucide.CircleCheck, enabled = enabled) {
            review.approve(review.waiting)
        }
        waiting.forEach { item ->
            val id = item.recordId ?: return@forEach
            DividerLine()
            TransactionRow(item, state.privacyModeEnabled) { openTransaction(state, navigation, item, isLive = true) }
            Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                TandemActionButton("Approve", TandemActionButtonTone.NEUTRAL, Modifier.weight(1f), icon = Lucide.Check, enabled = enabled) {
                    review.approve(setOf(id))
                }
                TandemActionButton(
                    "Reject",
                    TandemActionButtonTone.DESTRUCTIVE,
                    Modifier.weight(1f),
                    icon = Lucide.Trash2,
                    enabled = enabled,
                ) {
                    scope.launch { mobileSession.transactions.deleteTransactionsWithUndo(state.selectedBudgetId, setOf(id)) }
                }
            }
        }
    }
}

internal const val REVIEW_OFF = "off"
