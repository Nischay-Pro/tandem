package app.tandemforactual.ui.feature.schedules

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.planning.AccountKind
import app.tandemforactual.ui.app.WindowPresets
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.ScheduleSeed
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.tandemPalette
import java.io.File
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScheduleCreateScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val accounts = listOf(LiveAccountSummary("checking", "Checking", 120_000, AccountKind.BANK, offBudget = false))

    private fun show(state: AppState) {
        val session = TandemMobileSession(TandemApiClient(), null, { 0L })
        composeTestRule.setContent {
            TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(tandemPalette.surface)) {
                    UpcomingSchedulesScreen(state, session, emptyList(), accounts, emptyList())
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun capture(name: String) {
        val image = composeTestRule.onRoot().captureToImage()
        File("build/reports/schedule-$name.png").also { it.parentFile.mkdirs() }.outputStream().use {
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    @Config(qualifiers = WindowPresets.PHONE)
    fun addOpensANewScheduleOnAPhone() {
        show(AppState().apply { selectedBudgetId = "budget" })
        composeTestRule.onNodeWithText("No upcoming bills").assertExists()
        composeTestRule.onNodeWithContentDescription("Add schedule").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("New schedule").assertExists()
        composeTestRule.onNodeWithText("Money out").assertExists()
        capture("new-phone-dark")
    }

    @Test
    @Config(qualifiers = WindowPresets.LARGE)
    fun aTransactionOpensAFilledScheduleBesideTheList() {
        val state =
            AppState().apply {
                selectedBudgetId = "budget"
                pendingScheduleSeed = ScheduleSeed("t1", "checking", "", "", -4_550, "2026-11-20", "Gym")
            }
        show(state)
        composeTestRule.onNodeWithText("Make this a schedule").assertExists()
        composeTestRule.onNodeWithText("Checking").assertExists()
        assertNull(state.pendingScheduleSeed)
        capture("from-transaction-large-dark")
    }
}
