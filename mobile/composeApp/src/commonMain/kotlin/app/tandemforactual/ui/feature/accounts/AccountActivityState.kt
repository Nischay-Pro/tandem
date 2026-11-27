@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.format.rememberTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.observeAccountActivityRecords
import app.tandemforactual.ui.core.session.observeClearedAccountBalance
import app.tandemforactual.ui.core.state.AccountActivitySnapshot
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.buildLiveTransactionItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

internal const val ACCOUNT_RECONCILIATION_PAGE_SIZE = 500

/** Paged account activity and cleared balance shown on the account detail screen. */
internal class AccountActivityState(
    activity: MutableState<List<TransactionItem>>,
    offset: MutableState<Int>,
    hasMore: MutableState<Boolean>,
    loadingOlder: MutableState<Boolean>,
    clearedBalanceMinor: MutableState<Long?>,
) {
    var activity by activity
    var offset by offset
    var hasMore by hasMore
    var loadingOlder by loadingOlder
    var clearedBalanceMinor by clearedBalanceMinor
}

@Composable
internal fun rememberAccountActivityState(
    state: AppState,
    mobileSession: TandemMobileSession,
    liveAccount: LiveAccountSummary?,
): AccountActivityState {
    val activity =
        remember(mobileSession, state.selectedBudgetId, liveAccount?.id) {
            mutableStateOf(
                mobileSession.activity
                    .cachedAccountActivity(state.selectedBudgetId, liveAccount?.id.orEmpty())
                    ?.transactions
                    .orEmpty(),
            )
        }
    val offset =
        remember(mobileSession, state.selectedBudgetId, liveAccount?.id) {
            mutableStateOf(mobileSession.activity.cachedAccountActivity(state.selectedBudgetId, liveAccount?.id.orEmpty())?.offset ?: 0)
        }
    val hasMore =
        remember(mobileSession, state.selectedBudgetId, liveAccount?.id) {
            mutableStateOf(
                mobileSession.activity.cachedAccountActivity(state.selectedBudgetId, liveAccount?.id.orEmpty())?.hasMore ?: false,
            )
        }
    val loadingOlder = remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(false) }
    val clearedBalanceMinor = remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf<Long?>(null) }
    return AccountActivityState(activity, offset, hasMore, loadingOlder, clearedBalanceMinor)
}

/** Keeps the account's activity pages and cleared balance in sync with the local database. */
@Composable
internal fun AccountActivityEffects(
    activity: AccountActivityState,
    state: AppState,
    mobileSession: TandemMobileSession,
    liveAccount: LiveAccountSummary?,
    merchantIconMatcher: MerchantIconMatcherPack?,
) {
    val todayIso = rememberTodayIsoDate()
    LaunchedEffect(
        mobileSession.databaseGeneration,
        state.selectedBudgetId,
        liveAccount?.id,
        activity.offset,
        mobileSession.accountRecords,
        mobileSession.payeeRecords,
        mobileSession.categoryRecords,
        mobileSession.budgetDisplaySettings,
        merchantIconMatcher,
        todayIso,
    ) {
        val accountId = liveAccount?.id ?: return@LaunchedEffect
        val accountActivityFlow =
            mobileSession.observeAccountActivityRecords(
                state.selectedBudgetId,
                accountId,
                limit = ACCOUNT_RECONCILIATION_PAGE_SIZE,
                offset = activity.offset,
            )
                ?: return@LaunchedEffect
        accountActivityFlow.collect { records ->
            val page =
                withContext(Dispatchers.Default) {
                    buildLiveTransactionItems(
                        records,
                        mobileSession.accountRecords,
                        mobileSession.payeeRecords,
                        mobileSession.categoryRecords,
                        mobileSession.budgetDisplaySettings,
                        merchantIconMatcher,
                        todayIso,
                    ).filterNot(TransactionItem::isSplitParent)
                }
            val nextActivity =
                if (activity.offset == 0) {
                    page
                } else {
                    (activity.activity + page).distinctBy(TransactionItem::recordId)
                }
            val hasMore = records.size == ACCOUNT_RECONCILIATION_PAGE_SIZE
            activity.activity = nextActivity
            activity.hasMore = hasMore
            mobileSession.activity.cacheAccountActivity(
                state.selectedBudgetId,
                accountId,
                AccountActivitySnapshot(nextActivity, activity.offset, hasMore),
            )
            activity.loadingOlder = false
        }
    }
    LaunchedEffect(state.selectedBudgetId, liveAccount?.id, mobileSession.databaseGeneration) {
        val accountId = liveAccount?.id ?: return@LaunchedEffect
        val clearedBalanceFlow = mobileSession.observeClearedAccountBalance(state.selectedBudgetId, accountId) ?: return@LaunchedEffect
        clearedBalanceFlow.collect { activity.clearedBalanceMinor = it }
    }
}
