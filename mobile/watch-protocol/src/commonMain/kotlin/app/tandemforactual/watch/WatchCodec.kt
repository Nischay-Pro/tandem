package app.tandemforactual.watch

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** Data Layer paths and capabilities both apps agree on. */
object WatchPaths {
    const val SUMMARY = "/tandem/summary"
    const val SUMMARY_KEY = "summary"
    const val SUMMARY_REQUEST = "/tandem/summary-request"
    const val QUICK_ADD = "/tandem/quick-add"
    const val QUICK_ADD_RESULT = "/tandem/quick-add-result"
    const val PHONE_CAPABILITY = "tandem_phone"
    const val WATCH_CAPABILITY = "tandem_watch"
}

object WatchCodec {
    const val MAX_SUMMARY_BYTES = 2048
    private const val SHORT_NAME = 18

    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    /** The summary as compact JSON, trimmed until it fits in [MAX_SUMMARY_BYTES]. */
    fun encode(summary: WatchSummary): ByteArray = encodeRaw(fitted(summary))

    fun fitted(summary: WatchSummary): WatchSummary {
        val capped =
            summary.copy(
                categories =
                    summary.categories
                        .take(WatchSummary.MAX_CATEGORIES)
                        .map { it.copy(recent = it.recent.take(WatchSummary.MAX_RECENT)) },
                bills = summary.bills.take(WatchSummary.MAX_BILLS),
                quickCategories = summary.quickCategories.take(WatchSummary.MAX_CATEGORIES),
                recentPayees = summary.recentPayees.take(WatchSummary.MAX_CATEGORIES),
            )
        return trims(capped).firstOrNull { encodeRaw(it).size <= MAX_SUMMARY_BYTES } ?: emptied(capped)
    }

    fun decodeSummary(bytes: ByteArray): WatchSummary? =
        decode(WatchSummary.serializer(), bytes)?.takeIf { it.version == WatchSummary.VERSION }

    fun encodeQuickAdd(entry: WatchQuickAdd): ByteArray = encodeWith(WatchQuickAdd.serializer(), entry)

    fun decodeQuickAdd(bytes: ByteArray): WatchQuickAdd? = decode(WatchQuickAdd.serializer(), bytes)

    fun encodeResult(result: WatchQuickAddResult): ByteArray = encodeWith(WatchQuickAddResult.serializer(), result)

    fun decodeResult(bytes: ByteArray): WatchQuickAddResult? = decode(WatchQuickAddResult.serializer(), bytes)

    fun encodeQueue(items: List<PendingQuickAdd>): String = json.encodeToString(PendingQuickAdd.listSerializer, items)

    fun decodeQueue(text: String?): List<PendingQuickAdd> =
        text?.let { runCatching { json.decodeFromString(PendingQuickAdd.listSerializer, it) }.getOrNull() }.orEmpty()

    private fun encodeRaw(summary: WatchSummary): ByteArray = encodeWith(WatchSummary.serializer(), summary)

    private fun <T> encodeWith(
        serializer: KSerializer<T>,
        value: T,
    ): ByteArray = json.encodeToString(serializer, value).encodeToByteArray()

    private fun <T> decode(
        serializer: KSerializer<T>,
        bytes: ByteArray,
    ): T? = runCatching { json.decodeFromString(serializer, bytes.decodeToString()) }.getOrNull()

    /** Ever smaller versions: fewer recent transactions, then no extras, then shorter names, then fewer categories. */
    private fun trims(summary: WatchSummary): Sequence<WatchSummary> =
        sequence {
            for (keep in WatchSummary.MAX_RECENT downTo 0) {
                yield(summary.copy(categories = summary.categories.map { it.copy(recent = it.recent.take(keep)) }))
            }
            val bare = summary.copy(categories = summary.categories.map { it.copy(recent = emptyList()) })
            val lean = bare.copy(quickCategories = emptyList(), recentPayees = emptyList())
            yield(lean)
            val short = lean.shortened()
            yield(short)
            for (keep in short.categories.size - 1 downTo 0) {
                yield(short.copy(categories = short.categories.take(keep)))
            }
        }

    private fun WatchSummary.shortened(): WatchSummary =
        copy(
            budgetName = budgetName.short(),
            accountName = accountName?.short(),
            categories = categories.map { it.copy(name = it.name.short()) },
            bills = bills.map { it.copy(name = it.name.short()) },
            money = money.copy(symbol = money.symbol.take(4)),
        )

    private fun emptied(summary: WatchSummary): WatchSummary =
        summary.copy(
            budgetName = summary.budgetName.short(),
            accountName = null,
            categories = emptyList(),
            quickCategories = emptyList(),
            recentPayees = emptyList(),
            bills = emptyList(),
        )

    private fun String.short(): String = if (length <= SHORT_NAME) this else take(SHORT_NAME - 1).trimEnd() + "…"
}
