package app.tandemforactual.ui.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Features that launcher shortcuts, assistants and automation apps can open directly. */
enum class QuickAction(
    val slug: String,
) {
    ADD_TRANSACTION("add-transaction"),
    SCAN_RECEIPT("scan-receipt"),
    BUDGET_CHECKUP("budget-checkup"),
    ACCOUNTS("accounts"),
    ;

    val link: String get() = "$QUICK_ACTION_SCHEME://$QUICK_ACTION_HOST/$slug"

    companion object {
        fun fromSlug(slug: String?): QuickAction? {
            val normalized =
                slug
                    ?.trim()
                    ?.lowercase()
                    ?.replace(' ', '-')
                    ?.replace('_', '-') ?: return null
            return entries.firstOrNull { it.slug == normalized } ?: FEATURE_ALIASES[normalized]
        }

        /** Reads links shaped like `app.tandemforactual://open/scan-receipt`. */
        fun fromLink(link: String?): QuickAction? {
            val prefix = "$QUICK_ACTION_SCHEME://$QUICK_ACTION_HOST/"
            if (link == null || !link.startsWith(prefix, ignoreCase = true)) return null
            val slug =
                link
                    .substring(prefix.length)
                    .substringBefore('?')
                    .substringBefore('#')
                    .trimEnd('/')
            return fromSlug(slug)
        }
    }
}

private const val QUICK_ACTION_SCHEME = "app.tandemforactual"
private const val QUICK_ACTION_HOST = "open"

private val FEATURE_ALIASES =
    mapOf(
        "transaction" to QuickAction.ADD_TRANSACTION,
        "new-transaction" to QuickAction.ADD_TRANSACTION,
        "receipt" to QuickAction.SCAN_RECEIPT,
        "checkup" to QuickAction.BUDGET_CHECKUP,
        "account" to QuickAction.ACCOUNTS,
    )

/** Shortcuts ask for a feature here; the app opens it once a budget is ready, behind the app lock. */
object QuickActionEvents {
    private val mutableAddTransaction = MutableStateFlow(false)
    private val mutableDestination = MutableStateFlow<QuickAction?>(null)

    val addTransactionRequested: StateFlow<Boolean> = mutableAddTransaction

    internal val destinationRequested: StateFlow<QuickAction?> = mutableDestination

    private var sharedText: String? = null
    private var receiptScan = false

    fun requestAddTransaction() {
        receiptScan = false
        mutableAddTransaction.value = true
    }

    fun open(action: QuickAction) {
        when (action) {
            QuickAction.ADD_TRANSACTION -> requestAddTransaction()
            QuickAction.SCAN_RECEIPT -> {
                receiptScan = true
                mutableAddTransaction.value = true
            }
            QuickAction.BUDGET_CHECKUP, QuickAction.ACCOUNTS -> mutableDestination.value = action
        }
    }

    /** Opens the feature a deep link names, returning false for links that aren’t feature links. */
    fun openLink(link: String): Boolean {
        val action = QuickAction.fromLink(link) ?: return false
        open(action)
        return true
    }

    /** Opens a new transaction prefilled from text shared into Tandem, such as a bank alert. */
    fun shareText(text: String) {
        sharedText = text
        receiptScan = false
        mutableAddTransaction.value = true
    }

    internal fun consumeSharedText(): String? = sharedText.also { sharedText = null }

    internal fun consumeReceiptScan(): Boolean = receiptScan.also { receiptScan = false }

    internal fun consumeAddTransaction(): Boolean {
        val requested = mutableAddTransaction.value
        mutableAddTransaction.value = false
        return requested
    }

    internal fun consumeDestination(): QuickAction? = mutableDestination.value.also { mutableDestination.value = null }
}
