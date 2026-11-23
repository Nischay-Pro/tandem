package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.AppMenuItem
import app.tandemforactual.ui.app.MenuEntry
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.retirement.MatchEditorScreen
import app.tandemforactual.ui.feature.retirement.PlanLimitsScreen
import app.tandemforactual.ui.feature.retirement.PublicPensionsScreen
import app.tandemforactual.ui.feature.retirement.RetirementScreen
import app.tandemforactual.ui.feature.retirement.RetirementSetupScreen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank

/** Screens for the retirement routes. */
internal val retirementScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.RETIREMENT to
            @Composable { ctx ->
                with(ctx) {
                    RetirementScreen(state, navigation, mobileSession, liveAccounts)
                }
            },
        AppRoute.RETIREMENT_SETUP to
            @Composable { ctx ->
                with(ctx) {
                    RetirementSetupScreen(state, navigation, mobileSession, liveAccounts)
                }
            },
        AppRoute.PUBLIC_PENSIONS to
            @Composable { ctx ->
                with(ctx) {
                    PublicPensionsScreen(state, navigation, mobileSession)
                }
            },
        AppRoute.RETIREMENT_MATCH to
            @Composable { ctx ->
                with(ctx) {
                    MatchEditorScreen(state, navigation, mobileSession)
                }
            },
        AppRoute.RETIREMENT_LIMITS to
            @Composable { ctx ->
                with(ctx) {
                    PlanLimitsScreen(state, navigation, mobileSession)
                }
            },
    )

/** This feature's entries in the full-screen menu. */
internal val retirementMenu: List<MenuEntry> =
    listOf(
        MenuEntry(200) { ctx ->
            with(ctx) {
                AppMenuItem(Lucide.PiggyBank, "Retirement & savings", "401(k), IRA, HSA and more") { open(AppRoute.RETIREMENT) }
            }
        },
    )
