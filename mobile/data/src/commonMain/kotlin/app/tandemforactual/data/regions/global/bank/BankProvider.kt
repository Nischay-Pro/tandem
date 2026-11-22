package app.tandemforactual.data.regions.global.bank

import app.tandemforactual.data.actual.LinkedAccount
import app.tandemforactual.data.regions.Region
import kotlinx.serialization.json.JsonObject

/** How an account gets linked: the server already holds a key and can list accounts, or the bank needs a browser sign-in. */
enum class BankLinkMethod { LIST_ACCOUNTS, BROWSER }

/** An account at the bank provider that can be linked to one in the budget. */
data class BankProviderAccount(
    val id: String,
    val name: String,
    val institution: String?,
    val bankId: String,
    val balance: String?,
)

/**
 * One bank-sync provider that Actual's server proxies. To add a provider, create a folder under its region
 * (`regions/<region>/bank/<provider>`), extend this class there, and list it in [BankProviders].
 */
abstract class BankProvider {
    /** The `account_sync_source` Actual stores on linked accounts. */
    abstract val source: String

    /** The Actual server route prefix, as in `{server}/{serverPath}/transactions`. */
    abstract val serverPath: String
    abstract val displayName: String
    abstract val regions: Set<Region>
    abstract val linkMethod: BankLinkMethod

    /** True when the bank reports the current balance, so the first sync starts from it instead of working it out. */
    open val startsFromCurrentBalance: Boolean = false

    /** Every provider uses the same three Actual server routes: whether it's set up, its accounts, and an account's transactions. */
    val statusRoute get() = "$serverPath/status"
    val accountsRoute get() = "$serverPath/accounts"
    val transactionsRoute get() = "$serverPath/transactions"

    abstract fun transactionsRequest(account: LinkedAccount): JsonObject

    /** Reads the server's account list response; only called for [BankLinkMethod.LIST_ACCOUNTS] providers. */
    open fun parseAccounts(data: JsonObject): List<BankProviderAccount> = emptyList()
}
