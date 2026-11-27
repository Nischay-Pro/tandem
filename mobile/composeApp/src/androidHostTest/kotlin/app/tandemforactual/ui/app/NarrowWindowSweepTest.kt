package app.tandemforactual.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.planning.AccountKind
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.InterfaceMode
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.design.templates.dashboardColumnCount
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.WindowSizeInfo
import app.tandemforactual.ui.design.theme.currentWindowSizeInfo
import app.tandemforactual.ui.feature.activity.ActivityTransactionSortCache
import app.tandemforactual.ui.feature.budget.budgetMonthCount
import app.tandemforactual.ui.feature.reports.ReportsDataCache
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every screen stays whole in split-screen thirds and halves, floating windows and the narrowest pop-up. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NarrowWindowSweepTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = WindowPresets.TABLET_THIRD)
    fun tabletThird() = sweep()

    @Test
    @Config(qualifiers = WindowPresets.TALL_TABLET_THIRD)
    fun tallTabletThird() = sweep()

    @Test
    @Config(qualifiers = WindowPresets.FOLD_HALF)
    fun foldableHalf() = sweep()

    @Test
    @Config(qualifiers = WindowPresets.FLOATING)
    fun floatingWindow() = sweep()

    @Test
    @Config(qualifiers = WindowPresets.VERY_NARROW)
    fun veryNarrowWindow() = sweep()

    private fun sweep() {
        val shown = mutableStateOf(AppRoute.HOME)
        val state = sampleState()
        val session = TandemMobileSession(TandemApiClient(), null, { 0L })
        lateinit var window: WindowSizeInfo
        composeTestRule.setContent {
            window = currentWindowSizeInfo()
            val route = shown.value
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true, interfaceMode = InterfaceMode.ADVANCED) {
                PresetChrome(window, route, showBack = route !in TAB_ROUTES, state = state) {
                    key(route) { screenRegistry.getValue(route)(sampleScreenContext(route, state, session)) }
                }
            }
        }
        assertSinglePane(window)
        val problems =
            SWEPT_ROUTES.flatMap { route ->
                composeTestRule.runOnIdle { shown.value = route }
                try {
                    composeTestRule.waitForIdle()
                } catch (failure: Throwable) {
                    throw AssertionError("$route failed to render at ${window.widthDp}x${window.heightDp}dp", failure)
                }
                composeTestRule.windowFitProblems(route.name)
            }
        assertTrue(problems.isEmpty(), "At ${window.widthDp}x${window.heightDp}dp:\n" + problems.joinToString("\n"))
    }

    private fun assertSinglePane(window: WindowSizeInfo) {
        val content = window.contentWidthDp
        assertFalse(window.usesRail, "a narrow window keeps the bottom bar")
        assertFalse(window.floatsEditors, "a narrow window opens editors full screen")
        assertFalse(usesListDetailPanes(content), "a narrow window shows one pane")
        assertFalse(usesSupportingPane(content), "a narrow window opens details as pages")
        assertEquals(1, budgetMonthCount(window.widthClass, content.dp - TandemSpacing.screen * 2))
        assertEquals(1, dashboardColumnCount(window.widthClass, content.dp, TandemSpacing.large))
    }

    private companion object {
        val TAB_ROUTES = setOf(AppRoute.HOME, AppRoute.ACCOUNTS, AppRoute.ACTIVITY, AppRoute.BUDGET, AppRoute.REPORTS)
        val SWEPT_ROUTES = AppRoute.entries.filter { it != AppRoute.STARTUP && it in screenRegistry }
    }
}

private const val LONG_ACCOUNT = "Everyday joint chequing account at the credit union"
private const val LONG_GROUP = "Household essentials and recurring bills"
private const val LONG_CATEGORY = "Groceries and household supplies for the family"
private const val LONG_PAYEE = "The Neighbourhood Grocery and Pharmacy Co-operative"
private const val BIG = 9_876_543_210_99L

private fun sampleState(): AppState =
    AppState().apply {
        selectedBudget = "Family budget shared with everyone in the household"
        selectedBudgetId = "budget"
        selectedAccountId = "checking"
        selectedCategoryGroupId = "essentials"
        selectedCategoryId = "groceries"
        selectedTransactionRecordId = "t1"
        selectedTransactionAccountId = "checking"
        selectedTransactionAccountName = LONG_ACCOUNT
        selectedTransactionPayeeName = LONG_PAYEE
        selectedTransactionCategoryName = LONG_CATEGORY
        selectedTransactionAmountMinor = -BIG
        selectedTransactionNotes = "Weekly shop for the whole family, including the party supplies for Saturday"
        transactionPayee = LONG_PAYEE
        transactionCategory = LONG_CATEGORY
        transactionAccount = LONG_ACCOUNT
        ruleEditorRuleId = ""
    }

private val sampleAccounts =
    listOf(
        LiveAccountSummary("checking", LONG_ACCOUNT, BIG, AccountKind.BANK, offBudget = false, accountGroupName = "Banking and everyday"),
        LiveAccountSummary("card", "Rewards credit card with travel insurance", -BIG, AccountKind.CREDIT_CARD, offBudget = false),
        LiveAccountSummary("savings", "Long-term savings for the house deposit", BIG, AccountKind.SAVINGS, offBudget = true),
    )

private val sampleMonth =
    LiveBudgetMonthSummary(
        toBudgetMinor = -BIG,
        incomeAvailableMinor = BIG,
        totalBudgetedMinor = BIG,
        totalSpentMinor = -BIG,
        groups =
            listOf(
                LiveCategoryGroupSummary(
                    "essentials",
                    LONG_GROUP,
                    listOf(
                        LiveCategorySummary("groceries", LONG_CATEGORY, BIG, -BIG, -BIG),
                        LiveCategorySummary("rent", "Rent", BIG, -BIG, BIG, carryover = true),
                    ),
                ),
                LiveCategoryGroupSummary("income", "Income", listOf(LiveCategorySummary("salary", "Salary", 0, BIG, BIG)), isIncome = true),
            ),
        totalIncomeMinor = BIG,
    )

private val sampleTransactions =
    listOf(
        TransactionItem(
            payee = LONG_PAYEE,
            category = LONG_CATEGORY,
            dateLabel = "Wednesday 26 November",
            amountMinorUnits = -BIG,
            icon = "",
            account = LONG_ACCOUNT,
            accountId = "checking",
            recordId = "t1",
            notes = "Weekly shop for the whole family",
            dateIso = "2026-11-26",
            categoryId = "groceries",
        ),
        TransactionItem(LONG_ACCOUNT, "", "Tuesday 25 November", BIG, "", accountId = "card", recordId = "t2", isTransfer = true),
    )

private val sampleSchedules =
    listOf(
        LiveScheduleSummary(
            "rent",
            "Monthly rent for the apartment downtown",
            "2026-12-01",
            "\"2026-12-01\"",
            "checking",
            LONG_ACCOUNT,
            null,
            -BIG,
            null,
            "is",
            canSkip = true,
        ),
    )

@Composable
private fun sampleScreenContext(
    route: AppRoute,
    state: AppState,
    session: TandemMobileSession,
): ScreenContext =
    ScreenContext(
        route = route,
        isForegroundRoute = true,
        state = state,
        deviceName = "Pixel Tablet",
        navigation = AppNavigation(open = {}, reset = {}, replaceTop = {}, newTransaction = {}, selectTab = {}, back = {}, backToList = {}),
        mobileSession = session,
        reportsDataCache = ReportsDataCache(),
        onSignIn = {},
        onStartOpenIdSignIn = {},
        onRefreshBudgets = {},
        onPrepareBudgetUnlock = {},
        onOpenSelectedBudget = {},
        onRetrySync = {},
        backgroundSyncSupported = true,
        merchantIconMatcher = null,
        merchantIconMatcherLoadError = null,
        merchantIconCatalog = null,
        merchantIconCatalogLoadError = null,
        liveTransactions = sampleTransactions,
        liveActivityTransactions = sampleTransactions,
        liveUncategorized = emptyList(),
        onActivitySelectionTotalChanged = {},
        activitySortCache = ActivityTransactionSortCache(),
        liveAccounts = sampleAccounts,
        liveSchedules = sampleSchedules,
        liveBudgetMonth = sampleMonth,
        onSyncNow = {},
        onBudgetGroupReorderModeChange = {},
        onSaveTransaction = { _, _ -> },
        onDeleteTransaction = {},
        onCreateTransaction = {},
        onResolveConflict = { _, _, _ -> },
        onDiscardWrite = {},
        onRetryUncertainWrite = {},
        onCheckUncertainWriteStatus = {},
        onForgetDevice = {},
        onForgetDeviceLocally = {},
        onRequestNotificationPermission = null,
        onOpenNotificationSettings = null,
        notificationPermissionEnabled = null,
        onToggleNotificationPermission = null,
        scope = rememberCoroutineScope(),
        uriHandler = LocalUriHandler.current,
    )
