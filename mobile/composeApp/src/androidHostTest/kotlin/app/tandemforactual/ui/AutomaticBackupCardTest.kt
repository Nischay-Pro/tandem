package app.tandemforactual.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.tandemforactual.ui.core.session.AutomaticBackupSettings
import app.tandemforactual.ui.core.session.BackupSecrets
import app.tandemforactual.ui.core.session.DeviceCredentialStore
import app.tandemforactual.ui.core.session.LocalBackupSecrets
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.feature.export.AutomaticBackupCard
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
class AutomaticBackupCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val reason = "This budget is end-to-end encrypted, so its backups need a passphrase too."

    private class MemoryStore(
        var value: String? = null,
    ) : DeviceCredentialStore {
        override fun read(): String? = value

        override fun write(value: String) {
            this.value = value
        }

        override fun clear() {
            value = null
        }
    }

    private fun show(
        encrypted: Boolean,
        enabled: Boolean = false,
        passphrase: String? = null,
    ): Pair<AppState, MemoryStore> {
        val store = MemoryStore(passphrase)
        val state =
            AppState().apply {
                selectedBudgetEncrypted = encrypted
                automaticBackup = AutomaticBackupSettings(enabled = enabled, folder = "content://tree/Documents", folderLabel = "Documents")
            }
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                CompositionLocalProvider(LocalBackupSecrets provides BackupSecrets(store, MemoryStore())) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        AutomaticBackupCard(state, preferences = null)
                    }
                }
            }
        }
        return state to store
    }

    @Test
    fun anEncryptedBudgetAsksForAPassphraseBeforeBackingUp() {
        val (state, store) = show(encrypted = true)
        composeTestRule.onNodeWithText("Back up automatically").performClick()
        composeTestRule.onNodeWithText(reason).assertExists()
        assertFalse(state.automaticBackup.enabled)

        composeTestRule.onNodeWithContentDescription("Close").performClick()
        composeTestRule.waitForIdle()
        assertFalse(state.automaticBackup.enabled)
        assertNull(store.value)

        composeTestRule.onNodeWithText("Back up automatically").performClick()
        composeTestRule.onNodeWithContentDescription("Passphrase").performTextInput("correct horse")
        composeTestRule.onNodeWithContentDescription("Confirm passphrase").performTextInput("correct horse")
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitForIdle()
        assertEquals("correct horse", store.value)
        assertTrue(state.automaticBackup.enabled)
    }

    @Test
    fun aPlainBudgetTurnsOnWithoutAPassphrase() {
        val (state, store) = show(encrypted = false)
        composeTestRule.onNodeWithText("Back up automatically").performClick()
        composeTestRule.waitForIdle()
        assertTrue(state.automaticBackup.enabled)
        composeTestRule.onNodeWithText(reason).assertDoesNotExist()
        assertNull(store.value)
    }

    @Test
    fun keepsTheEncryptedBudgetsPassphraseWhileBackupsAreOn() {
        val (state, store) = show(encrypted = true, enabled = true, passphrase = "correct horse")
        composeTestRule.onNodeWithText("Encrypt backups").performScrollTo().performClick()
        composeTestRule.onNodeWithText("$reason To remove it, turn off automatic backups first.").assertExists()
        composeTestRule.onNodeWithText("Stop encrypting").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Close").performClick()

        composeTestRule.onNodeWithText("Back up automatically").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertFalse(state.automaticBackup.enabled)
        composeTestRule.onNodeWithText("Encrypt backups").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Stop encrypting").performClick()
        composeTestRule.waitForIdle()
        assertNull(store.value)
    }

    @Test
    fun saysWhyBackupsArePausedWithoutAPassphrase() {
        show(encrypted = true, enabled = true)
        composeTestRule.onNodeWithText("Paused: set a backup passphrase for this encrypted budget").assertExists()
        composeTestRule.onNodeWithText("Needed before this end-to-end encrypted budget can be backed up.").assertExists()
    }
}
