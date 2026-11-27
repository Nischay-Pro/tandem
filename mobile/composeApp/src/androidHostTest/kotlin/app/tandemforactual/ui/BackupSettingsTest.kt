package app.tandemforactual.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.tandemforactual.ui.core.session.AutomaticBackupSettings
import app.tandemforactual.ui.core.session.BackupSecrets
import app.tandemforactual.ui.core.session.LocalBackupSecrets
import app.tandemforactual.ui.core.session.SyncNetwork
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.feature.export.AutomaticBackupsSection
import app.tandemforactual.ui.feature.export.BackupEncryptionSection
import app.tandemforactual.ui.feature.export.rememberBackupEncryption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BackupSettingsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val reason = "This budget is end-to-end encrypted, so its backups are always encrypted."

    private fun show(
        encrypted: Boolean,
        enabled: Boolean = false,
        passphrase: String? = null,
    ): Pair<AppState, MemoryCredentialStore> {
        val store = MemoryCredentialStore(passphrase)
        val secrets = BackupSecrets(store, MemoryCredentialStore())
        val state =
            AppState().apply {
                selectedBudgetEncrypted = encrypted
                automaticBackup = AutomaticBackupSettings(enabled = enabled, folder = "content://tree/Documents", folderLabel = "Documents")
            }
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                CompositionLocalProvider(LocalBackupSecrets provides secrets) {
                    val encryption = rememberBackupEncryption(secrets, encrypted)
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        AutomaticBackupsSection(state, preferences = null, encryption = encryption)
                        BackupEncryptionSection(encryption)
                    }
                }
            }
        }
        return state to store
    }

    private fun chooseSchedule(label: String) {
        composeTestRule.onNodeWithText("Back up").performClick()
        composeTestRule.onNodeWithText(label).performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun anEncryptedBudgetSetsAPassphraseBeforeAutomaticBackupsStart() {
        val (state, store) = show(encrypted = true)
        chooseSchedule("Daily")
        composeTestRule.onNodeWithText(reason).assertExists()
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        composeTestRule.waitForIdle()
        assertFalse(state.automaticBackup.enabled)
        assertNull(store.value)

        chooseSchedule("Weekly")
        composeTestRule.onNodeWithContentDescription("Passphrase").performTextInput("correct horse")
        composeTestRule.onNodeWithContentDescription("Confirm passphrase").performTextInput("correct horse")
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitForIdle()
        assertEquals("correct horse", store.value)
        assertTrue(state.automaticBackup.enabled)
        assertEquals(168, state.automaticBackup.intervalHours)
    }

    @Test
    fun aPlainBudgetTurnsOnWithoutAPassphraseAndShowsTheRestOnlyWhileOn() {
        val (state, store) = show(encrypted = false)
        composeTestRule.onNodeWithText("Save to").assertDoesNotExist()
        chooseSchedule("Daily")
        assertTrue(state.automaticBackup.enabled)
        assertNull(store.value)
        composeTestRule.onNodeWithText("Save to").assertExists()
        composeTestRule.onNodeWithText("Wi-Fi only for WebDAV uploads").assertExists()

        chooseSchedule("Off")
        assertFalse(state.automaticBackup.enabled)
    }

    @Test
    fun backUpUsingChoosesBetweenWifiOnlyAndAnyNetwork() {
        val (state) = show(encrypted = false, enabled = true)
        assertEquals(SyncNetwork.WIFI, state.automaticBackup.network)
        composeTestRule.onNodeWithText("Back up using").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Wi-Fi or mobile data").performClick()
        composeTestRule.waitForIdle()
        assertEquals(SyncNetwork.ANY, state.automaticBackup.network)
    }

    @Test
    fun anEncryptedBudgetKeepsEncryptionOn() {
        show(encrypted = true, enabled = true, passphrase = "correct horse")
        composeTestRule
            .onNodeWithText("Encrypt backups")
            .performScrollTo()
            .assertIsOn()
            .assertIsNotEnabled()
        composeTestRule.onNodeWithText("Always on for an end-to-end encrypted budget").assertExists()
        composeTestRule.onNodeWithText("Change passphrase").assertExists()
    }

    @Test
    fun turningEncryptionOffAsksFirstThenForgetsThePassphrase() {
        val (_, store) = show(encrypted = false, passphrase = "correct horse")
        composeTestRule.onNodeWithText("Encrypt backups").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Keep encrypting").performClick()
        composeTestRule.waitForIdle()
        assertEquals("correct horse", store.value)

        composeTestRule.onNodeWithText("Encrypt backups").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Stop encrypting").performClick()
        composeTestRule.waitForIdle()
        assertNull(store.value)
        composeTestRule.onNodeWithText("Change passphrase").assertDoesNotExist()
    }

    @Test
    fun saysWhenAnEncryptedBudgetStillNeedsAPassphrase() {
        show(encrypted = true, enabled = true)
        composeTestRule.onNodeWithText("Set a passphrase").performScrollTo()
        composeTestRule.onNodeWithText("Needed before this budget can be backed up").assertExists()
    }
}
