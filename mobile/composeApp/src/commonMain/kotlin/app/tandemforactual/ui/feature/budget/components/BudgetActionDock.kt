@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.app.FloatingAction
import app.tandemforactual.ui.app.FloatingTransactionAction
import app.tandemforactual.ui.core.session.shouldShowBudgetCreateAction
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LocalRouteLazyListState
import app.tandemforactual.ui.core.state.LocalRouteScrollState
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.currentWindowSizeInfo
import app.tandemforactual.ui.design.theme.tandemFloatingEnter
import app.tandemforactual.ui.design.theme.tandemFloatingExit
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.budget.floatingActionsHidden
import app.tandemforactual.ui.feature.budget.scrollStep
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Tags

/** Floating "Add group" and new-transaction buttons on the budget screen. */
@Composable
internal fun BudgetActionDock(
    visible: Boolean,
    readOnlyMode: Boolean,
    showTransactionAction: Boolean,
    transactionActionCompact: Boolean,
    onAddGroup: () -> Unit,
    onNewTransaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = tandemFloatingEnter(tandemReduceMotion),
        exit = tandemFloatingExit(tandemReduceMotion),
    ) {
        Row(
            Modifier
                .padding(end = TandemSpacing.large, bottom = TandemSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingAction(
                compact = true,
                enabled = !readOnlyMode,
                label = "Add group",
                contentDescription = "Add category group",
                icon = Lucide.Tags,
                outlined = true,
                onClick = onAddGroup,
            )
            if (showTransactionAction) {
                FloatingTransactionAction(
                    compact = transactionActionCompact,
                    enabled = !readOnlyMode,
                    onClick = onNewTransaction,
                )
            }
        }
    }
}

/** Floating actions, hidden while reordering or after scrolling down the list. */
@Composable
internal fun BudgetScreenDock(
    state: AppState,
    navigation: AppNavigation,
    reorder: BudgetReorderState,
    isLiveBudget: Boolean,
    modifier: Modifier,
    onAddGroup: () -> Unit,
) {
    val routeScrollState = LocalRouteScrollState.current
    val routeListState = LocalRouteLazyListState.current
    val createActionsCompact by
        remember(routeScrollState, routeListState) {
            derivedStateOf {
                (routeScrollState?.value ?: 0) > 72 ||
                    routeListState?.firstVisibleItemIndex?.let { it > 0 } == true ||
                    (routeListState?.firstVisibleItemScrollOffset ?: 0) > 72
            }
        }
    val compactHeight = currentWindowSizeInfo().compactHeight
    val showCreateActions =
        shouldShowBudgetCreateAction(
            isLiveBudget,
            reorder.selectedGroupOrderId != null || reorder.selectedCategoryOrderId != null,
        )
    val actionDockScrolledAway = rememberActionDockScrolledAway(routeListState)
    BudgetActionDock(
        visible = showCreateActions && !actionDockScrolledAway,
        readOnlyMode = state.readOnlyMode,
        showTransactionAction = showCreateActions && !compactHeight,
        transactionActionCompact = createActionsCompact,
        onAddGroup = onAddGroup,
        onNewTransaction = { navigation.newTransaction(null) },
        modifier = modifier,
    )
}

@Composable
private fun rememberActionDockScrolledAway(routeListState: LazyListState?): Boolean {
    var actionDockScrolledAway by remember { mutableStateOf(false) }
    val dockThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    LaunchedEffect(routeListState, dockThresholdPx) {
        val listState = routeListState ?: return@LaunchedEffect
        var previous = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        var travel = 0f
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { current ->
            val step = scrollStep(previous, current, dockThresholdPx)
            travel = if (step == 0f || (step > 0) == (travel > 0)) travel + step else step
            actionDockScrolledAway = floatingActionsHidden(actionDockScrolledAway, travel, current, dockThresholdPx)
            previous = current
        }
    }
    return actionDockScrolledAway
}
