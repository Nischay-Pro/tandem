package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class AppStateTest {
    @Test
    fun persistentSettingsRoundTripAcrossStateRecreation() {
        val original =
            AppState().apply {
                theme = ThemeMode.PURE_BLACK
                accent = AccentChoice.VIOLET
                backgroundStyle = "Plain"
                homeLayout = "Compact"
                reduceMotion = true
                syncOnOpen = false
                syncOnWifi = true
                syncReminders = false
                conflictReviewAlerts = true
                rejectedEditAlerts = false
                faceUnlock = true
                confirmBeforeDelete = false
            }

        val restored = AppState().apply { restorePersistentSettings(original.persistentSettings()) }

        assertEquals(ThemeMode.PURE_BLACK, restored.theme)
        assertEquals(AccentChoice.VIOLET, restored.accent)
        assertEquals("Plain", restored.backgroundStyle)
        assertEquals("Compact", restored.homeLayout)
        assertEquals(true, restored.reduceMotion)
        assertEquals(false, restored.syncOnOpen)
        assertEquals(true, restored.syncOnWifi)
        assertEquals(false, restored.syncReminders)
        assertEquals(true, restored.conflictReviewAlerts)
        assertEquals(false, restored.rejectedEditAlerts)
        assertEquals(true, restored.faceUnlock)
        assertEquals(false, restored.confirmBeforeDelete)
    }

    @Test
    fun legacyNotificationSettingMigratesToIndependentAlertPreferences() {
        val restored = AppState().apply { restorePersistentSettings(mapOf("notifications" to "false")) }

        assertEquals(false, restored.syncReminders)
        assertEquals(false, restored.conflictReviewAlerts)
        assertEquals(false, restored.rejectedEditAlerts)
    }
}
