package app.tandemforactual.ui.core.importing

import app.tandemforactual.api.client.StatementLedgerTransaction
import app.tandemforactual.ui.core.format.parseIsoDate

internal enum class StatementRowStatus {
    MATCHED,
    CHOOSE,
    GROUP,
    NEW,
    DUPLICATE,
    IMPORTED,
}

internal data class StatementCandidate(
    val transaction: StatementLedgerTransaction,
    val days: Int,
    val sharedWords: Int,
)

internal sealed interface StatementNoMatchReason {
    data class OppositeSign(
        val transaction: StatementLedgerTransaction,
    ) : StatementNoMatchReason

    data class TooFarApart(
        val transaction: StatementLedgerTransaction,
        val days: Int,
    ) : StatementNoMatchReason

    data class AlreadyImported(
        val transaction: StatementLedgerTransaction,
    ) : StatementNoMatchReason

    data object NothingNearby : StatementNoMatchReason

    data object NoSameAmount : StatementNoMatchReason
}

internal data class StatementRowReview(
    val index: Int,
    val row: StatementImportDraft,
    val status: StatementRowStatus,
    val candidates: List<StatementCandidate> = emptyList(),
    val existing: StatementLedgerTransaction? = null,
    val group: Int? = null,
    val duplicateOf: Int? = null,
    val provenDuplicate: Boolean = false,
    val reason: StatementNoMatchReason? = null,
)

internal data class StatementReview(
    val rows: List<StatementRowReview>,
    val repeatedStatement: Boolean,
    val spendingLooksPositive: Boolean,
) {
    val alreadyImported: Int get() = rows.count { it.status == StatementRowStatus.IMPORTED }
}

internal const val MATCH_WINDOW_DAYS = 7
internal const val EXPLAIN_WINDOW_DAYS = 31

/** The dates to read from the account so every row's matches and its explanation can be found. */
internal fun statementLedgerRange(rows: List<StatementImportDraft>): Pair<String, String>? {
    val days = rows.mapNotNull { isoDayNumber(it.date) }
    if (days.isEmpty()) return null
    return isoDate(days.min() - EXPLAIN_WINDOW_DAYS) to isoDate(days.max() + EXPLAIN_WINDOW_DAYS)
}

/**
 * Pairs statement rows with the account's transactions. Only the exact signed amount within [MATCH_WINDOW_DAYS] makes a
 * candidate; a row is matched on its own only when it has one candidate that no other row could take. Shared words
 * rank candidates but never create one.
 */
internal fun reviewStatement(
    rows: List<StatementImportDraft>,
    ledger: List<StatementLedgerTransaction>,
): StatementReview {
    val existing = alreadyImported(rows, ledger)
    val claimed = existing.values.map { it.id }.toSet()
    val duplicates = statementDuplicates(rows)
    val words = rows.map { meaningfulWords("${it.payeeName} ${it.notes.orEmpty()}") }
    val candidates =
        rows.mapIndexed { index, row ->
            if (index in existing || duplicates[index]?.second == true) {
                emptyList()
            } else {
                candidatesFor(row, words[index], ledger.filter { it.id !in claimed })
            }
        }
    val claims = candidates.flatten().groupingBy { it.transaction.id }.eachCount()
    val groups = sameDayGroups(rows, words, candidates)
    val reviewed =
        rows.mapIndexed { index, row ->
            val own = candidates[index]
            val duplicate = duplicates[index]
            when {
                index in existing -> StatementRowReview(index, row, StatementRowStatus.IMPORTED, existing = existing[index])
                duplicate?.second == true ->
                    StatementRowReview(index, row, StatementRowStatus.DUPLICATE, duplicateOf = duplicate.first, provenDuplicate = true)
                own.size == 1 && claims[own.single().transaction.id] == 1 ->
                    StatementRowReview(index, row, StatementRowStatus.MATCHED, own, duplicateOf = duplicate?.first)
                own.isNotEmpty() ->
                    StatementRowReview(
                        index,
                        row,
                        if (groups[index] != null) StatementRowStatus.GROUP else StatementRowStatus.CHOOSE,
                        own,
                        group = groups[index],
                        duplicateOf = duplicate?.first,
                    )
                duplicate != null -> StatementRowReview(index, row, StatementRowStatus.DUPLICATE, duplicateOf = duplicate.first)
                else -> StatementRowReview(index, row, StatementRowStatus.NEW, reason = noMatchReason(row, ledger, claimed))
            }
        }
    val unmatched = reviewed.filter { it.status == StatementRowStatus.NEW }
    val oppositeSign = unmatched.count { it.reason is StatementNoMatchReason.OppositeSign }
    return StatementReview(
        rows = reviewed,
        repeatedStatement = existing.isNotEmpty() && existing.size * 5 >= rows.size * 4,
        spendingLooksPositive = oppositeSign >= minOf(2, rows.size) && oppositeSign * 2 >= unmatched.size,
    )
}

/** Rows Actual already has: its `imported_id` is the row's own id, or the bank id an earlier import stored there. */
private fun alreadyImported(
    rows: List<StatementImportDraft>,
    ledger: List<StatementLedgerTransaction>,
): Map<Int, StatementLedgerTransaction> {
    val byImportedId = ledger.filter { it.importedId != null }.associateBy { it.importedId!! }
    val taken = mutableSetOf<String>()
    return buildMap {
        rows.forEachIndexed { index, row ->
            val match = byImportedId[row.importedId] ?: row.bankId?.let(byImportedId::get)
            if (match != null && taken.add(match.id)) put(index, match)
        }
    }
}

/** For each row that repeats an earlier one, that row and whether the bank's own id proves it. */
private fun statementDuplicates(rows: List<StatementImportDraft>): Map<Int, Pair<Int, Boolean>> =
    buildMap {
        rows.forEachIndexed { index, row ->
            val earlier =
                (0 until index).firstOrNull { other ->
                    val first = rows[other]
                    first.date == row.date &&
                        first.amountMinor == row.amountMinor &&
                        if (first.bankId != null && row.bankId != null) {
                            first.bankId == row.bankId
                        } else {
                            normalizedText(first) == normalizedText(row)
                        }
                } ?: return@forEachIndexed
            put(index, earlier to (row.bankId != null && rows[earlier].bankId == row.bankId))
        }
    }

private fun normalizedText(row: StatementImportDraft): String =
    "${row.payeeName} ${row.notes.orEmpty()}".lowercase().filter(Char::isLetterOrDigit)

private fun candidatesFor(
    row: StatementImportDraft,
    rowWords: Set<String>,
    ledger: List<StatementLedgerTransaction>,
): List<StatementCandidate> {
    val day = isoDayNumber(row.date) ?: return emptyList()
    return ledger
        .asSequence()
        .filter { it.amountMinor == row.amountMinor }
        .mapNotNull { transaction ->
            val days = (isoDayNumber(transaction.date) ?: return@mapNotNull null) - day
            if (kotlin.math.abs(days) > MATCH_WINDOW_DAYS) return@mapNotNull null
            StatementCandidate(transaction, days, sharedWordCount(rowWords, ledgerWords(transaction)))
        }.sortedWith(
            compareByDescending<StatementCandidate> { it.sharedWords }
                .thenBy { kotlin.math.abs(it.days) }
                .thenBy { if (it.transaction.importedId != null) 1 else 0 }
                .thenBy { it.transaction.id },
        ).toList()
}

/** Rows on one day from one merchant that compete for the same transactions, keyed to the group's first row. */
private fun sameDayGroups(
    rows: List<StatementImportDraft>,
    words: List<Set<String>>,
    candidates: List<List<StatementCandidate>>,
): Map<Int, Int> {
    val parent = IntArray(rows.size) { it }

    fun root(index: Int): Int {
        var current = index
        while (parent[current] != current) current = parent[current]
        return current
    }
    val linked = mutableSetOf<Int>()
    for (first in rows.indices) {
        val firstIds = candidates[first].map { it.transaction.id }.toSet()
        if (firstIds.isEmpty() || words[first].isEmpty()) continue
        for (second in first + 1 until rows.size) {
            val sameMerchant = rows[second].date == rows[first].date && words[second].any(words[first]::contains)
            if (sameMerchant && candidates[second].any { it.transaction.id in firstIds }) {
                val a = root(first)
                val b = root(second)
                if (a != b) parent[maxOf(a, b)] = minOf(a, b)
                linked += first
                linked += second
            }
        }
    }
    return linked.associateWith(::root)
}

private fun noMatchReason(
    row: StatementImportDraft,
    ledger: List<StatementLedgerTransaction>,
    claimed: Set<String>,
): StatementNoMatchReason {
    val day = isoDayNumber(row.date) ?: return StatementNoMatchReason.NoSameAmount
    val nearby =
        ledger.mapNotNull { transaction ->
            isoDayNumber(transaction.date)?.let { transaction to it - day }
        }
    fun closest(test: (StatementLedgerTransaction, Int) -> Boolean) =
        nearby.filter { (transaction, days) -> test(transaction, days) }.minByOrNull { kotlin.math.abs(it.second) }
    closest { t, days -> t.amountMinor == row.amountMinor && t.id in claimed && kotlin.math.abs(days) <= MATCH_WINDOW_DAYS }
        ?.let { return StatementNoMatchReason.AlreadyImported(it.first) }
    closest { t, days -> t.amountMinor == -row.amountMinor && kotlin.math.abs(days) <= MATCH_WINDOW_DAYS }
        ?.let { return StatementNoMatchReason.OppositeSign(it.first) }
    closest { t, _ -> t.amountMinor == row.amountMinor }
        ?.let { return StatementNoMatchReason.TooFarApart(it.first, it.second) }
    return if (nearby.none { kotlin.math.abs(it.second) <= MATCH_WINDOW_DAYS }) {
        StatementNoMatchReason.NothingNearby
    } else {
        StatementNoMatchReason.NoSameAmount
    }
}

private val GENERIC_WORDS =
    setOf(
        "ach",
        "and",
        "atm",
        "authorized",
        "autopay",
        "bill",
        "card",
        "cash",
        "charge",
        "check",
        "cheque",
        "com",
        "contactless",
        "credit",
        "debit",
        "deposit",
        "direct",
        "fee",
        "fees",
        "for",
        "from",
        "gmbh",
        "inc",
        "llc",
        "ltd",
        "mastercard",
        "mobile",
        "online",
        "payment",
        "payments",
        "pmt",
        "pos",
        "ppd",
        "purchase",
        "recurring",
        "ref",
        "reference",
        "service",
        "thank",
        "the",
        "transaction",
        "transfer",
        "visa",
        "web",
        "withdrawal",
        "www",
        "you",
    )

/** Words that can tell merchants apart: short words, numbers and generic banking words aren't evidence. */
internal fun meaningfulWords(text: String): Set<String> =
    text
        .lowercase()
        .split(Regex("[^\\p{L}]+"))
        .filter { it.length >= 3 && it !in GENERIC_WORDS }
        .toSet()

private fun ledgerWords(transaction: StatementLedgerTransaction): Set<String> =
    meaningfulWords(listOfNotNull(transaction.payeeName, transaction.importedPayee, transaction.notes).joinToString(" "))

private fun sharedWordCount(
    first: Set<String>,
    second: Set<String>,
): Int = first.count { word -> second.any { it == word || (minOf(it.length, word.length) >= 4 && (it.startsWith(word) || word.startsWith(it))) } }

internal fun isoDayNumber(date: String): Int? {
    val (year, month, day) = parseIsoDate(date) ?: return null
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yearOfEra = y - era * 400
    val dayOfYear = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * 146_097 + dayOfEra - 719_468
}

internal fun isoDate(dayNumber: Int): String {
    val z = dayNumber + 719_468
    val era = (if (z >= 0) z else z - 146_096) / 146_097
    val dayOfEra = z - era * 146_097
    val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val mp = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}
