package app.tandemforactual.ui.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * One row of the shortcut table as the platform lists see it: [key] is a single character or a key name such as
 * "Enter", [keyCode] the Compose key code, and [menu] the iPad menu bar menu it belongs in, or empty for none.
 */
data class KeyboardCommand(
    val commandId: String,
    val title: String,
    val key: String,
    val keyCode: Long,
    val command: Boolean,
    val shift: Boolean,
    val group: String,
    val menu: String,
    val enabled: Boolean,
)

/**
 * The shortcuts the shell offers right now, for the platform lists: Android's keyboard shortcuts helper and the iPad
 * menu bar. A menu item that the platform handles before Compose sees the key sends it back through [perform].
 */
object KeyboardCommands {
    const val MENU_FILE = "file"
    const val MENU_GO = "go"
    const val MENU_HELP = "help"

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
