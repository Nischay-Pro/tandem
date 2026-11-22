@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.ui.core.format.AppLanguageController
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.theme.SystemReduceMotionEnabled
import app.tandemforactual.ui.design.theme.TandemTheme

/** The app shell: shell state, the start-up, session and sync effects, then the themed chrome and overlays. */
@Composable
internal fun AppShell(env: ShellEnvironment) {
    val shell = rememberAppShellState(env.nowEpochMillis)
    val systemReduceMotion = key(shell.accessibilitySettingsRevision) { SystemReduceMotionEnabled() }
    val state = rememberSaveable(saver = AppState.Saver) { AppState() }
    val saveableStateHolder = rememberSaveableStateHolder()
    val budgetScoped = remember(state.selectedBudgetId) { BudgetScopedShellState() }
    SideEffect { state.systemReduceMotionEnabled = systemReduceMotion }
    val reduceMotion = state.reduceMotionOverride ?: systemReduceMotion
    val mobileSession = rememberShellSession(env)
    val appPreferencesStore = remember(env.database) { env.database?.let(::AppPreferencesStore) }
    SessionQueueEffects(env, state, mobileSession, appPreferencesStore)
    val navigator = ShellNavigator(shell, state, rememberCoroutineScope(), reduceMotion)
    StartupEffects(env, state, shell, navigator, mobileSession)
    AppSettingsEffects(env, state, mobileSession, appPreferencesStore)
    val ctx =
        ShellContext(
            env,
            state,
            shell,
            budgetScoped,
            mobileSession,
            navigator,
            saveableStateHolder,
            LocalUriHandler.current,
            LocalHapticFeedback.current,
        )
    NavigationEffects(ctx)
    RouteEntryEffects(ctx)
    val automaticSyncRequest = rememberAutomaticSync(ctx)
    BudgetSessionEffects(ctx)
    RouteSyncEffect(ctx, automaticSyncRequest)
    val isAppInForeground by env.appInForeground.collectAsState()
    ForegroundEffects(ctx, isAppInForeground)
    PeriodicSyncEffects(ctx, isAppInForeground)
    LiveDataEffects(ctx)
    LiveBudgetMonthEffect(ctx)
    OpenIdReturnEffect(state, mobileSession, ctx.navigation)
    env.screenshotScript?.let { ScreenshotScriptEffect(ctx, it) }
    TandemTheme(
        state.theme,
        state.accent,
        reduceMotion,
        animateThemeChanges = state.settingsStorageReady || appPreferencesStore == null,
        language = AppLanguageController(state.appLanguage) { state.appLanguage = it },
        interfaceMode = state.interfaceMode,
    ) {
        ShellFrame(ctx, isAppInForeground)
    }
}
