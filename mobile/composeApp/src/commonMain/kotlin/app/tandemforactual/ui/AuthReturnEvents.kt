package app.tandemforactual.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Receives the short-lived return URI from the platform after browser sign-in. */
object AuthReturnEvents {
    private val mutableUri = MutableStateFlow<String?>(null)

    val uri: StateFlow<String?> = mutableUri

    fun publish(uri: String) {
        mutableUri.value = uri
    }

    fun clear(uri: String) {
        if (mutableUri.value == uri) mutableUri.value = null
    }
}
