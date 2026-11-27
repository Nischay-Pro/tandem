@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import app.tandemforactual.ui.app.KeyboardCommands
import app.tandemforactual.ui.app.KeyboardShortcut
import app.tandemforactual.ui.app.QuickActionEvents
import app.tandemforactual.ui.app.keyboardCommands
import app.tandemforactual.ui.app.keyboardShortcut
import app.tandemforactual.ui.app.navigationTabs
import app.tandemforactual.ui.app.shortcut
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.design.theme.dispatchEscapeAsBack

/** App shortcuts while a budget's tabs are showing, and Esc as back where the platform doesn't do that itself. */
internal fun Modifier.shellKeyboard(
    ctx: ShellContext,
    enabled: Boolean,
    tabs: List<AppRoute>,
): Modifier =
    onPreviewKeyEvent { event ->
        val shortcut = event.keyboardShortcut(tabs)?.takeIf { enabled } ?: return@onPreviewKeyEvent false
        ctx.runShortcut(shortcut)
        true
    }.onKeyEvent { event ->
        event.type == KeyEventType.KeyDown &&
            event.key == Key.Escape &&
            !(event.isMetaPressed || event.isCtrlPressed || event.isAltPressed || event.isShiftPressed) &&
            dispatchEscapeAsBack()
    }

internal fun ShellContext.runShortcut(shortcut: KeyboardShortcut) {
    state.menuOpen = false
    when (shortcut) {
        KeyboardShortcut.NewTransaction -> QuickActionEvents.requestAddTransaction()
        KeyboardShortcut.Search -> navigation.open(AppRoute.ACTIVITY_SEARCH)
        KeyboardShortcut.Sync -> budgetActions.syncNow()
        KeyboardShortcut.Back -> navigation.back()
        KeyboardShortcut.Settings -> navigation.selectTab(AppRoute.SETTINGS)
        is KeyboardShortcut.Tab -> navigation.selectTab(shortcut.route)
    }
}

/** Publishes the shortcut list for the platform helpers and runs the ones a platform menu sends back. */
@Composable
internal fun ShellKeyboardCommands(
    ctx: ShellContext,
    enabled: Boolean,
) {
    val tabs = navigationTabs(ctx.state.interfaceMode)
    val commands = keyboardCommands(tabs, enabled)
    SideEffect { KeyboardCommands.publish(commands) }
    val currentCtx by rememberUpdatedState(ctx)
    val currentEnabled by rememberUpdatedState(enabled && !ctx.transactionSheetActive)
    val currentTabs by rememberUpdatedState(tabs.map { it.first })
    LaunchedEffect(Unit) {
        KeyboardCommands.performed.collect { id ->
            val command = KeyboardCommands.current.firstOrNull { it.commandId == id }
            val shortcut = command?.shortcut(currentTabs)
            if (currentEnabled && shortcut != null) currentCtx.runShortcut(shortcut)
        }
    }
}
