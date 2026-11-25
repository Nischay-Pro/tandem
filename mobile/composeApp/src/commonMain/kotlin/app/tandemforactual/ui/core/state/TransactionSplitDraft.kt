package app.tandemforactual.ui.core.state

/** One line of a split: part of the amount as typed, in its own category. */
internal data class SplitLine(
    val key: Int,
    val amountText: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val id: String? = null,
)

/**
 * A transaction being split across categories, and which line's category the picker is choosing, if any. Amounts are
 * unsigned minor units, like the form's total; [parse] reads a line's text in the budget's currency.
 */
internal data class TransactionSplitDraft(
    val lines: List<SplitLine>,
    val pickingLineKey: Int? = null,
) {
    fun amounts(parse: (String) -> Long?): List<Long?> = lines.map { line -> line.amountText.takeIf(String::isNotBlank)?.let(parse) }

    fun remaining(
        totalMinor: Long,
        parse: (String) -> Long?,
    ): Long = totalMinor - amounts(parse).sumOf { it ?: 0L }

    /** Ready to save: at least two lines, each with an amount and a category, adding up to the total exactly. */
    fun isReady(
        totalMinor: Long,
        parse: (String) -> Long?,
    ): Boolean =
        lines.size >= 2 &&
            lines.all { it.categoryId.isNotBlank() } &&
            amounts(parse).all { it != null && it > 0 } &&
            remaining(totalMinor, parse) == 0L

    /** Adds a line holding whatever is still unassigned. */
    fun addLine(remainingText: String): TransactionSplitDraft =
        copy(lines = lines + SplitLine((lines.maxOfOrNull { it.key } ?: 0) + 1, remainingText))

    fun removeLine(key: Int): TransactionSplitDraft = copy(lines = lines.filterNot { it.key == key })

    fun setAmount(
        key: Int,
        amountText: String,
    ): TransactionSplitDraft = copy(lines = lines.map { if (it.key == key) it.copy(amountText = amountText) else it })

    fun choosingCategoryFor(key: Int): TransactionSplitDraft = copy(pickingLineKey = key)

    /** Gives the line being picked the chosen category, and closes the picker. */
    fun pickCategory(
        id: String,
        name: String,
    ): TransactionSplitDraft =
        copy(
            lines = lines.map { if (it.key == pickingLineKey) it.copy(categoryId = id, categoryName = name) else it },
            pickingLineKey = null,
        )

    /** The lines to save over an existing split: Actual's id for each line kept, a signed amount and its category. */
    fun editedLines(
        parse: (String) -> Long?,
        income: Boolean,
    ): List<SplitEditLine> =
        lines.zip(signedLines(parse, income)).map { (line, signed) ->
            SplitEditLine(line.id, signed.first, signed.second)
        }

    /** The lines as signed amounts and categories for saving; spending lines are negative like the transaction. */
    fun signedLines(
        parse: (String) -> Long?,
        income: Boolean,
    ): List<Pair<Long, String>> =
        lines.zip(amounts(parse)).map { (line, amount) ->
            val unsigned = amount ?: 0L
            (if (income) unsigned else -unsigned) to line.categoryId
        }

    companion object {
        /** Starts a split with the form's category and whole amount on the first line, and an empty second line. */
        fun start(
            totalText: String,
            categoryId: String,
            categoryName: String,
        ) = TransactionSplitDraft(listOf(SplitLine(1, totalText, categoryId, categoryName), SplitLine(2)))
    }
}

/** One line of an edited split as it is sent: the existing line it replaces, if any, its signed amount and category. */
internal data class SplitEditLine(
    val id: String?,
    val amountMinor: Long,
    val categoryId: String,
)
