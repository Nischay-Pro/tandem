package app.tandemforactual.ui

import app.tandemforactual.api.client.BudgetCatalogItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppStateTest {
    @Test
    fun accountActionButtonsStackWhenSystemTextNeedsMoreWidth() {
        assertFalse(shouldStackActionButtons(1.0f))
        assertFalse(shouldStackActionButtons(1.19f))
        assertTrue(shouldStackActionButtons(1.2f))
        assertTrue(shouldStackActionButtons(2.0f))
    }

    @Test
    fun budgetHeaderStaysCappedAtTwoLinesAtEveryTextSize() {
        assertEquals(2, budgetHeaderTitleMaxLines())
    }

    @Test
    fun pinnedCategoriesAndCollapsedBudgetSectionsSurviveAppRestart() {
        val state =
            AppState().apply {
                collapsedBudgetSections = "live:budget-a:pinned"
                pinnedCategoryIds = "budget-a:category-a"
            }
        val restored = AppState().apply { restorePersistentSettings(state.persistentSettings()) }

        assertEquals(state.collapsedBudgetSections, restored.collapsedBudgetSections)
        assertEquals(state.pinnedCategoryIds, restored.pinnedCategoryIds)
    }

    @Test
    fun oldEssentialsLayoutLabelsMigrateToEssentials() {
        mapOf(
            "Essentials" to "Essentials",
            "Essentials first" to "Essentials",
            "Bills first" to "Essentials",
        ).forEach { (oldLabel, expectedLabel) ->
            val restored = AppState().apply { restorePersistentSettings(mapOf("homeLayout" to oldLabel)) }

            assertEquals(expectedLabel, restored.homeLayout)
        }
    }

    @Test
    fun mainCloudIndicatorDistinguishesOffSyncedSyncingAndAttentionStates() {
        fun status(
            syncEnabled: Boolean = true,
            isSyncing: Boolean = false,
            hasSyncError: Boolean = false,
            persistedSyncStatus: String? = "READY",
            pendingWriteStatuses: Collection<String> = emptyList(),
            hasSuccessfulSync: Boolean = true,
        ) = resolveMainCloudStatus(
            syncEnabled = syncEnabled,
            isSyncing = isSyncing,
            hasSyncError = hasSyncError,
            persistedSyncStatus = persistedSyncStatus,
            pendingWriteStatuses = pendingWriteStatuses,
            hasSuccessfulSync = hasSuccessfulSync,
        )

        assertEquals(MainCloudStatus.OFF, status(syncEnabled = false, hasSyncError = true))
        assertEquals(MainCloudStatus.SYNCED, status())
        assertEquals(MainCloudStatus.SYNCING, status(isSyncing = true, hasSyncError = true))
        assertEquals(MainCloudStatus.SYNCING, status(pendingWriteStatuses = listOf("queued")))
        assertEquals(MainCloudStatus.ERROR, status(hasSyncError = true))
        assertEquals(MainCloudStatus.ERROR, status(persistedSyncStatus = "FAILED"))
        assertEquals(MainCloudStatus.ERROR, status(pendingWriteStatuses = listOf("conflict")))
        assertEquals(MainCloudStatus.ERROR, status(persistedSyncStatus = "IDLE", hasSuccessfulSync = false))
    }

    @Test
    fun newTransactionIsDirtyOnlyAfterUserChangesAFieldAndCanBeRestored() {
        val state =
            AppState().apply {
                transactionAmount = formatAmount(0)
                transactionAccount = "Checking"
                newTransactionAccountId = "checking-id"
                newTransactionInitialAccountName = transactionAccount
                newTransactionInitialAccountId = newTransactionAccountId
                transactionDate = "2026-11-08"
                newTransactionInitialDate = transactionDate
                transactionPayee = ""
                transactionCategory = ""
                transactionType = "Spending"
                transactionNotes = ""
            }

        assertFalse(transactionFormHasUnsavedChanges(state, AppRoute.NEW_TRANSACTION))
        state.transactionPayee = "Corner Market"
        state.newTransactionPayeeId = "payee-id"
        state.transactionAmount = state.formatAmount(4_218)
        assertTrue(transactionFormHasUnsavedChanges(state, AppRoute.NEW_TRANSACTION))

        restoreTransactionFormBaseline(state, AppRoute.NEW_TRANSACTION)

        assertFalse(transactionFormHasUnsavedChanges(state, AppRoute.NEW_TRANSACTION))
        assertEquals("", state.transactionPayee)
        assertEquals(0L, state.parseAmountMinorUnits(state.transactionAmount))
    }

    @Test
    fun editTransactionDetectsChangesToEditableValuesAndRestoresItsBaseline() {
        val state =
            AppState().apply {
                selectedTransactionAmountMinor = -4_218L
                selectedTransactionNotes = "Lunch"
                selectedTransactionAccountId = "checking-id"
                selectedTransactionPayeeId = "payee-id"
                selectedTransactionCategoryId = "food-id"
                selectedTransactionDate = "2026-11-01"
                selectedTransactionAccountName = "Checking"
                selectedTransactionPayeeName = "Corner Market"
                selectedTransactionCategoryName = "Groceries"
                transactionAmount = formatAmount(4_218)
                transactionNotes = "Lunch"
                newTransactionAccountId = "checking-id"
                newTransactionPayeeId = "payee-id"
                newTransactionCategoryId = "food-id"
                transactionDate = "2026-11-01"
                transactionAccount = "Checking"
                transactionPayee = "Corner Market"
                transactionCategory = "Groceries"
            }

        assertFalse(transactionFormHasUnsavedChanges(state, AppRoute.EDIT_TRANSACTION))
        state.transactionDate = "2026-11-02"
        state.transactionNotes = "Dinner"
        assertTrue(transactionFormHasUnsavedChanges(state, AppRoute.EDIT_TRANSACTION))

        restoreTransactionFormBaseline(state, AppRoute.EDIT_TRANSACTION)

        assertFalse(transactionFormHasUnsavedChanges(state, AppRoute.EDIT_TRANSACTION))
        assertEquals("2026-11-01", state.transactionDate)
        assertEquals("Lunch", state.transactionNotes)
    }

    @Test
    fun startupOpensCachedBudgetWithoutWaitingForDeviceCredentialRestore() {
        assertEquals(
            AppStartupDestination.CACHED_BUDGET,
            resolveAppStartupDestination(
                cacheDiscoveryComplete = true,
                hasCachedBudget = true,
                preferencesReady = true,
                preferencesFailed = false,
                sessionRestoreComplete = false,
                isAuthenticated = false,
            ),
        )
        assertEquals(
            AppStartupDestination.WAIT,
            resolveAppStartupDestination(
                cacheDiscoveryComplete = true,
                hasCachedBudget = true,
                preferencesReady = false,
                preferencesFailed = false,
                sessionRestoreComplete = false,
                isAuthenticated = false,
            ),
        )
        assertEquals(
            AppStartupDestination.WELCOME,
            resolveAppStartupDestination(
                cacheDiscoveryComplete = true,
                hasCachedBudget = false,
                preferencesReady = false,
                preferencesFailed = false,
                sessionRestoreComplete = true,
                isAuthenticated = false,
            ),
        )
        assertEquals(
            AppStartupDestination.BUDGET_CHOOSER,
            resolveAppStartupDestination(
                cacheDiscoveryComplete = true,
                hasCachedBudget = false,
                preferencesReady = false,
                preferencesFailed = false,
                sessionRestoreComplete = true,
                isAuthenticated = true,
            ),
        )
    }

    @Test
    fun persistentSettingsRoundTripAcrossStateRecreation() {
        val original =
            AppState().apply {
                theme = ThemeMode.PURE_BLACK
                accent = AccentChoice.VIOLET
                backgroundStyle = "Plain"
                homeLayout = "Compact"
                selectedBudget = "Household"
                selectedBudgetId = "household-budget-id"
                selectedBudgetEncrypted = true
                cachedBudgetCatalogJson = encodeCachedBudgetCatalog(listOf(BudgetCatalogItem("household-budget-id", "Household", true)))
                reduceMotionOverride = true
                syncOnOpen = false
                backgroundSyncEnabled = true
                syncOnWifi = true
                syncReminders = false
                conflictReviewAlerts = true
                rejectedEditAlerts = false
            }

        val restored = AppState().apply { restorePersistentSettings(original.persistentSettings()) }

        assertEquals(ThemeMode.PURE_BLACK, restored.theme)
        assertEquals(AccentChoice.VIOLET, restored.accent)
        assertEquals("Plain", restored.backgroundStyle)
        assertEquals("Compact", restored.homeLayout)
        assertEquals("Household", restored.selectedBudget)
        assertEquals("household-budget-id", restored.selectedBudgetId)
        assertEquals(true, restored.selectedBudgetEncrypted)
        assertEquals(
            listOf(BudgetCatalogItem("household-budget-id", "Household", true)),
            decodeCachedBudgetCatalog(restored.cachedBudgetCatalogJson),
        )
        assertEquals(true, restored.reduceMotion)
        assertEquals(false, restored.syncOnOpen)
        assertEquals(true, restored.backgroundSyncEnabled)
        assertEquals(true, restored.syncOnWifi)
        assertEquals(false, restored.syncReminders)
        assertEquals(true, restored.conflictReviewAlerts)
        assertEquals(false, restored.rejectedEditAlerts)
    }

    @Test
    fun legacyNotificationSettingMigratesToIndependentAlertPreferences() {
        val restored = AppState().apply { restorePersistentSettings(mapOf("notifications" to "false")) }

        assertEquals(false, restored.syncReminders)
        assertEquals(false, restored.conflictReviewAlerts)
        assertEquals(false, restored.rejectedEditAlerts)
    }

    @Test
    fun cachedBudgetCatalogIgnoresInvalidEntries() {
        val cached =
            decodeCachedBudgetCatalog(
                """[{"budgetId":"home","name":"Home","isEncrypted":false},{"budgetId":"bad"},7]""",
            )

        assertEquals(listOf(BudgetCatalogItem("home", "Home", false)), cached)
        assertEquals(emptyList(), decodeCachedBudgetCatalog("not-json"))
    }

    @Test
    fun cachedBudgetCatalogOnlyIncludesBudgetsWithLocalSnapshots() {
        val saved =
            listOf(
                BudgetCatalogItem("cached", "Household", true),
                BudgetCatalogItem("remote-only", "Travel", false),
            )
        val local =
            listOf(BudgetCatalogItem("cached", "Saved budget · CAD", false), BudgetCatalogItem("offline-only", "Saved budget · USD", false))

        assertEquals(
            listOf(BudgetCatalogItem("cached", "Household", true), BudgetCatalogItem("offline-only", "Saved budget · USD", false)),
            cachedBudgetsWithLocalSnapshots(saved, local),
        )
    }

    @Test
    fun serverChoicesKeepCachedBudgetsAvailableForLocalViewing() {
        val server = listOf(BudgetCatalogItem("remote", "Travel", false), BudgetCatalogItem("cached", "Household", true))
        val cached = listOf(BudgetCatalogItem("cached", "Household", true), BudgetCatalogItem("offline", "Saved budget · USD", false))

        assertEquals(
            listOf(server[0], server[1], cached[1]),
            budgetChoicesForSelection(server, cached, serverCatalogLoaded = true),
        )
        assertEquals(cached, budgetChoicesForSelection(server, cached, serverCatalogLoaded = false))
    }

    @Test
    fun automaticSyncRunsOnlyForEligibleVisibleBudgetsAndIsRateLimited() {
        fun eligible(
            lastSyncAt: Long? = null,
            now: Long = 100_000L,
        ) = shouldTriggerAutomaticSync(
            isBudgetVisible = true,
            isAuthenticated = true,
            syncOnOpen = true,
            budgetId = "budget-1",
            isSyncing = false,
            encryptedBudgetNeedsPassword = false,
            hasEncryptionPassword = false,
            lastAutomaticSyncAtEpochMillis = lastSyncAt,
            nowEpochMillis = now,
        )

        assertTrue(eligible())
        assertFalse(eligible(lastSyncAt = 75_000L))
        assertTrue(eligible(lastSyncAt = 69_999L))
        assertFalse(
            shouldTriggerAutomaticSync(
                isBudgetVisible = true,
                isAuthenticated = false,
                syncOnOpen = true,
                budgetId = "budget-1",
                isSyncing = false,
                encryptedBudgetNeedsPassword = false,
                hasEncryptionPassword = false,
                lastAutomaticSyncAtEpochMillis = null,
                nowEpochMillis = 100_000L,
            ),
        )
    }

    @Test
    fun returningToBudgetFromAnotherTabOrDetailDoesNotStartAnExtraSync() {
        assertTrue(shouldTriggerAutomaticSyncForRouteEntry(AppRoute.STARTUP, AppRoute.BUDGET))
        assertTrue(shouldTriggerAutomaticSyncForRouteEntry(AppRoute.BUDGET_CHOOSER, AppRoute.BUDGET))
        assertFalse(shouldTriggerAutomaticSyncForRouteEntry(AppRoute.CATEGORY_GROUP, AppRoute.BUDGET))
        assertFalse(shouldTriggerAutomaticSyncForRouteEntry(AppRoute.ACCOUNTS, AppRoute.BUDGET))
        assertFalse(shouldTriggerAutomaticSyncForRouteEntry(AppRoute.FIRST_SYNC, AppRoute.BUDGET))
    }

    @Test
    fun unsupportedLegacySecurityPreferencesAreNotRestoredOrSaved() {
        val restored =
            AppState().apply {
                restorePersistentSettings(
                    mapOf(
                        "faceUnlock" to "true",
                        "confirmBeforeDelete" to "false",
                    ),
                )
            }

        assertFalse("faceUnlock" in restored.persistentSettings())
        assertFalse("confirmBeforeDelete" in restored.persistentSettings())
    }
}
