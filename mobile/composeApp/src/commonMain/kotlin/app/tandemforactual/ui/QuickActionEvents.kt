package app.tandemforactual.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Home-screen shortcuts ask for a new transaction here; the app opens it once a budget is ready. */
object QuickActionEvents {
    private val mutableAddTransaction = MutableStateFlow(false)

    val addTransactionRequested: StateFlow<Boolean> = mutableAddTransaction

    fun requestAddTransaction() {
        mutableAddTransaction.value = true
    }

    internal fun consumeAddTransaction(): Boolean {
        val requested = mutableAddTransaction.value
        mutableAddTransaction.value = false
        return requested
    }
}
