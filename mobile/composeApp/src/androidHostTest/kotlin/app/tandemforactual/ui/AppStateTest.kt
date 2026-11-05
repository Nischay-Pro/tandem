package app.tandemforactual.ui

import app.tandemforactual.api.client.BudgetCatalogItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppStateTest {
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
    fun sampleAccountSelectionKeepsTheChosenAccountDetails() {
        val savings = previewAccountById("sample:savings")
        val carLoan = previewAccountById("sample:car-loan")

        assertEquals("Savings", savings.name)
        assertEquals(820_000L, savings.balanceMinor)
        assertFalse(savings.offBudget)
        assertEquals("Car loan", carLoan.name)
        assertTrue(carLoan.offBudget)
        assertEquals("Checking", previewAccountById("").name)
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
