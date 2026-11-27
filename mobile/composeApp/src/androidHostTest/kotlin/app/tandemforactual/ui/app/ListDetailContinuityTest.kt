@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.window.layout.FoldingFeature.Orientation as FoldOrientation
import androidx.window.layout.FoldingFeature.State as FoldState
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppRouteEntry
import app.tandemforactual.ui.core.state.appRouteStackSaver
import app.tandemforactual.ui.design.theme.AccentChoice
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1000dp-h800dp-mdpi")
class ListDetailContinuityTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var widthDp by mutableStateOf(900f)
    private var hinge by mutableStateOf<WindowHinge?>(null)
    private var selected: String? = "Checking"

    @Test
    fun theOpenAccountStaysOpenThroughFoldsUnfoldsAndRecreation() {
        val restoration = StateRestorationTester(composeTestRule)
        restoration.setContent { Harness() }
        composeTestRule.onNodeWithText("list beside detail").assertExists()
        composeTestRule.onNodeWithText("detail Checking").assertExists()

        widthDp = 411f
        composeTestRule.onNodeWithText("page Checking").assertExists()

        restoration.emulateSavedInstanceStateRestore()
        composeTestRule.onNodeWithText("page Checking").assertExists()

        widthDp = 900f
        composeTestRule.onNodeWithText("list beside detail").assertExists()
        composeTestRule.onNodeWithText("detail Checking").assertExists()
        composeTestRule.onNodeWithText("page Checking").assertDoesNotExist()
    }

    @Test
    fun aHalfOpenHingeSplitsThePanesAndLayingFlatKeepsTheItemOpen() {
        widthDp = 673f
        composeTestRule.setContent { Harness() }
        composeTestRule.onNodeWithText("list alone").assertExists()

        hinge = TestWindowLayoutInfo(listOf(fold(FoldState.HALF_OPENED))).separatingVerticalHinge()
        assertEquals(WindowHinge(336f, 336f), hinge)
        composeTestRule.onNodeWithText("list beside detail").assertExists()
        composeTestRule.onNodeWithText("detail Checking").assertExists()

        hinge = TestWindowLayoutInfo(listOf(fold(FoldState.FLAT))).separatingVerticalHinge()
        assertNull(hinge)
        composeTestRule.onNodeWithText("page Checking").assertExists()
    }

    @Test
    fun aListWithNothingOpenStaysTheList() {
        selected = null
        composeTestRule.setContent { Harness() }
        composeTestRule.onNodeWithText("Choose an account").assertExists()
        widthDp = 411f
        composeTestRule.onNodeWithText("list alone").assertExists()
    }

    @Test
    fun onlyAListDetailPairMoves() {
        val open = { _: AppRoute -> true }
        val accounts = listOf(AppRoute.ACCOUNTS, AppRoute.ACCOUNT_DETAIL)
        assertEquals(ListDetailChange.SHOW_PANE, listDetailChange(accounts, panes = true, panesJustChanged = false, open))
        assertNull(listDetailChange(listOf(AppRoute.HOME, AppRoute.ACCOUNT_DETAIL), panes = true, panesJustChanged = true, open))
        assertNull(listDetailChange(accounts, panes = true, panesJustChanged = true) { false })
        assertEquals(
            ListDetailChange.SHOW_PAGE,
            listDetailChange(listOf(AppRoute.ACTIVITY), panes = false, panesJustChanged = true, open),
        )
        assertNull(listDetailChange(listOf(AppRoute.ACTIVITY), panes = false, panesJustChanged = false, open))
        assertNull(listDetailChange(listOf(AppRoute.BUDGET), panes = false, panesJustChanged = true, open))
    }

    private fun fold(state: FoldState) =
        FoldingFeature(
            android.graphics.Rect(0, 0, 673, 800),
            center = 336,
            size = 0,
            state = state,
            orientation = FoldOrientation.VERTICAL,
        )

    @Composable
    private fun Harness() {
        val routes = rememberSaveable(saver = appRouteStackSaver) { mutableStateListOf(AppRouteEntry(AppRoute.ACCOUNTS, 1)) }
        var bounds by remember { mutableStateOf<Rect?>(null) }
        TandemTheme(ThemeMode.DARK, AccentChoice.ACTUAL_PURPLE, reduceMotion = true) {
            CompositionLocalProvider(LocalWindowHinge provides hinge) {
                ListDetailContinuityEffect(bounds, routes.map { it.route }, { selected != null }) { change ->
                    when (change) {
                        ListDetailChange.SHOW_PANE -> routes.removeAt(routes.lastIndex)
                        ListDetailChange.SHOW_PAGE ->
                            routes.add(AppRouteEntry(routes.last().route.listDetailPage()!!, routes.size + 1L))
                    }
                }
                Box(
                    Modifier
                        .width(widthDp.dp)
                        .fillMaxHeight()
                        .onGloballyPositioned { bounds = Rect(it.positionInWindow(), it.size.toSize()) },
                ) {
                    when (routes.last().route) {
                        AppRoute.ACCOUNT_DETAIL -> Text("page $selected")
                        else ->
                            ListDetailPanes(
                                selected,
                                "Choose an account",
                                "Its balance and activity appear here.",
                                list = { inPane -> Text(if (inPane) "list beside detail" else "list alone") },
                                detail = { Box(Modifier.fillMaxSize()) { Text("detail $selected") } },
                            )
                    }
                }
            }
        }
    }
}
