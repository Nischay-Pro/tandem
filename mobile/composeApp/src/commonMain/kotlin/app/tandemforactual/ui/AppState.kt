package app.tandemforactual.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.setValue
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.reports.ReportKind
import app.tandemforactual.ui.reports.ReportPeriodMode
import app.tandemforactual.ui.reports.ReportRangePreset

private fun decodeScrollPositions(serialized: String?): Map<String, Int> =
    serialized
        ?.split('|')
        ?.mapNotNull { entry ->
            val delimiter = entry.lastIndexOf(':')
            if (delimiter < 1) {
                null
            } else {
                entry.substring(delimiter + 1).toIntOrNull()?.let { entry.substring(0, delimiter) to it }
            }
        }?.toMap()
        ?: emptyMap()

private fun decodeLazyScrollPositions(serialized: String?): Map<String, Pair<Int, Int>> =
    serialized
        ?.split('|')
        ?.mapNotNull { entry ->
            val delimiter = entry.lastIndexOf(':')
            if (delimiter < 1) {
                null
            } else {
                val values = entry.substring(delimiter + 1).split(',', limit = 2)
                val index = values.getOrNull(0)?.toIntOrNull()
                val offset = values.getOrNull(1)?.toIntOrNull()
                if (index == null || offset == null || index < 0 || offset < 0) {
                    null
                } else {
                    entry.substring(0, delimiter) to (index to offset)
                }
            }
        }?.toMap()
        ?: emptyMap()

internal class AppState {
    var theme by mutableStateOf(ThemeMode.DARK)
    var accent by mutableStateOf(AccentChoice.ACTUAL_PURPLE)
    var selectedBudget by mutableStateOf("Household")
    var selectedBudgetId by mutableStateOf("")
    var selectedBudgetEncrypted by mutableStateOf(false)
    var cachedBudgetCatalogJson by mutableStateOf("")
    var budgetDisplaySettings by mutableStateOf<BudgetDisplaySettings?>(null)
    var selectedMonth by mutableStateOf(currentMonthLabel())
    var selectedMonthId by mutableStateOf(currentMonthId())
    var reportsMonthId by mutableStateOf(currentMonthId())
    var selectedReportKindName by mutableStateOf(ReportKind.SPENDING_BY_CATEGORY.name)
    var reportsBudgetId by mutableStateOf("")
    var reportsPeriodMode by mutableStateOf(ReportPeriodMode.MONTH.name)
    var reportsRangeMonths by mutableStateOf(6)
    var reportsRangePreset by mutableStateOf(ReportRangePreset.LAST_6_MONTHS.name)
    var reportsNetWorthScope by mutableStateOf("All accounts")
    var activitySearchQuery by mutableStateOf("")
    var activitySearchFilters by mutableStateOf<List<ActivitySearchFilter>>(emptyList())
    var savedActivitySearchesJson by mutableStateOf("")
    var budgetTemplatesJson by mutableStateOf("")
    var activitySortOrder by mutableStateOf(ActivitySortOrder.NEWEST_FIRST)
    var activitySearchCache: ActivitySearchCache? = null
    var activitySearchResultCache: ActivitySearchResultCache? = null
    var selectedAccountId by mutableStateOf("")
    var selectedCategoryGroupId by mutableStateOf("")
    var selectedCategoryId by mutableStateOf("")
    var categoryEditRequested by mutableStateOf(false)
    var selectedTransactionRecordId by mutableStateOf("")
    var selectedTransactionAccountId by mutableStateOf("")
    var selectedTransactionPayeeId by mutableStateOf("")
    var selectedTransactionCategoryId by mutableStateOf("")
    var selectedTransactionDate by mutableStateOf("")
    var selectedTransactionAccountName by mutableStateOf("")
    var selectedTransactionPayeeName by mutableStateOf("")
    var selectedTransactionCategoryName by mutableStateOf("")
    var selectedConflictOperationId by mutableStateOf("")
    var serverUrl by mutableStateOf("https://budget.example.com")
    var pairingCode by mutableStateOf("")
    var encryptedPassword by mutableStateOf("")
    var transactionAmount by mutableStateOf(formatAmount(4_218))
    var transactionAccount by mutableStateOf("Checking")
    var newTransactionAccountId by mutableStateOf("")
    var newTransactionInitialAccountId by mutableStateOf("")
    var newTransactionInitialAccountName by mutableStateOf("Checking")
    var transactionPayee by mutableStateOf("Corner Market")
    var newTransactionPayeeId by mutableStateOf("")
    var transactionCategory by mutableStateOf("Groceries")
    var newTransactionCategoryId by mutableStateOf("")
    var transactionDate by mutableStateOf(formatDate(SampleDate(2026, 5, 14)))
    var newTransactionInitialDate by mutableStateOf("")
    var transactionType by mutableStateOf("Spending")
    var transactionNotes by mutableStateOf("")
    var selectedTransactionAmountMinor by mutableStateOf<Long?>(null)
    var selectedTransactionNotes by mutableStateOf("")
    var selectedTransactionIsLive by mutableStateOf(false)
    var conflictChoice by mutableStateOf("")
    var backgroundStyle by mutableStateOf("Solid surface")
    var homeLayout by mutableStateOf("Comfortable")
    var collapsedBudgetSections by mutableStateOf("")
    var pinnedCategoryIds by mutableStateOf("")
    var categoryIconOverrides by mutableStateOf("")
    var readOnlyMode by mutableStateOf(false)
    var reduceMotionOverride by mutableStateOf<Boolean?>(null)
    var systemReduceMotionEnabled by mutableStateOf(false)
    val reduceMotion: Boolean get() = reduceMotionOverride ?: systemReduceMotionEnabled
    var syncOnOpen by mutableStateOf(true)
    var backgroundSyncEnabled by mutableStateOf(false)
    var syncOnWifi by mutableStateOf(false)
    var syncReminders by mutableStateOf(true)
    var conflictReviewAlerts by mutableStateOf(true)
    var rejectedEditAlerts by mutableStateOf(true)
    var settingsStorageError by mutableStateOf<String?>(null)
    var settingsStorageRetry by mutableStateOf(0)
    var settingsStorageReady by mutableStateOf(false)
    val scrollPositions = mutableMapOf<String, Int>()
    val lazyScrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    fun persistentSettings(): Map<String, String> =
        mapOf(
            "theme" to theme.name,
            "accent" to accent.name,
            "backgroundStyle" to backgroundStyle,
            "homeLayout" to homeLayout,
            "collapsedBudgetSections" to collapsedBudgetSections,
            "pinnedCategoryIds" to pinnedCategoryIds,
            "categoryIconOverrides" to categoryIconOverrides,
            "readOnlyMode" to readOnlyMode.toString(),
            "selectedBudget" to selectedBudget,
            "selectedBudgetId" to selectedBudgetId,
            "selectedBudgetEncrypted" to selectedBudgetEncrypted.toString(),
            "cachedBudgetCatalogJson" to cachedBudgetCatalogJson,
            "reportsMonthId" to reportsMonthId,
            "selectedReportKindName" to selectedReportKindName,
            "reportsBudgetId" to reportsBudgetId,
            "reportsPeriodMode" to reportsPeriodMode,
            "reportsRangeMonths" to reportsRangeMonths.toString(),
            "reportsRangePreset" to reportsRangePreset,
            "reportsNetWorthScope" to reportsNetWorthScope,
            "savedActivitySearchesJson" to savedActivitySearchesJson,
            "budgetTemplatesJson" to budgetTemplatesJson,
            "reduceMotionMode" to (reduceMotionOverride?.let { if (it) "on" else "off" } ?: "system"),
            "syncOnOpen" to syncOnOpen.toString(),
            "backgroundSyncEnabled" to backgroundSyncEnabled.toString(),
            "syncOnWifi" to syncOnWifi.toString(),
            "syncReminders" to syncReminders.toString(),
            "conflictReviewAlerts" to conflictReviewAlerts.toString(),
            "rejectedEditAlerts" to rejectedEditAlerts.toString(),
        )

    fun restorePersistentSettings(saved: Map<String, String>) {
        theme = ThemeMode.entries.firstOrNull { it.name == saved["theme"] } ?: theme
        accent = AccentChoice.entries.firstOrNull { it.name == saved["accent"] } ?: accent
        backgroundStyle = saved["backgroundStyle"] ?: backgroundStyle
        homeLayout = (saved["homeLayout"] ?: homeLayout).let { if (it == "Essentials first") "Essentials" else it }
        collapsedBudgetSections = saved["collapsedBudgetSections"] ?: collapsedBudgetSections
        pinnedCategoryIds = saved["pinnedCategoryIds"] ?: pinnedCategoryIds
        categoryIconOverrides = saved["categoryIconOverrides"] ?: categoryIconOverrides
        readOnlyMode = saved["readOnlyMode"]?.toBooleanStrictOrNull() ?: readOnlyMode
        selectedBudget = saved["selectedBudget"] ?: selectedBudget
        selectedBudgetId = saved["selectedBudgetId"] ?: selectedBudgetId
        selectedBudgetEncrypted = saved["selectedBudgetEncrypted"]?.toBooleanStrictOrNull() ?: selectedBudgetEncrypted
        cachedBudgetCatalogJson = saved["cachedBudgetCatalogJson"] ?: cachedBudgetCatalogJson
        reportsMonthId = saved["reportsMonthId"] ?: reportsMonthId
        selectedReportKindName = saved["selectedReportKindName"] ?: selectedReportKindName
        reportsBudgetId = saved["reportsBudgetId"] ?: reportsBudgetId
        reportsPeriodMode = saved["reportsPeriodMode"] ?: reportsPeriodMode
        reportsRangeMonths = saved["reportsRangeMonths"]?.toIntOrNull()?.coerceIn(3, 12) ?: reportsRangeMonths
        reportsRangePreset = saved["reportsRangePreset"] ?: reportsRangePreset
        reportsNetWorthScope = saved["reportsNetWorthScope"] ?: reportsNetWorthScope
        savedActivitySearchesJson = saved["savedActivitySearchesJson"] ?: savedActivitySearchesJson
        budgetTemplatesJson = saved["budgetTemplatesJson"] ?: budgetTemplatesJson
        reduceMotionOverride =
            when (saved["reduceMotionMode"]) {
                "on" -> true
                "off" -> false
                "system" -> null
                else -> saved["reduceMotion"]?.toBooleanStrictOrNull() ?: reduceMotionOverride
            }
        syncOnOpen = saved["syncOnOpen"]?.toBooleanStrictOrNull() ?: syncOnOpen
        backgroundSyncEnabled = saved["backgroundSyncEnabled"]?.toBooleanStrictOrNull() ?: backgroundSyncEnabled
        syncOnWifi = saved["syncOnWifi"]?.toBooleanStrictOrNull() ?: syncOnWifi
        val legacyNotifications = saved["notifications"]?.toBooleanStrictOrNull()
        syncReminders = saved["syncReminders"]?.toBooleanStrictOrNull() ?: legacyNotifications ?: syncReminders
        conflictReviewAlerts =
            saved["conflictReviewAlerts"]?.toBooleanStrictOrNull() ?: legacyNotifications ?: conflictReviewAlerts
        rejectedEditAlerts =
            saved["rejectedEditAlerts"]?.toBooleanStrictOrNull() ?: legacyNotifications ?: rejectedEditAlerts
    }

    companion object {
        val Saver =
            mapSaver<AppState>(
                save = { state ->
                    // Pairing codes and budget passwords must stay out of saved instance state.
                    mapOf(
                        "theme" to state.theme.name,
                        "accent" to state.accent.name,
                        "selectedBudget" to state.selectedBudget,
                        "selectedBudgetId" to state.selectedBudgetId,
                        "selectedBudgetEncrypted" to state.selectedBudgetEncrypted,
                        "cachedBudgetCatalogJson" to state.cachedBudgetCatalogJson,
                        "selectedMonth" to state.selectedMonth,
                        "selectedMonthId" to state.selectedMonthId,
                        "reportsMonthId" to state.reportsMonthId,
                        "selectedReportKindName" to state.selectedReportKindName,
                        "reportsBudgetId" to state.reportsBudgetId,
                        "reportsPeriodMode" to state.reportsPeriodMode,
                        "reportsRangeMonths" to state.reportsRangeMonths,
                        "reportsRangePreset" to state.reportsRangePreset,
                        "reportsNetWorthScope" to state.reportsNetWorthScope,
                        "savedActivitySearchesJson" to state.savedActivitySearchesJson,
                        "selectedAccountId" to state.selectedAccountId,
                        "selectedCategoryGroupId" to state.selectedCategoryGroupId,
                        "selectedCategoryId" to state.selectedCategoryId,
                        "selectedTransactionRecordId" to state.selectedTransactionRecordId,
                        "selectedTransactionAccountId" to state.selectedTransactionAccountId,
                        "selectedTransactionPayeeId" to state.selectedTransactionPayeeId,
                        "selectedTransactionCategoryId" to state.selectedTransactionCategoryId,
                        "selectedTransactionDate" to state.selectedTransactionDate,
                        "selectedTransactionAccountName" to state.selectedTransactionAccountName,
                        "selectedTransactionPayeeName" to state.selectedTransactionPayeeName,
                        "selectedTransactionCategoryName" to state.selectedTransactionCategoryName,
                        "selectedTransactionNotes" to state.selectedTransactionNotes,
                        "selectedConflictOperationId" to state.selectedConflictOperationId,
                        "serverUrl" to state.serverUrl,
                        "transactionAmount" to state.transactionAmount,
                        "transactionAccount" to state.transactionAccount,
                        "newTransactionAccountId" to state.newTransactionAccountId,
                        "newTransactionInitialAccountId" to state.newTransactionInitialAccountId,
                        "newTransactionInitialAccountName" to state.newTransactionInitialAccountName,
                        "transactionPayee" to state.transactionPayee,
                        "newTransactionPayeeId" to state.newTransactionPayeeId,
                        "transactionCategory" to state.transactionCategory,
                        "newTransactionCategoryId" to state.newTransactionCategoryId,
                        "transactionDate" to state.transactionDate,
                        "newTransactionInitialDate" to state.newTransactionInitialDate,
                        "transactionType" to state.transactionType,
                        "transactionNotes" to state.transactionNotes,
                        "selectedTransactionAmountMinor" to state.selectedTransactionAmountMinor,
                        "selectedTransactionIsLive" to state.selectedTransactionIsLive,
                        "conflictChoice" to state.conflictChoice,
                        "backgroundStyle" to state.backgroundStyle,
                        "homeLayout" to state.homeLayout,
                        "collapsedBudgetSections" to state.collapsedBudgetSections,
                        "pinnedCategoryIds" to state.pinnedCategoryIds,
                        "categoryIconOverrides" to state.categoryIconOverrides,
                        "readOnlyMode" to state.readOnlyMode,
                        "reduceMotionOverride" to state.reduceMotionOverride,
                        "syncOnOpen" to state.syncOnOpen,
                        "backgroundSyncEnabled" to state.backgroundSyncEnabled,
                        "syncOnWifi" to state.syncOnWifi,
                        "syncReminders" to state.syncReminders,
                        "conflictReviewAlerts" to state.conflictReviewAlerts,
                        "rejectedEditAlerts" to state.rejectedEditAlerts,
                        "scrollPositions" to state.scrollPositions.entries.joinToString("|") { "${it.key}:${it.value}" },
                        "lazyScrollPositions" to
                            state.lazyScrollPositions.entries.joinToString("|") { "${it.key}:${it.value.first},${it.value.second}" },
                    )
                },
                restore = { saved ->
                    AppState().apply {
                        theme = ThemeMode.entries.firstOrNull { it.name == saved["theme"] } ?: theme
                        accent = AccentChoice.entries.firstOrNull { it.name == saved["accent"] } ?: accent
                        selectedBudget = saved["selectedBudget"] as? String ?: selectedBudget
                        selectedBudgetId = saved["selectedBudgetId"] as? String ?: selectedBudgetId
                        selectedBudgetEncrypted = saved["selectedBudgetEncrypted"] as? Boolean ?: selectedBudgetEncrypted
                        cachedBudgetCatalogJson = saved["cachedBudgetCatalogJson"] as? String ?: cachedBudgetCatalogJson
                        selectedMonth = saved["selectedMonth"] as? String ?: selectedMonth
                        selectedMonthId = saved["selectedMonthId"] as? String ?: selectedMonthId
                        reportsMonthId = saved["reportsMonthId"] as? String ?: reportsMonthId
                        selectedReportKindName = saved["selectedReportKindName"] as? String ?: selectedReportKindName
                        reportsBudgetId = saved["reportsBudgetId"] as? String ?: reportsBudgetId
                        reportsPeriodMode = saved["reportsPeriodMode"] as? String ?: reportsPeriodMode
                        reportsRangeMonths = (saved["reportsRangeMonths"] as? Int)?.coerceIn(3, 12) ?: reportsRangeMonths
                        reportsRangePreset = saved["reportsRangePreset"] as? String ?: reportsRangePreset
                        reportsNetWorthScope = saved["reportsNetWorthScope"] as? String ?: reportsNetWorthScope
                        savedActivitySearchesJson = saved["savedActivitySearchesJson"] as? String ?: savedActivitySearchesJson
                        selectedAccountId = saved["selectedAccountId"] as? String ?: selectedAccountId
                        selectedCategoryGroupId = saved["selectedCategoryGroupId"] as? String ?: selectedCategoryGroupId
                        selectedCategoryId = saved["selectedCategoryId"] as? String ?: selectedCategoryId
                        selectedTransactionRecordId = saved["selectedTransactionRecordId"] as? String ?: selectedTransactionRecordId
                        selectedTransactionAccountId = saved["selectedTransactionAccountId"] as? String ?: selectedTransactionAccountId
                        selectedTransactionPayeeId = saved["selectedTransactionPayeeId"] as? String ?: selectedTransactionPayeeId
                        selectedTransactionCategoryId = saved["selectedTransactionCategoryId"] as? String ?: selectedTransactionCategoryId
                        selectedTransactionDate = saved["selectedTransactionDate"] as? String ?: selectedTransactionDate
                        selectedTransactionAccountName =
                            saved["selectedTransactionAccountName"] as? String ?: selectedTransactionAccountName
                        selectedTransactionPayeeName = saved["selectedTransactionPayeeName"] as? String ?: selectedTransactionPayeeName
                        selectedTransactionCategoryName =
                            saved["selectedTransactionCategoryName"] as? String ?: selectedTransactionCategoryName
                        selectedTransactionNotes = saved["selectedTransactionNotes"] as? String ?: selectedTransactionNotes
                        selectedConflictOperationId = saved["selectedConflictOperationId"] as? String ?: selectedConflictOperationId
                        serverUrl = saved["serverUrl"] as? String ?: serverUrl
                        transactionAmount = saved["transactionAmount"] as? String ?: transactionAmount
                        transactionAccount = saved["transactionAccount"] as? String ?: transactionAccount
                        newTransactionAccountId = saved["newTransactionAccountId"] as? String ?: newTransactionAccountId
                        newTransactionInitialAccountId =
                            saved["newTransactionInitialAccountId"] as? String ?: newTransactionInitialAccountId
                        newTransactionInitialAccountName =
                            saved["newTransactionInitialAccountName"] as? String ?: newTransactionInitialAccountName
                        transactionPayee = saved["transactionPayee"] as? String ?: transactionPayee
                        newTransactionPayeeId = saved["newTransactionPayeeId"] as? String ?: newTransactionPayeeId
                        transactionCategory = saved["transactionCategory"] as? String ?: transactionCategory
                        newTransactionCategoryId = saved["newTransactionCategoryId"] as? String ?: newTransactionCategoryId
                        transactionDate = saved["transactionDate"] as? String ?: transactionDate
                        newTransactionInitialDate = saved["newTransactionInitialDate"] as? String ?: newTransactionInitialDate
                        transactionType = saved["transactionType"] as? String ?: transactionType
                        transactionNotes = saved["transactionNotes"] as? String ?: transactionNotes
                        selectedTransactionAmountMinor = saved["selectedTransactionAmountMinor"] as? Long
                        selectedTransactionIsLive = saved["selectedTransactionIsLive"] as? Boolean ?: selectedTransactionIsLive
                        conflictChoice = saved["conflictChoice"] as? String ?: conflictChoice
                        backgroundStyle = saved["backgroundStyle"] as? String ?: backgroundStyle
                        homeLayout =
                            (saved["homeLayout"] as? String ?: homeLayout).let {
                                if (it == "Essentials first") "Essentials" else it
                            }
                        collapsedBudgetSections = saved["collapsedBudgetSections"] as? String ?: collapsedBudgetSections
                        pinnedCategoryIds = saved["pinnedCategoryIds"] as? String ?: pinnedCategoryIds
                        categoryIconOverrides = saved["categoryIconOverrides"] as? String ?: categoryIconOverrides
                        readOnlyMode = saved["readOnlyMode"] as? Boolean ?: readOnlyMode
                        selectedBudgetId = saved["selectedBudgetId"] as? String ?: selectedBudgetId
                        reduceMotionOverride =
                            (saved["reduceMotionOverride"] as? Boolean) ?: (saved["reduceMotion"] as? Boolean)
                        syncOnOpen = saved["syncOnOpen"] as? Boolean ?: syncOnOpen
                        backgroundSyncEnabled = saved["backgroundSyncEnabled"] as? Boolean ?: backgroundSyncEnabled
                        syncOnWifi = saved["syncOnWifi"] as? Boolean ?: syncOnWifi
                        val legacyNotifications = saved["notifications"] as? Boolean
                        syncReminders = saved["syncReminders"] as? Boolean ?: legacyNotifications ?: syncReminders
                        conflictReviewAlerts =
                            saved["conflictReviewAlerts"] as? Boolean ?: legacyNotifications ?: conflictReviewAlerts
                        rejectedEditAlerts =
                            saved["rejectedEditAlerts"] as? Boolean ?: legacyNotifications ?: rejectedEditAlerts
                        scrollPositions.putAll(decodeScrollPositions(saved["scrollPositions"] as? String))
                        lazyScrollPositions.putAll(decodeLazyScrollPositions(saved["lazyScrollPositions"] as? String))
                    }
                },
            )
    }
}

internal fun transactionFormHasUnsavedChanges(
    state: AppState,
    route: AppRoute?,
): Boolean {
    val amount = state.parseAmountMinorUnits(state.transactionAmount)
    return when (route) {
        AppRoute.NEW_TRANSACTION ->
            amount == null ||
                amount != 0L ||
                state.newTransactionAccountId != state.newTransactionInitialAccountId ||
                state.transactionAccount != state.newTransactionInitialAccountName ||
                state.transactionPayee.isNotBlank() ||
                state.newTransactionPayeeId.isNotBlank() ||
                state.transactionCategory.isNotBlank() ||
                state.newTransactionCategoryId.isNotBlank() ||
                state.transactionDate != state.newTransactionInitialDate ||
                state.transactionType != "Spending" ||
                state.transactionNotes.isNotBlank()
        AppRoute.EDIT_TRANSACTION ->
            amount == null ||
                state.selectedTransactionAmountMinor?.let { amount != kotlin.math.abs(it) } != false ||
                state.transactionNotes != state.selectedTransactionNotes ||
                state.newTransactionAccountId != state.selectedTransactionAccountId ||
                state.newTransactionPayeeId != state.selectedTransactionPayeeId ||
                state.newTransactionCategoryId != state.selectedTransactionCategoryId ||
                state.transactionDate != state.selectedTransactionDate ||
                state.transactionAccount != state.selectedTransactionAccountName ||
                state.transactionPayee != state.selectedTransactionPayeeName ||
                state.transactionCategory != state.selectedTransactionCategoryName
        else -> false
    }
}

internal fun restoreTransactionFormBaseline(
    state: AppState,
    route: AppRoute?,
) {
    when (route) {
        AppRoute.NEW_TRANSACTION -> {
            state.transactionAmount = state.formatAmount(0)
            state.transactionAccount = state.newTransactionInitialAccountName
            state.newTransactionAccountId = state.newTransactionInitialAccountId
            state.transactionPayee = ""
            state.newTransactionPayeeId = ""
            state.transactionCategory = ""
            state.newTransactionCategoryId = ""
            state.transactionDate = state.newTransactionInitialDate
            state.transactionType = "Spending"
            state.transactionNotes = ""
        }
        AppRoute.EDIT_TRANSACTION -> {
            state.selectedTransactionAmountMinor?.let { state.transactionAmount = state.formatAmount(kotlin.math.abs(it)) }
            state.transactionAccount = state.selectedTransactionAccountName
            state.newTransactionAccountId = state.selectedTransactionAccountId
            state.transactionPayee = state.selectedTransactionPayeeName
            state.newTransactionPayeeId = state.selectedTransactionPayeeId
            state.transactionCategory = state.selectedTransactionCategoryName
            state.newTransactionCategoryId = state.selectedTransactionCategoryId
            state.transactionDate = state.selectedTransactionDate
            state.transactionNotes = state.selectedTransactionNotes
        }
        else -> Unit
    }
}
