package app.tandemforactual.ui.feature.banking

import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.ui.core.state.LiveAccountSummary

/** A budget account the bank account could link into, and how closely their names match from 0 to about 2. */
internal data class LinkCandidate(
    val account: LiveAccountSummary,
    val score: Double,
)

private const val BEST_MATCH_SCORE = 0.5
private val SYNONYMS =
    mapOf(
        "chequing" to "checking",
        "chk" to "checking",
        "sav" to "savings",
        "saving" to "savings",
        "cc" to "card",
        "credit" to "card",
        "visa" to "card",
        "mastercard" to "card",
        "amex" to "card",
        "mc" to "card",
    )
private val FILLER = setOf("account", "acct", "the", "my", "and", "of", "bank")

/** Budget accounts best first, as Actual users name them after the bank, the product or the card's last digits. */
internal fun rankLinkCandidates(
    bankAccount: BankProviderAccount,
    accounts: List<LiveAccountSummary>,
): List<LinkCandidate> =
    accounts
        .map { LinkCandidate(it, linkMatchScore(bankAccount.name, bankAccount.institution, it.name)) }
        .sortedWith(compareByDescending<LinkCandidate> { it.score }.thenBy { it.account.name.lowercase() })

internal fun isBestMatch(
    candidates: List<LinkCandidate>,
    candidate: LinkCandidate,
): Boolean = candidate === candidates.firstOrNull() && candidate.score >= BEST_MATCH_SCORE

internal fun linkMatchScore(
    bankName: String,
    institution: String?,
    accountName: String,
): Double {
    val bankTokens = tokens("$bankName ${institution.orEmpty()}")
    val accountTokens = tokens(accountName)
    if (accountTokens.isEmpty()) return 0.0
    val matched = accountTokens.count { token -> bankTokens.any { similar(it, token) } }
    val overlap = matched.toDouble() / accountTokens.size
    val whole = dice(normalize(bankName), normalize(accountName))
    val digits = lastDigits(bankName)?.let { if (it in accountName) 1.0 else 0.0 } ?: 0.0
    return maxOf(overlap, whole) + digits
}

private fun normalize(text: String) = text.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

private fun tokens(text: String): Set<String> =
    normalize(text)
        .split(' ')
        .filter { it.isNotEmpty() && it !in FILLER }
        .map { SYNONYMS[it] ?: it }
        .toSet()

private fun similar(
    a: String,
    b: String,
) = a == b ||
    (a.length >= 4 && b.length >= 4 && (a.startsWith(b) || b.startsWith(a) || edits(a, b) <= if (minOf(a.length, b.length) >= 7) 2 else 1))

private fun lastDigits(text: String): String? = Regex("\\d{4}").findAll(text).lastOrNull()?.value

/** Sørensen–Dice similarity of character pairs, which tolerates typos and reordered words. */
private fun dice(
    a: String,
    b: String,
): Double {
    val left = a.windowed(2)
    val right = b.windowed(2).toMutableList()
    if (left.isEmpty() || right.isEmpty()) return if (a == b) 1.0 else 0.0
    val total = left.size + right.size
    val shared = left.count { right.remove(it) }
    return 2.0 * shared / total
}

/** Edits between two words, counting a swap of neighbouring letters as one, so "Sapphrie" still finds "Sapphire". */
private fun edits(
    a: String,
    b: String,
): Int {
    val d =
        Array(a.length + 1) { i ->
            IntArray(b.length + 1) { j ->
                if (i == 0) {
                    j
                } else if (j == 0) {
                    i
                } else {
                    0
                }
            }
        }
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
    }
    return d[a.length][b.length]
}
