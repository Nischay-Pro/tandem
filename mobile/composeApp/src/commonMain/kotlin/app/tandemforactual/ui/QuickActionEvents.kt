package app.tandemforactual.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Home-screen shortcuts ask for a new transaction here; the app opens it once a budget is ready. */
object QuickActionEvents {
    private val mutableAddTransaction = MutableStateFlow(false)

    val addTransactionRequested: StateFlow<Boolean> = mutableAddTransaction

    private var sharedText: String? = null

    fun requestAddTransaction() {
        mutableAddTransaction.value = true
    }

    /** Opens a new transaction prefilled from text shared into Tandem, such as a bank alert. */
    fun shareText(text: String) {
        sharedText = text
        mutableAddTransaction.value = true
    }

    internal fun consumeSharedText(): String? = sharedText.also { sharedText = null }

    internal fun consumeAddTransaction(): Boolean {
        val requested = mutableAddTransaction.value
        mutableAddTransaction.value = false
        return requested
    }
}
