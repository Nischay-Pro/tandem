package app.tandemforactual.data.actual

import app.tandemforactual.data.regions.global.bank.BankProviders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** An account linked to a bank through Actual, and what to ask its provider for. */
data class LinkedAccount(
    val id: String,
    val name: String,
    val source: String,
    val providerAccountId: String,
    val bankId: String?,
    val bankName: String?,
    val startDate: String,
    val lastSyncMillis: Long? = null,
    val failure: String? = null,
)

/**
 * Actual's bank sync after the download: `normalizeBankSyncTransactions` and `processBankSyncDownload`. Providers are
 * reached through the Actual server, which holds their secrets; this turns what they return into transactions.
 */
class ActualBankSync(
    private val writer: ActualWriter,
    private val today: () -> String,
) {
    private val db get() = writer.db

    private fun pref(
        name: String,
        default: Boolean,
    ): Boolean = (db.first("SELECT value FROM preferences WHERE id = ?", name)?.get("value") as String?)?.let { it == "true" } ?: default

    /** Actual's synced `bank-sync-interval`: minutes between automatic bank syncs, or 0 when they're off. */
    fun syncIntervalMinutes(): Int =
        (db.first("SELECT value FROM preferences WHERE id = ?", SYNC_INTERVAL_PREF)?.get("value") as String?)
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it > 0 } ?: 0

    /** Saves the interval for every device; 0 turns automatic syncing off, and a custom one is at least 15 minutes. */
    fun setSyncIntervalMinutes(minutes: Int) =
        writer.update(
            "preferences",
            SYNC_INTERVAL_PREF,
            mapOf("value" to (if (minutes <= 0) 0 else maxOf(minutes, MIN_SYNC_INTERVAL_MINUTES)).toString()),
        )

    /**
     * `isAutomaticSyncDue`: due once any linked account went the interval without syncing, here or on another device.
     * [lastAutomaticRun] keeps a failing bank from being retried more than once per interval.
     */
    fun automaticSyncDue(
        now: Long,
        lastAutomaticRun: Long?,
    ): Boolean {
        val intervalMillis = syncIntervalMinutes() * 60_000L
        if (intervalMillis <= 0) return false
        if (lastAutomaticRun != null && now - lastAutomaticRun < intervalMillis) return false
        val linked = linkedAccounts()
        if (linked.isEmpty()) return false
        return now - linked.minOf { it.lastSyncMillis ?: 0L } >= intervalMillis
    }

    /** `link*Account` with `upgradingId`: points an existing account at a provider account, reusing its bank row. */
    fun linkAccount(
        accountId: String,
        source: String,
        providerAccountId: String,
        bankId: String,
        bankName: String?,
    ) = writer.batch {
        val bank =
            db.first("SELECT id FROM banks WHERE bank_id = ? AND name IS ? AND tombstone = 0", bankId, bankName)?.get("id") as String?
                ?: writer.insert("banks", linkedMapOf("bank_id" to bankId, "name" to bankName))
        writer.update("accounts", accountId, mapOf("account_id" to providerAccountId, "bank" to bank, "account_sync_source" to source))
    }

    /** `unlink-account`: drops the bank link but keeps the account and its transactions. */
    fun unlinkAccount(accountId: String) =
        writer.update(
            "accounts",
            accountId,
            mapOf(
                "account_id" to null,
                "bank" to null,
                "balance_current" to null,
                "balance_available" to null,
                "balance_limit" to null,
                "account_sync_source" to null,
            ),
        )

    /** `accountsBankSync`'s account list: open accounts with a bank link. */
    fun linkedAccounts(): List<LinkedAccount> =
        db
            .query(
                "SELECT a.id, a.name, a.account_id, a.account_sync_source, a.last_sync, b.bank_id, b.name AS bank_name FROM accounts a " +
                    "LEFT JOIN banks b ON a.bank = b.id WHERE a.tombstone = 0 AND a.closed = 0 AND b.bank_id IS NOT NULL " +
                    "AND a.account_id IS NOT NULL ORDER BY a.offbudget, a.sort_order",
            ).map {
                val id = it["id"] as String
                LinkedAccount(
                    id,
                    it["name"] as String,
                    BankProviders.forSource(it["account_sync_source"] as String?).source,
                    it["account_id"] as String,
                    it["bank_id"] as String?,
                    it["bank_name"] as String?,
                    startDate(id),
                    (it["last_sync"] as String?)?.toLongOrNull(),
                )
            }

    /** `getAccountSyncStartDate`: 89 days back, or the oldest transaction when that's later. */
    fun startDate(accountId: String): String {
        val floor = ActualDates.addDays(today(), -89)
        val oldest =
            (
                db
                    .first(
                        "SELECT MIN(date) AS d FROM v_transactions WHERE account = ? AND date <= ?",
                        accountId,
                        ActualDates.toInt(today()),
                    )?.get("d") as Number?
            )?.let { ActualMonths.dateString(it.toLong()) }
        return listOfNotNull(floor, oldest).max()
    }

    private fun hasTransactions(accountId: String) =
        db.first("SELECT 1 AS x FROM v_transactions WHERE account = ? AND date <= ? LIMIT 1", accountId, ActualDates.toInt(today())) != null

    /** Applies a provider download to the account; the first download also records its starting balance. */
    fun apply(
        account: LinkedAccount,
        download: JsonObject,
    ): ImportResult {
        val transactions =
            download["transactions"]
                ?.let {
                    (it as? JsonArray) ?: it.jsonObject["all"]?.jsonArray
                }.orEmpty()
                .map { it.jsonObject }
        val current = (download["startingBalance"] as? JsonPrimitive)?.longOrNull
        val initial = !hasTransactions(account.id)
        val rows = normalize(account.id, transactions)
        val import = ActualImport(writer)
        val options = { list: List<ImportRow> ->
            import.reconcile(
                account.id,
                list,
                titleCasePayees = false,
                strictIdChecking = false,
                updateDates = pref("sync-update-dates-${account.id}", false),
            )
        }
        if (initial) {
            val balance =
                if (BankProviders.forSource(account.source).startsFromCurrentBalance) {
                    current ?: 0
                } else {
                    transactions.fold(current ?: 0) { total, t -> total - amountOf(t) }
                }
            val date = transactions.lastOrNull()?.let { (it["date"] as? JsonPrimitive)?.contentOrNull } ?: today()
            val payee = startingBalancePayee()
            val offBudget =
                (db.first("SELECT offbudget FROM accounts WHERE id = ?", account.id)?.get("offbudget") as Number?)?.toLong() == 1L
            return writer.batch {
                val startingId =
                    ActualTransactions(writer)
                        .batchUpdate(
                            added =
                                listOf(
                                    mapOf(
                                        "id" to writer.uuid(),
                                        "account" to account.id,
                                        "amount" to balance,
                                        "category" to if (offBudget) null else payee.second,
                                        "payee" to payee.first,
                                        "date" to date,
                                        "cleared" to true,
                                        "starting_balance_flag" to true,
                                        "sort_order" to writer.nowMillis(),
                                    ),
                                ),
                        ).first()
                val result = options(rows)
                result.copy(added = listOf(startingId) + result.added)
            }
        }
        return writer.batch {
            val result = options(if (pref("sync-import-transactions-${account.id}", true)) rows else emptyList())
            writer.update(
                "accounts",
                account.id,
                buildMap {
                    if (current != null) put("balance_current", current)
                    put("last_sync", writer.nowMillis().toString())
                },
            )
            result
        }
    }

    private fun amountOf(transaction: JsonObject): Long =
        amountToInteger(
            (transaction["amount"] as? JsonPrimitive)?.contentOrNull
                ?: transaction["transactionAmount"]
                    ?.jsonObject
                    ?.get("amount")
                    ?.jsonPrimitive
                    ?.content ?: "0",
        )

    /** `normalizeBankSyncTransactions`: booked rows are cleared, pending rows follow the account's setting. */
    fun normalize(
        accountId: String,
        transactions: List<JsonObject>,
    ): List<ImportRow> {
        val importPending = pref("sync-import-pending-$accountId", true)
        val importNotes = pref("sync-import-notes-$accountId", true)
        val stored = db.first("SELECT value FROM preferences WHERE id = ?", "custom-sync-mappings-$accountId")?.get("value") as String?
        val mappings = stored?.let(::parseMappings)
        val categories = db.query("SELECT id FROM categories WHERE tombstone = 0").map { it["id"] as String }.toSet()
        return transactions.mapNotNull { t ->
            val cleared = (t["booked"] as? JsonPrimitive)?.booleanOrNull == true
            if (!importPending && !cleared) return@mapNotNull null
            val amount = amountOf(t)
            val mapping = mappings?.get(if (amount <= 0) "payment" else "deposit")

            fun field(
                key: String,
                default: String,
            ) = (t[mapping?.get(key) ?: default] as? JsonPrimitive)?.contentOrNull
            val date =
                field("date", "date") ?: (t["date"] as? JsonPrimitive)?.contentOrNull
                    ?: error("`date` is required when adding a transaction")
            val payee =
                field("payee", "payeeName") ?: (t["payeeName"] as? JsonPrimitive)?.contentOrNull
                    ?: error("`payeeName` is required when adding a transaction")
            val notes = field("notes", "notes")
            val transactionId = (t["transactionId"] as? JsonPrimitive)?.contentOrNull
            val internalId = (t["internalTransactionId"] as? JsonPrimitive)?.contentOrNull
            val providerAccount = (t["account"] as? JsonPrimitive)?.contentOrNull
            ImportRow(
                date = date,
                amount = amount,
                payeeName = payee,
                notes = if (importNotes && notes != null) notes.trim().replace("#", "##") else null,
                importedId = transactionId ?: if (cleared && internalId != null) "$providerAccount-$internalId" else null,
                category = (t["category"] as? JsonPrimitive)?.contentOrNull?.takeIf { it in categories },
                cleared = cleared,
                rawSyncedData = t.toString(),
            )
        }
    }

    private fun parseMappings(raw: String): Map<String, Map<String, String>>? =
        runCatching {
            Json.parseToJsonElement(raw).jsonObject.mapValues { (_, v) -> v.jsonObject.mapValues { it.value.jsonPrimitive.content } }
        }.getOrNull()

    private fun startingBalancePayee(): Pair<String, String?> {
        val category =
            db
                .first(
                    "SELECT id FROM categories WHERE is_income = 1 AND LOWER(name) = 'starting balances' AND tombstone = 0",
                )?.get("id") as String?
                ?: db.first("SELECT id FROM categories WHERE is_income = 1 AND tombstone = 0")?.get("id") as String?
        val payee =
            db.first("SELECT id FROM payees WHERE LOWER(name) = 'starting balance' AND tombstone = 0")?.get("id") as String?
                ?: ActualTransactions(writer).insertPayee("Starting Balance")
        return payee to category
    }

    companion object {
        const val SYNC_INTERVAL_PREF = "bank-sync-interval"
        const val MIN_SYNC_INTERVAL_MINUTES = 15

        /** `amountToInteger`: a decimal amount string or number in cents, rounded like Actual. */
        fun amountToInteger(amount: String): Long {
            val value = amount.trim().toBigDecimalOrZero()
            return kotlin.math.round(value * 100).toLong()
        }

        private fun String.toBigDecimalOrZero(): Double = toDoubleOrNull() ?: 0.0
    }
}
