package app.tandemforactual.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.tandemforactual.api.client.BankCredentialSource
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.regions.eu.bank.gocardless.GoCardless
import app.tandemforactual.data.regions.us.bank.simplefin.SimpleFin
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.feature.banking.BankProviderState
import app.tandemforactual.ui.feature.banking.SimpleFinSetup
import app.tandemforactual.ui.feature.banking.SimpleFinSetupSheet
import app.tandemforactual.ui.feature.banking.credentialFailure
import app.tandemforactual.ui.feature.banking.providerDetail
import app.tandemforactual.ui.feature.banking.providerFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SimpleFinSetupTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun show(
        initial: SimpleFinSetup,
        saved: MutableList<Pair<String, BankCredentialSource>> = mutableListOf(),
        removed: MutableList<Unit> = mutableListOf(),
    ) {
        composeTestRule.setContent {
            var setup by remember { mutableStateOf<SimpleFinSetup?>(initial) }
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                SimpleFinSetupSheet(setup, onDismiss = { setup = null }, onSave = { token, scope -> saved += token to scope }) {
                    removed += Unit
                }
            }
        }
    }

    @Test
    fun adminsChooseBetweenThisBudgetAndEveryBudget() {
        val saved = mutableListOf<Pair<String, BankCredentialSource>>()
        show(SimpleFinSetup(current = null, allowServer = true), saved)
        composeTestRule.onNodeWithText("Save token").assertIsNotEnabled()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("aHR0cHM6Ly9icmlkZ2U=")
        composeTestRule.onNodeWithText("Save token").assertIsEnabled().performClick()
        composeTestRule.onNodeWithText("All budgets on this server").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Save token").performClick()
        assertEquals(
            listOf("aHR0cHM6Ly9icmlkZ2U=" to BankCredentialSource.BUDGET, "aHR0cHM6Ly9icmlkZ2U=" to BankCredentialSource.SERVER),
            saved,
        )
        composeTestRule.onNodeWithText("Remove this budget’s token").assertDoesNotExist()
    }

    @Test
    fun otherUsersOnlySetTheirBudgetsTokenAndCanRemoveIt() {
        val saved = mutableListOf<Pair<String, BankCredentialSource>>()
        val removed = mutableListOf<Unit>()
        val failure = "Couldn’t reach your Actual server. Try again."
        show(SimpleFinSetup(current = BankCredentialSource.BUDGET, error = failure), saved, removed)
        composeTestRule.onNodeWithText("All budgets on this server").assertDoesNotExist()
        composeTestRule.onNodeWithText("Saved for this budget only. Only a server admin can set a token for every budget.").assertExists()
        composeTestRule.onNodeWithText(failure).assertExists()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("token")
        composeTestRule.onNodeWithText("Save token").performClick()
        composeTestRule.onNodeWithText("Remove this budget’s token").performScrollTo().performClick()
        assertEquals(listOf("token" to BankCredentialSource.BUDGET), saved)
        assertEquals(1, removed.size)
    }

    @Test
    fun cardsSayWhoseCredentialsTheBudgetUses() {
        assertEquals(
            "Accounts from this budget’s own credentials",
            providerDetail(SimpleFin, BankProviderState.Ready(emptyList(), BankCredentialSource.BUDGET), setsUpHere = true),
        )
        assertEquals("Accounts your Actual server can see", providerDetail(SimpleFin, BankProviderState.Ready(emptyList()), true))
        assertEquals(
            "This budget’s own SimpleFIN credentials aren’t working. Set them up again.",
            providerDetail(SimpleFin, BankProviderState.NotSetUp(BankCredentialSource.BUDGET), true),
        )
        assertEquals(
            "Not set up for this budget yet. Paste a setup token to start.",
            providerDetail(SimpleFin, BankProviderState.NotSetUp(), true),
        )
        assertTrue(providerDetail(GoCardless, BankProviderState.Idle, false).startsWith("Sign in at your bank"))
        assertEquals(
            "SimpleFIN didn’t accept the token. Get a new setup token and set it up again.",
            providerFailure(SimpleFin, TandemApiException("bank_sync_failed")),
        )
        assertEquals(
            "Couldn’t reach ${GoCardless.displayName} through your Actual server.",
            providerFailure(GoCardless, TandemApiException("bank_sync_failed")),
        )
        assertEquals(
            "Only an Actual server admin can set a token for every budget.",
            credentialFailure(TandemApiException("not_admin")),
        )
        assertEquals(
            "Only this budget’s owner or a server admin can change its bank credentials.",
            credentialFailure(TandemApiException("file_access_denied")),
        )
    }
}
