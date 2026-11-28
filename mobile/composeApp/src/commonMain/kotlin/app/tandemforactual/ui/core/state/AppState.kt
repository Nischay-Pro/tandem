package app.tandemforactual.ui.core.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.TrustedCertificates
import app.tandemforactual.data.planning.MonthStart
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.data.store.NotificationPolicy
import app.tandemforactual.data.store.QuietHours
import app.tandemforactual.data.store.WatchSettings
import app.tandemforactual.ui.core.automation.AutomationAccess
import app.tandemforactual.ui.core.format.currentMonthId
import app.tandemforactual.ui.core.format.currentMonthLabel
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.platform.PickedStatementFile
import app.tandemforactual.ui.core.search.ActivitySearchFilter
import app.tandemforactual.ui.core.search.ActivitySortOrder
import app.tandemforactual.ui.core.session.AutomaticBackupResult
import app.tandemforactual.ui.core.session.AutomaticBackupSettings
import app.tandemforactual.ui.core.session.SyncNetwork
import app.tandemforactual.ui.core.session.planning.ExchangeRateFetching
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.ThemeMode

internal fun AppState.restorePendingBudgetSelectionOrigin(): Boolean {
    val origin = pendingBudgetSelectionOrigin ?: return false
    selectedBudget = origin.name
    selectedBudgetId = origin.budgetId
    selectedBudgetEncrypted = origin.isEncrypted
    encryptedPassword = ""
    budgetDisplaySettings = null
    pendingBudgetSelectionOrigin = null
    return true
}

internal const val MENU_PINS_UNSET = "default"

internal class AppState {
    var theme by mutableStateOf(ThemeMode.SYSTEM)
    var accent by mutableStateOf(AccentChoice.ACTUAL_PURPLE)
    var selectedBudget by mutableStateOf("")
    var selectedBudgetId by mutableStateOf("")
    var selectedJurisdictionPeriodId by mutableStateOf("")
    var selectedBudgetEncrypted by mutableStateOf(false)
    var pendingBudgetSelectionOrigin by mutableStateOf<BudgetSelectionSnapshot?>(null)
    var budgetSwitchSplashId by mutableStateOf<String?>(null)
    var cachedBudgetCatalogJson by mutableStateOf("")
    var budgetDisplaySettings by mutableStateOf<BudgetDisplaySettings?>(null)
    var selectedMonth by mutableStateOf(currentMonthLabel())
    var selectedMonthId by mutableStateOf(currentMonthId())
    var isBudgetMonthPickerOpen by mutableStateOf(false)
    var accountsSearchExpanded by mutableStateOf(false)
    var accountsRequestedAction by mutableStateOf<String?>(null)
    var menuOpen by mutableStateOf(false)
    var receiptScanRequested by mutableStateOf(false)
    var pendingReceiptPhoto by mutableStateOf<String?>(null)
    var pendingStatementFile by mutableStateOf<PickedStatementFile?>(null)
    var reportsMonthId by mutableStateOf(currentMonthId())
    var selectedReportKindName by mutableStateOf(ReportKind.SPENDING_BY_CATEGORY.name)
    var reportsBudgetId by mutableStateOf("")
    var reportsPeriodMode by mutableStateOf(ReportPeriodMode.MONTH.name)
    var reportsRangeMonths by mutableStateOf(6)
    var reportsRangePreset by mutableStateOf(ReportRangePreset.LAST_6_MONTHS.name)
    var reportsNetWorthScope by mutableStateOf("All accounts")
    var activitySearchQuery by mutableStateOf("")
    var activitySearchFocusRequest by mutableStateOf(0)
    var activitySearchFilters by mutableStateOf<List<ActivitySearchFilter>>(emptyList())
    var savedActivitySearchesJson by mutableStateOf("")
    var budgetTemplatesJson by mutableStateOf("")
    var statementCsvMappingsJson by mutableStateOf("")
    var activitySortOrder by mutableStateOf(ActivitySortOrder.NEWEST_FIRST)
    var activitySearchCache: ActivitySearchCache? = null
    var activitySearchResultCache: ActivitySearchResultCache? = null
    var selectedAccountId by mutableStateOf("")
    var selectedCategoryGroupId by mutableStateOf("")
    var selectedCategoryId by mutableStateOf("")
    var pendingScheduleToOpenId by mutableStateOf<String?>(null)
    var pendingScheduleSeed by mutableStateOf<ScheduleSeed?>(null)
    var categoryEditRequested by mutableStateOf(false)
    var categoryMoveMoneyRequest by mutableStateOf<MoveMoneyRequest?>(null)
    var selectedTransactionRecordId by mutableStateOf("")
    var selectedCustomReportId by mutableStateOf("")
    var selectedCustomReportName by mutableStateOf("")
    var selectedSharedBillId by mutableStateOf("")
    var selectedSharedBillMemberTag by mutableStateOf("")
    var selectedTransactionAccountId by mutableStateOf("")
    var selectedTransactionPayeeId by mutableStateOf("")
    var selectedTransactionCategoryId by mutableStateOf("")
    var selectedTransactionDate by mutableStateOf("")
    var selectedTransactionAccountName by mutableStateOf("")
    var selectedTransactionPayeeName by mutableStateOf("")
    var selectedTransactionCategoryName by mutableStateOf("")
    var selectedConflictOperationId by mutableStateOf("")
    var ruleEditorRuleId by mutableStateOf<String?>(null)
    var rulesReferenceFilter by mutableStateOf<RulesReferenceFilter?>(null)
    var pointerInputSequence by mutableStateOf(0)
    var serverUrl by mutableStateOf("")

    /** Custom headers as typed while signing in. Kept with the saved sign-in once it works, never in settings. */
    var serverHeaders by mutableStateOf("")
    var recentServerUrls by mutableStateOf("")
    var actualPassword by mutableStateOf("")
    var encryptedPassword by mutableStateOf("")
    var transactionAmount by mutableStateOf(formatAmount(0))
    var transactionAccount by mutableStateOf("")
    var newTransactionAccountId by mutableStateOf("")
    var lastTransactionAccountId by mutableStateOf("")
    var newTransactionInitialAccountId by mutableStateOf("")
    var newTransactionInitialAccountName by mutableStateOf("")
    var transactionPayee by mutableStateOf("")
    var newTransactionPayeeId by mutableStateOf("")
    var transactionCategory by mutableStateOf("")
    var newTransactionCategoryId by mutableStateOf("")
    var transactionDate by mutableStateOf(formatLiveDate(platformTodayIsoDate()))
    var newTransactionInitialDate by mutableStateOf("")
    var transactionType by mutableStateOf("Spending")
    var transactionNotes by mutableStateOf("")
    var transactionSplit by mutableStateOf<TransactionSplitDraft?>(null)

    /** The split the edit form opened with, or null when the transaction being edited wasn't split. */
    var selectedTransactionSplit by mutableStateOf<TransactionSplitDraft?>(null)
    var selectedTransactionAmountMinor by mutableStateOf<Long?>(null)
    var selectedTransactionNotes by mutableStateOf("")
    var selectedTransactionIsLive by mutableStateOf(false)
    var selectedTransactionIsTransfer by mutableStateOf(false)
    var conflictChoice by mutableStateOf("")
    var backgroundStyle by mutableStateOf("Solid surface")
    var workspace by mutableStateOf(Workspace())
    var homeLayout by mutableStateOf("Comfortable")
    var budgetShowProgressBars by mutableStateOf(false)

    /** The day of the month reports and insights start each month on, such as payday; 1 is the calendar month. */
    var monthStartDay by mutableStateOf(1)

    /** Whether a month that would start on a weekend starts on the Friday before. */
    var monthStartPreviousWorkingDay by mutableStateOf(false)

    val monthStart: MonthStart get() = MonthStart(monthStartDay.coerceIn(1, MonthStart.MAX_DAY), monthStartPreviousWorkingDay)

    /** Whether the budget leaves out categories whose money has all been spent this month. */
    var budgetHideSpentCategories by mutableStateOf(false)

    /** Whether category amounts are green or red for what's left or overspent; plain text when off. */
    var budgetStatusColors by mutableStateOf(true)

    /** Whether each category group's header shows its totals. */
    var budgetGroupTotals by mutableStateOf(true)

    /** The sort the accounts are in right now, or null after they were arranged by hand. */
    var accountsActiveSort by mutableStateOf<AccountSort?>(null)
    var privacyModeEnabled by mutableStateOf(false)

    /** Whether linked banks' icons are downloaded from their own websites. */
    var bankIconsEnabled by mutableStateOf(false)

    /** Whether a two-finger double tap anywhere turns Hide budget amounts on or off. */
    var hideAmountsGesture by mutableStateOf(false)

    private var trustedCertificatesState by mutableStateOf("")

    /** Certificates trusted for their own servers, as `host=fingerprint` lines; every API client uses them. */
    var trustedCertificates: String
        get() = trustedCertificatesState
        set(value) {
            trustedCertificatesState = value
            TrustedCertificates.restore(value)
        }

    /** The tab the app opens on, by route name; Home when blank or not shown. */
    var startPageName by mutableStateOf("")

    /** The account new transactions start in, ahead of the last one used; none when blank. */
    var defaultTransactionAccountId by mutableStateOf("")

    /** Whether saving a transaction shows what's left in its category and the account's balance. */
    var balanceAfterSaveEnabled by mutableStateOf(true)
    var exchangeRateFetching by mutableStateOf(ExchangeRateFetching())
    var watchSettings by mutableStateOf(WatchSettings())

    /** Whether automation apps such as Tasker may add transactions, and separately read budget numbers. */
    var automationWritesEnabled by mutableStateOf(false)
    var automationReadsEnabled by mutableStateOf(false)
    var collapsedBudgetSections by mutableStateOf("")
    var pinnedCategoryIds by mutableStateOf("")
    var menuPins by mutableStateOf(MENU_PINS_UNSET)
    var categoryIconOverrides by mutableStateOf("")
    var readOnlyMode by mutableStateOf(false)
    var hasDismissedBudgetHint by mutableStateOf(false)
    var biometricLockEnabled by mutableStateOf(false)
    var rememberBudgetPasswordWithBiometrics by mutableStateOf(false)
    var reduceMotionOverride by mutableStateOf<Boolean?>(null)
    var systemReduceMotionEnabled by mutableStateOf(false)
    val reduceMotion: Boolean get() = reduceMotionOverride ?: systemReduceMotionEnabled
    var syncOnOpen by mutableStateOf(true)
    var appLanguage by mutableStateOf("")
    var budgetScrollToSummaryRequested by mutableStateOf(false)
    var backgroundSyncEnabled by mutableStateOf(false)
    var automaticBackup by mutableStateOf(AutomaticBackupSettings())

    /** When an automatic backup last reached a destination, as the background task recorded it. */
    var automaticBackupLastRun by mutableStateOf<Long?>(null)
    var automaticBackupResult by mutableStateOf<AutomaticBackupResult?>(null)
    var syncNetwork by mutableStateOf(SyncNetwork.ANY)
    var hasCompletedSyncOnboarding by mutableStateOf(false)
    var interfaceMode by mutableStateOf(InterfaceMode.MINIMAL)
    var modeSwitchTarget by mutableStateOf<InterfaceMode?>(null)

    /** Starts a switch; the app applies it once the splash covers the screen. */
    fun switchInterfaceMode(mode: InterfaceMode) {
        if (mode != interfaceMode && modeSwitchTarget == null) modeSwitchTarget = mode
    }

    var experienceSettingsJson by mutableStateOf("{}")
    var backupLogJson by mutableStateOf("{}")

    /** Personalization for the open budget; null until the person has decided, so setup can ask once. */
    val experience: ExperienceSettings? get() = decodeExperienceSettings(experienceSettingsJson)[selectedBudgetId]

    fun updateExperience(change: (ExperienceSettings) -> ExperienceSettings) {
        val all = decodeExperienceSettings(experienceSettingsJson)
        experienceSettingsJson = encodeExperienceSettings(all + (selectedBudgetId to change(all[selectedBudgetId] ?: ExperienceSettings())))
    }

    fun resetExperience() {
        experienceSettingsJson = encodeExperienceSettings(decodeExperienceSettings(experienceSettingsJson) - selectedBudgetId)
    }

    var pendingSetupAfterUnlock by mutableStateOf(false)
    var unlockFailureCount by mutableStateOf(0)
    var localServerUrl by mutableStateOf("")
    var unlockRetryAtEpochMillis by mutableStateOf(0L)
    var syncReminders by mutableStateOf(false)
    var conflictReviewAlerts by mutableStateOf(false)
    var rejectedEditAlerts by mutableStateOf(false)
    var overspendingAlerts by mutableStateOf(false)
    var uncategorizedAlerts by mutableStateOf(false)
    var bankSyncAlerts by mutableStateOf(false)
    var upcomingBillAlerts by mutableStateOf(false)
    var cardDueAlerts by mutableStateOf(false)
    var largeTransactionAlerts by mutableStateOf(false)
    var quietHours by mutableStateOf("")
    var largeTransactionMinor by mutableStateOf(NotificationPolicy.DEFAULT_LARGE_TRANSACTION_MINOR)
    var billLeadDays by mutableStateOf(NotificationPolicy.DEFAULT_BILL_LEAD_DAYS)
    var encryptedAlertDetails by mutableStateOf(false)
    var settingsStorageError by mutableStateOf<String?>(null)
    var settingsStorageRetry by mutableStateOf(0)
    var settingsStorageReady by mutableStateOf(false)
    val scrollPositions = mutableMapOf<String, Int>()
    val lazyScrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    fun clearBudgetSession() {
        selectedBudget = ""
        selectedBudgetId = ""
        selectedBudgetEncrypted = false
        cachedBudgetCatalogJson = ""
        budgetDisplaySettings = null
        reportsBudgetId = ""
        reportsMonthId = currentMonthId()
        selectedReportKindName = ReportKind.SPENDING_BY_CATEGORY.name
        savedActivitySearchesJson = ""
        budgetTemplatesJson = ""
        selectedAccountId = ""
        selectedCategoryGroupId = ""
        selectedCategoryId = ""
        selectedMonth = currentMonthLabel()
        selectedMonthId = currentMonthId()
        pendingScheduleToOpenId = null
        pendingScheduleSeed = null
        categoryEditRequested = false
        categoryMoveMoneyRequest = null
        selectedTransactionRecordId = ""
        selectedTransactionAccountId = ""
        selectedTransactionPayeeId = ""
        selectedTransactionCategoryId = ""
        selectedTransactionDate = ""
        selectedTransactionAccountName = ""
        selectedTransactionPayeeName = ""
        selectedTransactionCategoryName = ""
        selectedConflictOperationId = ""
        actualPassword = ""
        encryptedPassword = ""
        transactionAmount = formatAmount(0)
        transactionAccount = ""
        newTransactionAccountId = ""
        newTransactionInitialAccountId = ""
        newTransactionInitialAccountName = ""
        transactionPayee = ""
        newTransactionPayeeId = ""
        transactionCategory = ""
        newTransactionCategoryId = ""
        transactionDate = platformTodayIsoDate()
        newTransactionInitialDate = ""
        transactionType = "Spending"
        transactionNotes = ""
        selectedTransactionAmountMinor = null
        selectedTransactionNotes = ""
        selectedTransactionIsLive = false
        selectedTransactionIsTransfer = false
        conflictChoice = ""
        collapsedBudgetSections = ""
        pinnedCategoryIds = ""
        categoryIconOverrides = ""
        activitySearchQuery = ""
        activitySearchFilters = emptyList()
        rulesReferenceFilter = null
        activitySearchCache = null
        activitySearchResultCache = null
        scrollPositions.clear()
        lazyScrollPositions.clear()
    }

    fun persistentSettings(): Map<String, String> =
        mapOf(
            "theme" to theme.name,
            "accent" to accent.name,
            "backgroundStyle" to backgroundStyle,
            "workspace" to workspace.encode(),
            "homeLayout" to homeLayout,
            "budgetShowProgressBars" to budgetShowProgressBars.toString(),
            "monthStartDay" to monthStartDay.toString(),
            "monthStartPreviousWorkingDay" to monthStartPreviousWorkingDay.toString(),
            "budgetHideSpentCategories" to budgetHideSpentCategories.toString(),
            "budgetStatusColors" to budgetStatusColors.toString(),
            "budgetGroupTotals" to budgetGroupTotals.toString(),
            "privacyModeEnabled" to privacyModeEnabled.toString(),
            "bankIconsEnabled" to bankIconsEnabled.toString(),
            "hideAmountsGesture" to hideAmountsGesture.toString(),
            TrustedCertificates.PREFERENCE_KEY to trustedCertificates,
            "startPage" to startPageName,
            "defaultTransactionAccountId" to defaultTransactionAccountId,
            "balanceAfterSaveEnabled" to balanceAfterSaveEnabled.toString(),
            AutomationAccess.WRITES to automationWritesEnabled.toString(),
            AutomationAccess.READS to automationReadsEnabled.toString(),
            "collapsedBudgetSections" to collapsedBudgetSections,
            "pinnedCategoryIds" to pinnedCategoryIds,
            "menuPins" to menuPins,
            "categoryIconOverrides" to categoryIconOverrides,
            "readOnlyMode" to readOnlyMode.toString(),
            "hasDismissedBudgetHint" to hasDismissedBudgetHint.toString(),
            "biometricLockEnabled" to biometricLockEnabled.toString(),
            "selectedBudget" to selectedBudget,
            "selectedBudgetId" to selectedBudgetId,
            "selectedBudgetEncrypted" to selectedBudgetEncrypted.toString(),
            "serverUrl" to serverUrl,
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
            "statementCsvMappingsJson" to statementCsvMappingsJson,
            "reduceMotionMode" to (reduceMotionOverride?.let { if (it) "on" else "off" } ?: "system"),
            "syncOnOpen" to syncOnOpen.toString(),
            "appLanguage" to appLanguage,
            "backgroundSyncEnabled" to backgroundSyncEnabled.toString(),
        ) + automaticBackup.toPreferences() + exchangeRateFetching.toPreferences() + watchSettings.toPreferences() +
            mapOf(
                "syncNetwork" to syncNetwork.name,
                "hasCompletedSyncOnboarding" to hasCompletedSyncOnboarding.toString(),
                "interfaceMode" to interfaceMode.name,
                "experienceSettingsJson" to experienceSettingsJson,
                "backupLogJson" to backupLogJson,
                "unlockFailureCount" to unlockFailureCount.toString(),
                "localServerUrl" to localServerUrl,
                "lastTransactionAccountId" to lastTransactionAccountId,
                "recentServerUrls" to recentServerUrls,
                "unlockRetryAtEpochMillis" to unlockRetryAtEpochMillis.toString(),
                "syncReminders" to syncReminders.toString(),
                "conflictReviewAlerts" to conflictReviewAlerts.toString(),
                "rejectedEditAlerts" to rejectedEditAlerts.toString(),
                "overspendingAlerts" to overspendingAlerts.toString(),
                "uncategorizedAlerts" to uncategorizedAlerts.toString(),
                "bankSyncAlerts" to bankSyncAlerts.toString(),
                "upcomingBillAlerts" to upcomingBillAlerts.toString(),
                "cardDueAlerts" to cardDueAlerts.toString(),
                "largeTransactionAlerts" to largeTransactionAlerts.toString(),
                NotificationPolicy.QUIET_HOURS to quietHours,
                NotificationPolicy.LARGE_TRANSACTION_MINOR to largeTransactionMinor.toString(),
                NotificationPolicy.BILL_LEAD_DAYS to billLeadDays.toString(),
                NotificationPolicy.ENCRYPTED_DETAILS to encryptedAlertDetails.toString(),
            )

    fun restoreAutomaticBackupStatus(saved: Map<String, String>) {
        automaticBackupLastRun = saved[AutomaticBackupSettings.LAST_RUN]?.toLongOrNull()
        automaticBackupResult = AutomaticBackupResult.decode(saved[AutomaticBackupResult.KEY])
    }

    fun restorePersistentSettings(saved: Map<String, String>) {
        theme = ThemeMode.entries.firstOrNull { it.name == saved["theme"] } ?: theme
        accent = AccentChoice.entries.firstOrNull { it.name == saved["accent"] } ?: accent
        backgroundStyle = saved["backgroundStyle"] ?: backgroundStyle
        workspace = saved["workspace"]?.let(Workspace::decode) ?: workspace
        homeLayout = normalizeHomeLayoutName(saved["homeLayout"] ?: homeLayout)
        budgetShowProgressBars = saved["budgetShowProgressBars"]?.toBooleanStrictOrNull() ?: budgetShowProgressBars
        monthStartDay = saved["monthStartDay"]?.toIntOrNull()?.coerceIn(1, MonthStart.MAX_DAY) ?: monthStartDay
        monthStartPreviousWorkingDay = saved["monthStartPreviousWorkingDay"]?.toBooleanStrictOrNull() ?: monthStartPreviousWorkingDay
        budgetHideSpentCategories = saved["budgetHideSpentCategories"]?.toBooleanStrictOrNull() ?: budgetHideSpentCategories
        budgetStatusColors = saved["budgetStatusColors"]?.toBooleanStrictOrNull() ?: budgetStatusColors
        budgetGroupTotals = saved["budgetGroupTotals"]?.toBooleanStrictOrNull() ?: budgetGroupTotals
        privacyModeEnabled = saved["privacyModeEnabled"]?.toBooleanStrictOrNull() ?: privacyModeEnabled
        bankIconsEnabled = saved["bankIconsEnabled"]?.toBooleanStrictOrNull() ?: bankIconsEnabled
        hideAmountsGesture = saved["hideAmountsGesture"]?.toBooleanStrictOrNull() ?: hideAmountsGesture
        trustedCertificates = saved[TrustedCertificates.PREFERENCE_KEY] ?: trustedCertificates
        startPageName = saved["startPage"] ?: startPageName
        defaultTransactionAccountId = saved["defaultTransactionAccountId"] ?: defaultTransactionAccountId
        balanceAfterSaveEnabled = saved["balanceAfterSaveEnabled"]?.toBooleanStrictOrNull() ?: balanceAfterSaveEnabled
        automationWritesEnabled = saved[AutomationAccess.WRITES]?.toBooleanStrictOrNull() ?: automationWritesEnabled
        automationReadsEnabled = saved[AutomationAccess.READS]?.toBooleanStrictOrNull() ?: automationReadsEnabled
        exchangeRateFetching = ExchangeRateFetching.from(saved)
        watchSettings = WatchSettings.from(saved)
        collapsedBudgetSections = saved["collapsedBudgetSections"] ?: collapsedBudgetSections
        pinnedCategoryIds = saved["pinnedCategoryIds"] ?: pinnedCategoryIds
        menuPins = saved["menuPins"] ?: menuPins
        categoryIconOverrides = saved["categoryIconOverrides"] ?: categoryIconOverrides
        readOnlyMode = saved["readOnlyMode"]?.toBooleanStrictOrNull() ?: readOnlyMode
        hasDismissedBudgetHint = saved["hasDismissedBudgetHint"]?.toBooleanStrictOrNull() ?: hasDismissedBudgetHint
        biometricLockEnabled = saved["biometricLockEnabled"]?.toBooleanStrictOrNull() ?: biometricLockEnabled
        selectedBudget = saved["selectedBudget"] ?: selectedBudget
        selectedBudgetId = saved["selectedBudgetId"] ?: selectedBudgetId
        selectedBudgetEncrypted = saved["selectedBudgetEncrypted"]?.toBooleanStrictOrNull() ?: selectedBudgetEncrypted
        serverUrl =
            saved["serverUrl"]
                ?.takeUnless { it.trim().equals("https://budget.example.com", ignoreCase = true) }
                ?: serverUrl
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
        statementCsvMappingsJson = saved["statementCsvMappingsJson"] ?: statementCsvMappingsJson
        reduceMotionOverride =
            when (saved["reduceMotionMode"]) {
                "on" -> true
                "off" -> false
                "system" -> null
                else -> saved["reduceMotion"]?.toBooleanStrictOrNull() ?: reduceMotionOverride
            }
        syncOnOpen = saved["syncOnOpen"]?.toBooleanStrictOrNull() ?: syncOnOpen
        appLanguage = saved["appLanguage"] ?: appLanguage
        backgroundSyncEnabled = saved["backgroundSyncEnabled"]?.toBooleanStrictOrNull() ?: backgroundSyncEnabled
        automaticBackup = AutomaticBackupSettings.from(saved)
        restoreAutomaticBackupStatus(saved)
        syncNetwork = SyncNetwork.restore(saved["syncNetwork"], saved["syncOnWifi"]?.toBooleanStrictOrNull()) ?: syncNetwork
        hasCompletedSyncOnboarding = saved["hasCompletedSyncOnboarding"]?.toBooleanStrictOrNull() ?: hasCompletedSyncOnboarding
        interfaceMode = InterfaceMode.entries.firstOrNull { it.name == saved["interfaceMode"] } ?: interfaceMode
        experienceSettingsJson = saved["experienceSettingsJson"] ?: experienceSettingsJson
        backupLogJson = saved["backupLogJson"] ?: backupLogJson
        unlockFailureCount = saved["unlockFailureCount"]?.toIntOrNull()?.coerceAtLeast(0) ?: unlockFailureCount
        localServerUrl = saved["localServerUrl"] ?: localServerUrl
        lastTransactionAccountId = saved["lastTransactionAccountId"] ?: lastTransactionAccountId
        recentServerUrls = saved["recentServerUrls"] ?: recentServerUrls
        unlockRetryAtEpochMillis = saved["unlockRetryAtEpochMillis"]?.toLongOrNull()?.coerceAtLeast(0L) ?: unlockRetryAtEpochMillis
        restoreNotificationSettings(saved)
    }

    private fun restoreNotificationSettings(saved: Map<String, String>) {
        val legacyNotifications = saved["notifications"]?.toBooleanStrictOrNull()
        syncReminders = saved["syncReminders"]?.toBooleanStrictOrNull() ?: legacyNotifications ?: syncReminders
        conflictReviewAlerts =
            saved["conflictReviewAlerts"]?.toBooleanStrictOrNull() ?: legacyNotifications ?: conflictReviewAlerts
        rejectedEditAlerts =
            saved["rejectedEditAlerts"]?.toBooleanStrictOrNull() ?: legacyNotifications ?: rejectedEditAlerts
        overspendingAlerts = saved["overspendingAlerts"]?.toBooleanStrictOrNull() ?: overspendingAlerts
        uncategorizedAlerts = saved["uncategorizedAlerts"]?.toBooleanStrictOrNull() ?: uncategorizedAlerts
        bankSyncAlerts = saved["bankSyncAlerts"]?.toBooleanStrictOrNull() ?: bankSyncAlerts
        upcomingBillAlerts = saved["upcomingBillAlerts"]?.toBooleanStrictOrNull() ?: upcomingBillAlerts
        cardDueAlerts = saved["cardDueAlerts"]?.toBooleanStrictOrNull() ?: cardDueAlerts
        largeTransactionAlerts = saved["largeTransactionAlerts"]?.toBooleanStrictOrNull() ?: largeTransactionAlerts
        quietHours = saved[NotificationPolicy.QUIET_HOURS]?.takeIf { QuietHours.parse(it) != null } ?: quietHours
        largeTransactionMinor =
            saved[NotificationPolicy.LARGE_TRANSACTION_MINOR]?.toLongOrNull()?.takeIf { it > 0 } ?: largeTransactionMinor
        billLeadDays = saved[NotificationPolicy.BILL_LEAD_DAYS]?.toIntOrNull()?.coerceIn(0, 14) ?: billLeadDays
        encryptedAlertDetails = saved[NotificationPolicy.ENCRYPTED_DETAILS]?.toBooleanStrictOrNull() ?: encryptedAlertDetails
    }

    /** The phone notification choices as background sync reads them. */
    val notificationPolicy: NotificationPolicy get() = NotificationPolicy.read(persistentSettings())

    companion object {
        val Saver =
            mapSaver<AppState>(
                save = { state ->
                    // Actual and budget passwords must stay out of saved instance state.
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
                        "statementCsvMappingsJson" to state.statementCsvMappingsJson,
                        "selectedAccountId" to state.selectedAccountId,
                        "selectedCategoryGroupId" to state.selectedCategoryGroupId,
                        "selectedCategoryId" to state.selectedCategoryId,
                        "pendingScheduleToOpenId" to state.pendingScheduleToOpenId,
                        "selectedTransactionRecordId" to state.selectedTransactionRecordId,
                        "selectedTransactionAccountId" to state.selectedTransactionAccountId,
                        "selectedTransactionPayeeId" to state.selectedTransactionPayeeId,
                        "selectedTransactionCategoryId" to state.selectedTransactionCategoryId,
                        "selectedTransactionDate" to state.selectedTransactionDate,
                        "selectedTransactionAccountName" to state.selectedTransactionAccountName,
                        "selectedTransactionPayeeName" to state.selectedTransactionPayeeName,
                        "selectedTransactionCategoryName" to state.selectedTransactionCategoryName,
                        "selectedTransactionNotes" to state.selectedTransactionNotes,
                        "selectedTransactionIsTransfer" to state.selectedTransactionIsTransfer,
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
                        "workspace" to state.workspace.encode(),
                        "homeLayout" to state.homeLayout,
                        "budgetShowProgressBars" to state.budgetShowProgressBars,
                        "privacyModeEnabled" to state.privacyModeEnabled,
                        "collapsedBudgetSections" to state.collapsedBudgetSections,
                        "pinnedCategoryIds" to state.pinnedCategoryIds,
                        "categoryIconOverrides" to state.categoryIconOverrides,
                        "readOnlyMode" to state.readOnlyMode,
                        "hasDismissedBudgetHint" to state.hasDismissedBudgetHint,
                        "biometricLockEnabled" to state.biometricLockEnabled,
                        "reduceMotionOverride" to state.reduceMotionOverride,
                        "syncOnOpen" to state.syncOnOpen,
                        "appLanguage" to state.appLanguage,
                        "backgroundSyncEnabled" to state.backgroundSyncEnabled,
                        "syncNetwork" to state.syncNetwork.name,
                        "hasCompletedSyncOnboarding" to state.hasCompletedSyncOnboarding,
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
                        statementCsvMappingsJson = saved["statementCsvMappingsJson"] as? String ?: statementCsvMappingsJson
                        selectedAccountId = saved["selectedAccountId"] as? String ?: selectedAccountId
                        selectedCategoryGroupId = saved["selectedCategoryGroupId"] as? String ?: selectedCategoryGroupId
                        selectedCategoryId = saved["selectedCategoryId"] as? String ?: selectedCategoryId
                        pendingScheduleToOpenId = saved["pendingScheduleToOpenId"] as? String
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
                        selectedTransactionIsTransfer = saved["selectedTransactionIsTransfer"] as? Boolean ?: selectedTransactionIsTransfer
                        selectedConflictOperationId = saved["selectedConflictOperationId"] as? String ?: selectedConflictOperationId
                        serverUrl =
                            (saved["serverUrl"] as? String)
                                ?.takeUnless { it.trim().equals("https://budget.example.com", ignoreCase = true) }
                                ?: serverUrl
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
                        workspace = (saved["workspace"] as? String)?.let(Workspace::decode) ?: workspace
                        homeLayout = normalizeHomeLayoutName(saved["homeLayout"] as? String ?: homeLayout)
                        budgetShowProgressBars = saved["budgetShowProgressBars"] as? Boolean ?: budgetShowProgressBars
                        privacyModeEnabled = saved["privacyModeEnabled"] as? Boolean ?: privacyModeEnabled
                        collapsedBudgetSections = saved["collapsedBudgetSections"] as? String ?: collapsedBudgetSections
                        pinnedCategoryIds = saved["pinnedCategoryIds"] as? String ?: pinnedCategoryIds
                        categoryIconOverrides = saved["categoryIconOverrides"] as? String ?: categoryIconOverrides
                        readOnlyMode = saved["readOnlyMode"] as? Boolean ?: readOnlyMode
                        hasDismissedBudgetHint = saved["hasDismissedBudgetHint"] as? Boolean ?: hasDismissedBudgetHint
                        biometricLockEnabled = saved["biometricLockEnabled"] as? Boolean ?: biometricLockEnabled
                        selectedBudgetId = saved["selectedBudgetId"] as? String ?: selectedBudgetId
                        reduceMotionOverride =
                            (saved["reduceMotionOverride"] as? Boolean) ?: (saved["reduceMotion"] as? Boolean)
                        syncOnOpen = saved["syncOnOpen"] as? Boolean ?: syncOnOpen
                        appLanguage = saved["appLanguage"] as? String ?: appLanguage
                        backgroundSyncEnabled = saved["backgroundSyncEnabled"] as? Boolean ?: backgroundSyncEnabled
                        syncNetwork = SyncNetwork.restore(saved["syncNetwork"] as? String, saved["syncOnWifi"] as? Boolean) ?: syncNetwork
                        hasCompletedSyncOnboarding = saved["hasCompletedSyncOnboarding"] as? Boolean ?: hasCompletedSyncOnboarding
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
