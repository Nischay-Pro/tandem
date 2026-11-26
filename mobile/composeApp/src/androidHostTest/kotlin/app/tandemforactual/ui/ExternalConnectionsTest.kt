package app.tandemforactual.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.tandemforactual.api.client.BankIcon
import app.tandemforactual.api.client.BankIconFetcher
import app.tandemforactual.api.client.BankIconFormat
import app.tandemforactual.api.client.BankIcons
import app.tandemforactual.api.client.FileBankIconStore
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.ui.core.session.ForgetBankIconsWhenOff
import app.tandemforactual.ui.core.session.LocalBankIcons
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.feature.onboarding.FirstSyncScreen
import app.tandemforactual.ui.feature.settings.SettingsScreen
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExternalConnectionsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val navigation =
        AppNavigation(open = {}, reset = {}, replaceTop = {}, newTransaction = {}, selectTab = {}, back = {}, backToList = {})

    private fun session() = TandemMobileSession(TandemApiClient(), null, { 0L })

    @Test
    fun bankLogosStayOffUntilTurnedOn() {
        val state = AppState()
        assertFalse(state.bankIconsEnabled)
        assertEquals("false", state.persistentSettings()["bankIconsEnabled"])

        state.restorePersistentSettings(emptyMap())
        assertFalse(state.bankIconsEnabled)

        state.restorePersistentSettings(mapOf("bankIconsEnabled" to "true"))
        assertTrue(state.bankIconsEnabled)
    }

    @Test
    fun setupAsksAboutConnectionsWithBankLogosOff() {
        val state = AppState().apply { selectedBudgetId = "budget-1" }
        val mobileSession = session()
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                FirstSyncScreen(state, mobileSession, navigation, onRetrySync = {}, notificationPermissionEnabled = false)
            }
        }

        composeTestRule.onNodeWithText("Connections to other services").performScrollTo()
        composeTestRule
            .onNodeWithText(
                "Tandem downloads each linked bank’s logo from that bank’s website. The bank can see that your device asked, but nothing about your budget.",
            ).assertExists()
        composeTestRule
            .onNodeWithText("Show bank logos")
            .performScrollTo()
            .assertIsOff()
            .performClick()
        composeTestRule.onNodeWithText("Show bank logos").assertIsOn()
        assertTrue(state.bankIconsEnabled)
    }

    @Test
    fun settingsListsTheSameConnections() {
        val state = AppState()
        val mobileSession = session()
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                SettingsScreen(state, navigation, mobileSession)
            }
        }

        composeTestRule.onNodeWithText("Connections to other services").performScrollTo()
        composeTestRule
            .onNodeWithText("Show bank logos")
            .performScrollTo()
            .assertIsOff()
            .performClick()
        assertTrue(state.bankIconsEnabled)
        composeTestRule.onNodeWithText("Show bank logos").performClick()
        assertFalse(state.bankIconsEnabled)
    }

    @Test
    fun turningBankLogosOffDeletesTheDownloadedLogos() {
        val store = FileBankIconStore(Files.createTempDirectory("bank-icons").toString())
        val icons = BankIcons(store, BankIconFetcher.connect()) { 0L }
        runBlocking { store.saveIcon("bank.com", BankIcon(byteArrayOf(1), BankIconFormat.PNG)) }
        val enabled = mutableStateOf(true)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalBankIcons provides icons) { ForgetBankIconsWhenOff(enabled.value) }
        }

        composeTestRule.waitForIdle()
        assertNotNull(runBlocking { icons.cached("bank.com") })
        composeTestRule.runOnIdle { enabled.value = false }
        repeat(100) {
            composeTestRule.waitForIdle()
            if (runBlocking { icons.cached("bank.com") } == null) return
            Thread.sleep(50)
        }
        fail("Downloaded logos were kept after turning them off")
    }
}
