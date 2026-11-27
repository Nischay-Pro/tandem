@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.sheets

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

internal enum class FormKey { SAVE, ENTER, ESCAPE }

/** ⌘S or ⌘Enter (Ctrl elsewhere) saves from anywhere; a bare Enter or Esc only when nothing focused handled it. */
internal fun formKey(
    key: Key,
    command: Boolean,
    otherModifier: Boolean,
): FormKey? =
    when {
        command && (key == Key.S || key == Key.Enter || key == Key.NumPadEnter) -> FormKey.SAVE
        command || otherModifier -> null
        key == Key.Enter || key == Key.NumPadEnter -> FormKey.ENTER
        key == Key.Escape -> FormKey.ESCAPE
        else -> null
    }

private fun KeyEvent.formKey(): FormKey? =
    if (type == KeyEventType.KeyDown) formKey(key, isMetaPressed || isCtrlPressed, isAltPressed || isShiftPressed) else null

/** The primary actions on screen in one sheet; the keyboard saves only when exactly one is showing. */
internal class FormSubmitActions {
    internal class Action(
        var enabled: Boolean,
        var onSubmit: () -> Unit,
    )

    private val actions = mutableStateListOf<Action>()

    fun add(action: Action) {
        actions += action
    }

    fun remove(action: Action) {
        actions -= action
    }

    fun submit(): Boolean {
        val action = actions.singleOrNull()?.takeIf { it.enabled } ?: return false
        action.onSubmit()
        return true
    }
}

internal val LocalFormSubmitActions = staticCompositionLocalOf<FormSubmitActions?> { null }

/** Lets the keyboard press this primary button: ⌘S, ⌘Enter, or Enter when no field handled it. */
@Composable
internal fun RegisterFormSubmit(
    enabled: Boolean,
    onSubmit: () -> Unit,
) {
    val actions = LocalFormSubmitActions.current
    val action = remember { FormSubmitActions.Action(enabled, onSubmit) }
    SideEffect {
        action.enabled = enabled
        action.onSubmit = onSubmit
    }
    DisposableEffect(actions, action) {
        actions?.add(action)
        onDispose { actions?.remove(action) }
    }
}

/**
 * Keyboard handling for a sheet: save shortcuts reach the sheet's single primary button, and [onEscape] closes it. The
 * sheet takes focus when nothing inside it has, so a hardware keyboard works before any field is touched.
 */
@Composable
internal fun FormShortcutHost(
    onEscape: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val actions = remember { FormSubmitActions() }
    val focusRequester = remember { FocusRequester() }
    val hasFocus = remember { mutableStateOf(false) }
    val currentOnEscape = rememberUpdatedState(onEscape)
    LaunchedEffect(focusRequester) {
        withFrameNanos {}
        if (!hasFocus.value) runCatching { focusRequester.requestFocus() }
    }
    CompositionLocalProvider(LocalFormSubmitActions provides actions) {
        Box(
            modifier
                .onPreviewKeyEvent { event -> event.formKey() == FormKey.SAVE && actions.submit() }
                .onKeyEvent { event ->
                    when (event.formKey()) {
                        FormKey.ENTER -> actions.submit()
                        FormKey.ESCAPE -> {
                            val close = currentOnEscape.value
                            close?.invoke()
                            close != null
                        }
                        else -> false
                    }
                }.onFocusChanged { hasFocus.value = it.hasFocus }
                .focusRequester(focusRequester)
                .focusable(),
        ) {
            content()
        }
    }
}
