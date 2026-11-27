@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import app.tandemforactual.ui.app.KeyboardCommands
import app.tandemforactual.ui.app.QuickActionEvents
import app.tandemforactual.ui.app.keyboardCommands
import app.tandemforactual.ui.core.keyboard.Shortcut
import app.tandemforactual.ui.core.keyboard.ShortcutAction
import app.tandemforactual.ui.core.keyboard.ShortcutScope
import app.tandemforactual.ui.core.keyboard.TypingState
import app.tandemforactual.ui.core.keyboard.keyPress
import app.tandemforactual.ui.core.keyboard.match
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.design.theme.dispatchEscapeAsBack

/** The shell's rows of the shortcut table: global ones before the focused control, list ones only when nobody is typing. */
internal fun Modifier.shellKeyboard(
    ctx: ShellContext,
    enabled: Boolean,
    shortcuts: List<Shortcut>,
    tabs: List<AppRoute>,
    typing: TypingState,
): Modifier =
    onPreviewKeyEvent { event ->
        val shortcut = shortcuts.match(event.keyPress(), ShortcutScope.GLOBAL)
        enabled && shortcut != null && ctx.runShortcut(shortcut, tabs)
    }.onKeyEvent { event ->
        val shortcut = shortcuts.match(event.keyPress(), ShortcutScope.LIST)
        shortcut != null &&
            !typing.active &&
            (enabled || shortcut.action == ShortcutAction.ESCAPE_BACK) &&
            ctx.runShortcut(shortcut, tabs)
    }

internal fun ShellContext.runShortcut(
    shortcut: Shortcut,
    tabs: List<AppRoute>,
): Boolean {
    if (shortcut.action == ShortcutAction.ESCAPE_BACK) return dispatchEscapeAsBack()
    state.menuOpen = false
    when (shortcut.action) {
        ShortcutAction.NEW_TRANSACTION -> QuickActionEvents.requestAddTransaction()
        ShortcutAction.SEARCH -> navigation.open(AppRoute.ACTIVITY_SEARCH)
        ShortcutAction.SYNC -> budgetActions.syncNow()
        ShortcutAction.BACK -> navigation.back()
        ShortcutAction.SETTINGS -> navigation.selectTab(AppRoute.SETTINGS)
        ShortcutAction.TAB -> navigation.selectTab(shortcut.tab?.let { tabs.getOrNull(it) } ?: return false)
        ShortcutAction.SHORTCUTS -> shell.showShortcuts = true
        else -> return false
    }
    return true
}

/** Publishes the table for the platform lists and runs the rows a platform menu sends back. */
@Composable
internal fun ShellKeyboardCommands(
    ctx: ShellContext,
    enabled: Boolean,
    shortcuts: List<Shortcut>,
    tabs: List<AppRoute>,
) {
    val commands = keyboardCommands(shortcuts, enabled)
    SideEffect { KeyboardCommands.publish(commands) }
    val currentCtx by rememberUpdatedState(ctx)
    val currentEnabled by rememberUpdatedState(enabled && !ctx.transactionSheetActive)
    val currentShortcuts by rememberUpdatedState(shortcuts)
    val currentTabs by rememberUpdatedState(tabs)
    LaunchedEffect(Unit) {
        KeyboardCommands.performed.collect { id ->
            val shortcut = currentShortcuts.firstOrNull { it.id == id }
            if (currentEnabled && shortcut != null) currentCtx.runShortcut(shortcut, currentTabs)
        }
    }
}
