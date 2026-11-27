package app.tandemforactual.ui.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** One app shortcut: ⌘ (Ctrl on Android) plus [key], a single character. */
data class KeyboardCommand(
    val commandId: String,
    val title: String,
    val key: String,
    val enabled: Boolean,
) {
    companion object {
        const val NEW_TRANSACTION = "new-transaction"
        const val SEARCH = "search"
        const val SYNC = "sync"
        const val BACK = "back"
        const val SETTINGS = "settings"
        const val TAB_PREFIX = "tab-"
    }
}

/**
 * The shortcuts the shell offers right now, for the platform lists: Android's keyboard shortcuts helper and the iPad
 * menu bar. A menu item that the platform handles before Compose sees the key sends it back through [perform].
 */
object KeyboardCommands {
    var current: List<KeyboardCommand> = emptyList()
        private set
    private var listener: ((List<KeyboardCommand>) -> Unit)? = null
    private val mutablePerformed = MutableSharedFlow<String>(extraBufferCapacity = 8)

    internal val performed: SharedFlow<String> = mutablePerformed

    fun observe(onChanged: (List<KeyboardCommand>) -> Unit) {
        listener = onChanged
        onChanged(current)
    }

    fun perform(id: String) {
        mutablePerformed.tryEmit(id)
    }

    internal fun publish(commands: List<KeyboardCommand>) {
        if (commands == current) return
        current = commands
        listener?.invoke(commands)
    }
}
