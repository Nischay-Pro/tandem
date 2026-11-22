@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import app.tandemforactual.ui.core.state.AppState
import kotlinx.coroutines.flow.first

/** Scroll position to restore after returning from a transaction opened on the account detail screen. */
internal class AccountDetailScrollRestore(
    pending: MutableIntState,
    routeWasCovered: MutableState<Boolean>,
) {
    var pending by pending
    var routeWasCovered by routeWasCovered
}

@Composable
internal fun rememberAccountDetailScrollRestore(state: AppState): AccountDetailScrollRestore {
    val pending =
        rememberSaveable(state.selectedBudgetId, state.selectedAccountId) {
            mutableIntStateOf(-1)
        }
    val routeWasCovered =
        rememberSaveable(state.selectedBudgetId, state.selectedAccountId) {
            mutableStateOf(false)
        }
    return AccountDetailScrollRestore(pending, routeWasCovered)
}

/** Restores the saved scroll position once the route is back in front and its content has loaded. */
@Composable
internal fun AccountDetailScrollRestoreEffect(
    restore: AccountDetailScrollRestore,
    isForegroundRoute: Boolean,
    accountName: String?,
    activityCount: Int,
    routeScrollState: ScrollState?,
) {
    LaunchedEffect(
        isForegroundRoute,
        restore.pending,
        restore.routeWasCovered,
        accountName,
        activityCount,
        routeScrollState,
    ) {
        if (restore.pending < 0) return@LaunchedEffect
        if (!isForegroundRoute) {
            restore.routeWasCovered = true
            return@LaunchedEffect
        }
        if (!restore.routeWasCovered || accountName == null || activityCount == 0) return@LaunchedEffect

        val scrollState = routeScrollState ?: return@LaunchedEffect
        withFrameNanos { }
        if (restore.pending > 0 && scrollState.maxValue == 0) {
            snapshotFlow { scrollState.maxValue }.first { it > 0 }
        }
        withFrameNanos { }
        scrollState.scrollTo(restore.pending.coerceAtMost(scrollState.maxValue))
        restore.pending = -1
        restore.routeWasCovered = false
    }
}
