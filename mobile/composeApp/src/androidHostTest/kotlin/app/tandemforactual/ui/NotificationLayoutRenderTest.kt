package app.tandemforactual.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.home.HomeScreen
import app.tandemforactual.ui.feature.home.PersonalizeScreen
import app.tandemforactual.ui.feature.onboarding.WelcomeScreen
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationLayoutRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val navigation =
        AppNavigation(open = {}, reset = {}, replaceTop = {}, newTransaction = {}, selectTab = {}, back = {}, backToList = {})

    private fun render(
        name: String,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        composeTestRule.setContent {
            TandemTheme(ThemeMode.LIGHT, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(tandemPalette.background)) { content() }
            }
        }
        File("build/reports/setup-$name.png").also { it.parentFile.mkdirs() }.outputStream().use {
            composeTestRule
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun personalizeStep() = render("personalize") { PersonalizeScreen(AppState(), navigation) }

    @Test
    fun homeStep() =
        render("home") {
            HomeScreen(
                AppState(),
                TandemMobileSession(
                    app.tandemforactual.api.client
                        .TandemApiClient(),
                    null,
                    { 0L },
                ),
                navigation,
                emptyList(),
                null,
                emptyList(),
                emptyList(),
            )
        }

    @Test
    fun welcome() = render("welcome") { WelcomeScreen(navigation, onOpenSetupGuide = {}) }
}
