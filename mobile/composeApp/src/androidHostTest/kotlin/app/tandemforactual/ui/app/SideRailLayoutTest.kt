package app.tandemforactual.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.InterfaceMode
import app.tandemforactual.ui.core.state.WorkspaceLayout
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.tandemPalette
import java.io.File
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SideRailLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = "w1024dp-h1366dp-xhdpi")
    fun destinationsSitTogetherUnderTheMenuOnTallWindows() {
        renderRail(ThemeMode.LIGHT, InterfaceMode.ADVANCED)

        val density = composeTestRule.density
        val menu = composeTestRule.onNodeWithContentDescription("Menu").fetchSemanticsNode().boundsInRoot
        val tops =
            listOf("Home", "Accounts", "Activity", "Budget", "Reports").map { label ->
                composeTestRule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            }
        tops.zipWithNext().forEach { (above, below) ->
            val gap = with(density) { (below.top - above.bottom).toDp() }
            assertTrue(gap in 0.dp..24.dp, "rail items should sit together, found a $gap gap")
        }
        val belowMenu = with(density) { (tops.first().top - menu.bottom).toDp() }
        assertTrue(belowMenu in 0.dp..40.dp, "destinations should start right under the menu, found $belowMenu")
        val lastBottom = with(density) { tops.last().bottom.toDp() }
        assertTrue(lastBottom < 520.dp, "destinations should stay near the top, last ends at $lastBottom")
    }

    @Test
    @Config(qualifiers = "w1024dp-h1366dp-xhdpi")
    fun everyRailItemHasAnIconLabelAndDescription() {
        renderRail(ThemeMode.LIGHT, InterfaceMode.MINIMAL)

        val clickable = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
        val nodes = composeTestRule.onAllNodes(clickable).fetchSemanticsNodes()
        assertTrue(nodes.size >= 6)
        nodes.forEach { node ->
            val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            assertTrue(!description.isNullOrBlank(), "rail item without a description")
        }
        listOf("Home", "Activity", "Budget", "Settings", "Layout").forEach { label ->
            val labelled = composeTestRule.onAllNodesWithText(label).fetchSemanticsNodes()
            assertTrue(labelled.isNotEmpty(), "missing label $label")
        }
        composeTestRule.onNodeWithContentDescription("Screen layout: ${WorkspaceLayout.SINGLE.label}").assertExists()
    }

    @Test
    @Config(qualifiers = "w1024dp-h1366dp-xhdpi")
    fun capturesTabletRail() {
        capture("rail-ipad-light.png", ThemeMode.LIGHT)
    }

    @Test
    @Config(qualifiers = "w1024dp-h1366dp-xhdpi")
    fun capturesTabletRailDark() {
        capture("rail-ipad-dark.png", ThemeMode.DARK)
    }

    @Test
    @Config(qualifiers = "w900dp-h800dp-xhdpi")
    fun capturesFoldableRail() {
        capture("rail-foldable-light.png", ThemeMode.LIGHT)
    }

    @Test
    @Config(qualifiers = "w900dp-h800dp-xhdpi")
    fun capturesFoldableRailDark() {
        capture("rail-foldable-dark.png", ThemeMode.DARK)
    }

    private fun renderRail(
        theme: ThemeMode,
        mode: InterfaceMode,
        selected: AppRoute = AppRoute.ACTIVITY,
    ) {
        composeTestRule.setContent {
            TandemTheme(theme, AccentChoice.ACTUAL_PURPLE, reduceMotion = true, interfaceMode = mode) {
                Row(Modifier.fillMaxSize().background(tandemPalette.background)) {
                    SideNavigationRail(
                        selected = selected,
                        selectTab = {},
                        layouts = listOf(WorkspaceLayout.SINGLE, WorkspaceLayout.TWO),
                        onOpenMenu = {},
                    )
                    Box(Modifier.weight(1f).padding(TandemSpacing.screen)) {
                        Text("Activity", color = tandemPalette.text)
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun capture(
        name: String,
        theme: ThemeMode,
    ) {
        renderRail(theme, InterfaceMode.MINIMAL)
        val output = File("build/reports/side-rail/$name")
        output.parentFile.mkdirs()
        output.outputStream().use {
            composeTestRule
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
