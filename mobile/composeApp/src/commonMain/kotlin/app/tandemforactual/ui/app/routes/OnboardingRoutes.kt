package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.onboarding.BudgetChooserScreen
import app.tandemforactual.ui.feature.onboarding.BudgetUnlockScreen
import app.tandemforactual.ui.feature.onboarding.FirstSyncScreen
import app.tandemforactual.ui.feature.onboarding.ServerAddressScreen
import app.tandemforactual.ui.feature.onboarding.SetupCompleteScreen
import app.tandemforactual.ui.feature.onboarding.SignInScreen
import app.tandemforactual.ui.feature.onboarding.StartupScreen
import app.tandemforactual.ui.feature.onboarding.SwitchBudgetScreen
import app.tandemforactual.ui.feature.onboarding.WelcomeScreen

/** Screens for the onboarding routes. */
internal val onboardingScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.STARTUP to
            @Composable { ctx ->
                with(ctx) {
                    StartupScreen()
                }
            },
        AppRoute.WELCOME to
            @Composable { ctx ->
                with(ctx) {
                    WelcomeScreen(navigation, mobileSession.signOutNotice) {
                        uriHandler.openUri("https://github.com/Nischay-Pro/tandem#getting-started")
                    }
                }
            },
        AppRoute.SERVER_ADDRESS to
            @Composable { ctx ->
                with(ctx) {
                    ServerAddressScreen(state, navigation)
                }
            },
        AppRoute.SIGN_IN to
            @Composable { ctx ->
                with(ctx) {
                    SignInScreen(state, mobileSession, navigation, onSignIn, onStartOpenIdSignIn)
                }
            },
        AppRoute.BUDGET_CHOOSER to
            @Composable { ctx ->
                with(ctx) {
                    BudgetChooserScreen(state, mobileSession, navigation, onRefreshBudgets, onPrepareBudgetUnlock, onOpenSelectedBudget)
                }
            },
        AppRoute.SWITCH_BUDGET to
            @Composable { ctx ->
                with(ctx) {
                    SwitchBudgetScreen(state, mobileSession, navigation, onRefreshBudgets, onPrepareBudgetUnlock, onOpenSelectedBudget)
                }
            },
        AppRoute.BUDGET_UNLOCK to
            @Composable { ctx ->
                with(ctx) {
                    BudgetUnlockScreen(state, navigation, onOpenSelectedBudget)
                }
            },
        AppRoute.FIRST_SYNC to
            @Composable { ctx ->
                with(ctx) {
                    FirstSyncScreen(
                        state = state,
                        mobileSession = mobileSession,
                        navigation = navigation,
                        onRetrySync = onRetrySync,
                    )
                }
            },
        AppRoute.SETUP_COMPLETE to
            @Composable { ctx ->
                with(ctx) {
                    SetupCompleteScreen(state, navigation)
                }
            },
    )
