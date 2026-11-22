@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.state

internal data class AppNavigation(
    val open: (AppRoute) -> Unit,
    val openSchedule: (String) -> Boolean = { false },
    val reset: (AppRoute) -> Unit,
    val replaceTop: (AppRoute) -> Unit,
    val newTransaction: (accountId: String?) -> Unit,
    val selectTab: (AppRoute) -> Unit,
    val back: () -> Unit,
    val backToList: () -> Unit,
    val selectTabRoot: (AppRoute) -> Unit = selectTab,
)
