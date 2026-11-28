package app.tandemforactual.ui.core.state

/** The tabs the app can open on. */
internal val startPageChoices = listOf(AppRoute.HOME, AppRoute.BUDGET, AppRoute.ACCOUNTS, AppRoute.ACTIVITY, AppRoute.REPORTS)

/** The tab to open on: the one saved when it's among [tabs] shown in this interface mode, else Home. */
internal fun startPage(
    saved: String,
    tabs: List<AppRoute>,
): AppRoute = startPageChoices.firstOrNull { it.name == saved }?.takeIf { it in tabs } ?: AppRoute.HOME
