package app.tandemforactual.data.planning

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What an account is. Actual no longer stores this, so Tandem keeps the person's choice as planning data. */
enum class AccountKind(
    val key: String,
    val label: String,
    val groupLabel: String,
    val owesMoney: Boolean,
) {
    BANK("bank", "Bank account", "Bank accounts", false),
    SAVINGS("savings", "Savings", "Savings", false),
    CREDIT_CARD("credit_card", "Credit card", "Credit cards", true),
    CASH("cash", "Cash", "Cash", false),
    INVESTMENT("investment", "Investment", "Investments", false),
    LOAN("loan", "Loan", "Loans", true),
    MORTGAGE("mortgage", "Mortgage", "Mortgages", true),
    OTHER("other", "Other", "Other", false),
    ;

    companion object {
        fun fromKey(key: String?): AccountKind? = entries.firstOrNull { it.key == key }
    }
}

/** The kind the person chose for one Actual account. */
data class AccountKindChoice(
    val accountId: String,
    val kind: AccountKind,
) {
    val id: String get() = id(accountId)

    fun toJson(): JsonObject = jsonOf("accountId" to JsonPrimitive(accountId), "kind" to JsonPrimitive(kind.key))

    companion object {
        const val TYPE = "planning.account_kind"

        fun id(accountId: String) = "kind:$accountId"

        fun fromJson(body: JsonObject): AccountKindChoice? {
            val accountId = body.text("accountId")?.takeIf { it.isNotBlank() } ?: return null
            val kind = AccountKind.fromKey(body.text("kind")) ?: return null
            return AccountKindChoice(accountId, kind)
        }
    }
}

/** An account's kind as shown: the person's choice, else a guess they haven't confirmed. */
data class ResolvedAccountKind(
    val kind: AccountKind,
    val chosen: Boolean,
)

fun resolveAccountKind(
    chosen: AccountKind?,
    name: String,
    officialName: String? = null,
    storedType: String? = null,
): ResolvedAccountKind =
    chosen?.let { ResolvedAccountKind(it, chosen = true) }
        ?: ResolvedAccountKind(guessAccountKind(name, officialName, storedType) ?: AccountKind.OTHER, chosen = false)

/**
 * A best guess at an account's kind from what Actual has: a type left by older versions or bank links, the bank's
 * own name for a linked account, then the name the person gave it. Null when nothing hints at one.
 */
fun guessAccountKind(
    name: String,
    officialName: String? = null,
    storedType: String? = null,
): AccountKind? = kindFromType(storedType) ?: kindFromName(officialName) ?: kindFromName(name)

private fun kindFromType(type: String?): AccountKind? =
    when (
        type
            ?.trim()
            ?.lowercase()
            ?.replace('-', ' ')
            ?.replace('_', ' ')
    ) {
        "checking", "chequing", "depository", "bank" -> AccountKind.BANK
        "savings", "saving" -> AccountKind.SAVINGS
        "credit", "credit card" -> AccountKind.CREDIT_CARD
        "cash" -> AccountKind.CASH
        "investment", "brokerage" -> AccountKind.INVESTMENT
        "loan", "debt" -> AccountKind.LOAN
        "mortgage" -> AccountKind.MORTGAGE
        else -> null
    }

private val NAME_HINTS: List<Pair<Set<String>, AccountKind>> =
    listOf(
        setOf("mortgage", "heloc") to AccountKind.MORTGAGE,
        setOf("loan", "loans", "debt", "lease") to AccountKind.LOAN,
        setOf("credit", "card", "visa", "mastercard", "amex") to AccountKind.CREDIT_CARD,
        setOf("savings", "saving", "isa", "deposit", "emergency") to AccountKind.SAVINGS,
        setOf("investment", "investments", "brokerage", "401k", "403b", "ira", "roth", "rrsp", "tfsa", "pension", "stocks", "retirement") to
            AccountKind.INVESTMENT,
        setOf("cash", "wallet") to AccountKind.CASH,
        setOf("checking", "chequing", "current", "everyday", "bank", "debit") to AccountKind.BANK,
    )

private fun kindFromName(name: String?): AccountKind? {
    val words =
        name
            .orEmpty()
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .toSet()
    return NAME_HINTS.firstOrNull { (hints, _) -> hints.any(words::contains) }?.second
}
