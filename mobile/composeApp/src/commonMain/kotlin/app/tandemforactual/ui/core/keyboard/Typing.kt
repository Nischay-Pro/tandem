@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput

/** Whether a text field inside [TrackTyping] is taking keyboard input right now. */
internal class TypingState {
    private val sessions = mutableIntStateOf(0)

    val active: Boolean get() = sessions.intValue > 0

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun TrackTyping(content: @Composable () -> Unit) {
        InterceptPlatformTextInput(
            interceptor = { request, next ->
                sessions.intValue++
                try {
                    next.startInputMethod(request)
                } finally {
                    sessions.intValue--
                }
            },
            content = content,
        )
    }
}

/** How the command modifier is written on this platform: ⌘ on iPad, Ctrl on Android. */
internal expect val commandKeyLabel: String
