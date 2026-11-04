package app.tandemforactual.ui

import app.tandemforactual.api.client.BudgetCatalogItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppStateTest {
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
                reduceMotion = true
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
