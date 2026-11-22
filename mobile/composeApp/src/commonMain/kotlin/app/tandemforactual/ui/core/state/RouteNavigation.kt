@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.state
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList

internal enum class BackRequestDisposition {
    START,
    DEFER_UNTIL_ROUTE_TRANSITION_FINISHES,
    QUEUE_AFTER_BACK_TRANSITION,
}

internal fun shouldRenderRouteHeader(
    route: AppRoute,
    hasStationaryTabShell: Boolean,
): Boolean = !route.isSetupRoute() && hasStationaryTabShell

internal fun shouldRenderStandaloneRouteHeader(route: AppRoute): Boolean = !route.isSetupRoute()

internal fun backRequestDisposition(
    routeTransitionInputBlocked: Boolean,
    backButtonTransitionRunning: Boolean,
): BackRequestDisposition =
    when {
        backButtonTransitionRunning -> BackRequestDisposition.QUEUE_AFTER_BACK_TRANSITION
        routeTransitionInputBlocked -> BackRequestDisposition.DEFER_UNTIL_ROUTE_TRANSITION_FINISHES
        else -> BackRequestDisposition.START
    }

internal data class AppRouteEntry(
    val route: AppRoute,
    val id: Long,
) {
    val stateKey: String
        get() = if (route.isTab()) "tab-${route.name}" else "route-${route.name}-$id"
}

internal fun routeContentStateKey(
    entry: AppRouteEntry,
    selectedBudgetId: String,
): String =
    if (entry.route == AppRoute.BUDGET && selectedBudgetId.isNotBlank()) {
        "${entry.stateKey}-budget-$selectedBudgetId"
    } else {
        entry.stateKey
    }

internal fun encodeAppTabBackStacks(stacks: Map<AppRoute, List<AppRouteEntry>>): String =
    stacks.entries
        .filter { (tab, _) -> tab.isTab() }
        .joinToString("|") { (tab, entries) ->
            "${tab.name}=" + entries.joinToString(",") { "${it.id}:${it.route.name}" }
        }

internal fun decodeAppTabBackStacks(value: String): Map<AppRoute, List<AppRouteEntry>> =
    value
        .split('|')
        .mapNotNull { encodedStack ->
            val separator = encodedStack.indexOf('=')
            if (separator < 1) {
                null
            } else {
                val tab = AppRoute.entries.firstOrNull { it.name == encodedStack.substring(0, separator) && it.isTab() }
                val entries =
                    encodedStack
                        .substring(separator + 1)
                        .split(',')
                        .mapNotNull { encodedEntry ->
                            val entrySeparator = encodedEntry.indexOf(':')
                            if (entrySeparator < 1) {
                                null
                            } else {
                                val id = encodedEntry.substring(0, entrySeparator).toLongOrNull()
                                val route = AppRoute.entries.firstOrNull { it.name == encodedEntry.substring(entrySeparator + 1) }
                                if (id == null || route == null) null else AppRouteEntry(route, id)
                            }
                        }
                if (tab == null || entries.isEmpty()) null else tab to entries
            }
        }.toMap()

internal data class AppTabSelection(
    val backStack: List<AppRouteEntry>,
    val savedTabBackStacks: String,
)

internal fun selectAppTabNavigation(
    currentBackStack: List<AppRouteEntry>,
    savedTabBackStacks: String,
    selectedTab: AppRoute,
    newRouteEntry: (AppRoute) -> AppRouteEntry,
): AppTabSelection {
    val tabStacks = decodeAppTabBackStacks(savedTabBackStacks).toMutableMap()
    val activeTab = currentBackStack.lastOrNull { it.route.isTab() }?.route
    val nextBackStack =
        if (activeTab == selectedTab) {
            if (currentBackStack.lastOrNull()?.route == selectedTab) {
                currentBackStack
            } else {
                tabStacks.remove(selectedTab)
                buildList {
                    if (selectedTab != AppRoute.HOME) add(newRouteEntry(AppRoute.HOME))
                    add(newRouteEntry(selectedTab))
                }
            }
        } else {
            activeTab?.let { tabStacks[it] = currentBackStack }
            tabStacks[selectedTab] ?: buildList {
                if (selectedTab != AppRoute.HOME) add(newRouteEntry(AppRoute.HOME))
                add(newRouteEntry(selectedTab))
            }
        }
    return AppTabSelection(nextBackStack, encodeAppTabBackStacks(tabStacks))
}

internal fun selectAppTabRootNavigation(
    currentBackStack: List<AppRouteEntry>,
    savedTabBackStacks: String,
    selectedTab: AppRoute,
    newRouteEntry: (AppRoute) -> AppRouteEntry,
): AppTabSelection {
    require(selectedTab.isTab())
    val tabStacks = decodeAppTabBackStacks(savedTabBackStacks).toMutableMap()
    val activeTab = currentBackStack.lastOrNull { it.route.isTab() }?.route
    if (activeTab != null && activeTab != selectedTab) tabStacks[activeTab] = currentBackStack
    tabStacks.remove(selectedTab)
    val rootBackStack =
        buildList {
            if (selectedTab != AppRoute.HOME) add(newRouteEntry(AppRoute.HOME))
            add(newRouteEntry(selectedTab))
        }
    return AppTabSelection(rootBackStack, encodeAppTabBackStacks(tabStacks))
}

internal val appRouteStackSaver =
    listSaver<SnapshotStateList<AppRouteEntry>, String>(
        save = { stack -> stack.map { "${it.id}:${it.route.name}" } },
        restore = { saved ->
            mutableStateListOf<AppRouteEntry>().apply {
                saved.forEachIndexed { index, value ->
                    val split = value.split(':', limit = 2)
                    val savedId = split.firstOrNull()?.toLongOrNull()
                    val routeName = if (savedId == null) value else split.getOrNull(1).orEmpty()
                    val route = AppRoute.entries.firstOrNull { it.name == routeName }
                    if (route != null) add(AppRouteEntry(route, savedId ?: index.toLong() + 1))
                }
                if (isEmpty() || (size == 1 && single().route == AppRoute.WELCOME)) {
                    clear()
                    add(AppRouteEntry(AppRoute.STARTUP, 1))
                }
            }
        },
    )

internal fun AppRoute.isTab(): Boolean =
    this == AppRoute.HOME ||
        this == AppRoute.BUDGET ||
        this == AppRoute.ACCOUNTS ||
        this == AppRoute.ACTIVITY ||
        this == AppRoute.REPORTS ||
        this == AppRoute.SETTINGS
