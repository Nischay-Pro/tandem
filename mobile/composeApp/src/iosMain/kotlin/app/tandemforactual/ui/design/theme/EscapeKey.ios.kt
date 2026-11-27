package app.tandemforactual.ui.design.theme

internal actual val escapeClosesSheetsInCompose: Boolean = true

internal actual fun dispatchEscapeAsBack(): Boolean = IosBackHandlers.dispatch()

/** The composed [TandemBackHandler]s, newest last, so Esc on a hardware keyboard can go back like Android's back key. */
internal object IosBackHandlers {
    class Entry(
        var enabled: Boolean,
        var onBack: () -> Unit,
    )

    private val entries = mutableListOf<Entry>()

    fun add(entry: Entry) {
        entries += entry
    }

    fun remove(entry: Entry) {
        entries -= entry
    }

    fun dispatch(): Boolean {
        val entry = entries.lastOrNull { it.enabled } ?: return false
        entry.onBack()
        return true
    }
}
