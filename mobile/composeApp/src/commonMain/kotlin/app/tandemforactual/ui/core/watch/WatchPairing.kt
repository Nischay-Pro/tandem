package app.tandemforactual.ui.core.watch

import app.tandemforactual.ui.core.platform.WatchLink
import kotlinx.coroutines.flow.MutableStateFlow

/** A paired Wear OS watch. Android sends it summaries itself through [refresh], keeping the unlock rules, so the shared one is ignored. */
object WatchPairing : WatchLink {
    /** Whether a paired watch has Tandem installed, so Settings offers the Watch page. */
    val available = MutableStateFlow(false)

    override val paired = available

    /** Asks the platform to send the watch a fresh summary, such as after a watch setting changed. */
    var refresh: () -> Unit = {}

    override fun sendSummary(json: String) = Unit
}
