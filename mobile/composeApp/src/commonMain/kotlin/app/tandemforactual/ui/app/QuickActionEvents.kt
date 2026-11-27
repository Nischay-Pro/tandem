package app.tandemforactual.ui.app

import app.tandemforactual.ui.core.platform.PickedStatementFile
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
    REVIEW_TRANSACTIONS("review-transactions"),
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
    private var sharedReceiptPhoto: String? = null
    private var sharedStatement: PickedStatementFile? = null
    private var receiptScan = false
    private var quickAddDraft: QuickAddDraft? = null

    fun requestAddTransaction() {
        receiptScan = false
        quickAddDraft = null
        mutableAddTransaction.value = true
    }

    fun open(action: QuickAction) {
        when (action) {
            QuickAction.ADD_TRANSACTION -> requestAddTransaction()
            QuickAction.SCAN_RECEIPT -> {
                receiptScan = true
                quickAddDraft = null
                mutableAddTransaction.value = true
            }
            QuickAction.BUDGET_CHECKUP, QuickAction.ACCOUNTS, QuickAction.REVIEW_TRANSACTIONS -> mutableDestination.value = action
        }
    }

    /** Opens the feature a deep link names, returning false for links that aren’t feature links. */
    fun openLink(link: String): Boolean {
        val action = QuickAction.fromLink(link) ?: return false
        open(action)
        if (action == QuickAction.ADD_TRANSACTION) quickAddDraft = QuickAddDraft.fromLink(link)
        return true
    }

    /** Opens a new transaction prefilled from text shared into Tandem, such as a bank alert. */
    fun shareText(text: String) {
        sharedText = text
        receiptScan = false
        quickAddDraft = null
        mutableAddTransaction.value = true
    }

    /** Opens a new transaction and reads the receipt photo shared into Tandem. */
    fun shareReceiptPhoto(reference: String) {
        sharedReceiptPhoto = reference
        receiptScan = false
        quickAddDraft = null
        mutableAddTransaction.value = true
    }

    /** Opens Accounts so the person can choose where the shared statement goes. */
    internal fun shareStatement(file: PickedStatementFile) {
        sharedStatement = file
        mutableDestination.value = QuickAction.ACCOUNTS
    }

    internal fun consumeSharedText(): String? = sharedText.also { sharedText = null }

    internal fun consumeSharedReceiptPhoto(): String? = sharedReceiptPhoto.also { sharedReceiptPhoto = null }

    internal fun consumeSharedStatement(): PickedStatementFile? = sharedStatement.also { sharedStatement = null }

    internal fun consumeQuickAddDraft(): QuickAddDraft? = quickAddDraft.also { quickAddDraft = null }

    internal fun consumeReceiptScan(): Boolean = receiptScan.also { receiptScan = false }

    internal fun consumeAddTransaction(): Boolean {
        val requested = mutableAddTransaction.value
        mutableAddTransaction.value = false
        return requested
    }

    internal fun consumeDestination(): QuickAction? = mutableDestination.value.also { mutableDestination.value = null }
}
