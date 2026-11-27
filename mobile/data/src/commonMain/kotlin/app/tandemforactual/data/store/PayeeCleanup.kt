package app.tandemforactual.data.store

/** A payee name with the parts that change from one bank line to the next taken out, and what was taken. */
data class CleanedPayee(
    val name: String,
    val stripped: List<String>,
)

/** A payee as the cleanup sees it: its name and how many transactions use it. */
data class PayeeUsage(
    val id: String,
    val name: String,
    val transactions: Int,
)

/** Payees that look like the same merchant, the name they'd share, the one kept and what made them look different. */
data class PayeeDuplicateGroup(
    val name: String,
    val keepId: String,
    val members: List<PayeeUsage>,
    val stripped: List<String>,
)

private const val MIN_BOILERPLATE_PAYEES = 3

private val SHAPES =
    listOf(
        Regex("""\b[a-z]{3}\s?\d+[.,]\d+\b""", RegexOption.IGNORE_CASE),
        Regex("""@\s?\d+[.,]\d+"""),
        Regex("""\b\d{4}-\d{2}-\d{2}\b"""),
        Regex("""\b\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?\b|\b\d{1,2}\.\d{1,2}\.\d{2,4}\b"""),
        Regex("""\b\d{1,2}\s?(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\b""", RegexOption.IGNORE_CASE),
        Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b"""),
        Regex("""\b(?:card|crd)\s*(?:no\.?|#)?\s*[x*]*\d{4}\b""", RegexOption.IGNORE_CASE),
        Regex("""[x*]{2,}\d{2,4}\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:ref|id|txn|trx|auth)[:#\s]*[a-z0-9]*\d[a-z0-9]{3,}\b""", RegexOption.IGNORE_CASE),
        Regex("""#\s*\d+"""),
        Regex("""\b(?=[a-z0-9]*\d)(?=[a-z0-9]*[a-z])[a-z0-9]{6,}\b""", RegexOption.IGNORE_CASE),
        Regex("""\b\d{4,}\b"""),
    )

private val WEB_ADDRESS =
    Regex("""\b(?:https?://)?(?:www\.)?([a-z0-9-]+)\.(?:co\.uk|com|net|org|co|io|de|in|au|ca)\b(?:/\S*)?""", RegexOption.IGNORE_CASE)
private val SEPARATORS = Regex("""[\s*_]+""")
private val EDGES = Regex("""^[\s\-*/.,#:]+|[\s\-*/.,#:]+$""")

/**
 * [raw] read by its shape rather than by bank: dates, times, card fragments, reference and store numbers, exchange
 * rates and web-address dressing are taken out, then any [boilerplate] word at either end.
 */
fun cleanPayeeName(
    raw: String,
    boilerplate: Set<String>,
): CleanedPayee {
    val stripped = mutableListOf<String>()
    var text =
        WEB_ADDRESS.replace(raw) { match ->
            stripped += match.value
            match.groupValues[1]
        }
    SHAPES.forEach { shape ->
        text =
            shape.replace(text) { match ->
                stripped += match.value.trim()
                " "
            }
    }
    var words =
        text
            .replace(SEPARATORS, " ")
            .replace(EDGES, "")
            .split(' ')
            .filter(String::isNotBlank)
    while (words.size > 1 && words.first().lowercase() in boilerplate) {
        stripped += words.first()
        words = words.drop(1)
    }
    while (words.size > 1 && words.last().lowercase() in boilerplate) {
        stripped += words.last()
        words = words.dropLast(1)
    }
    return CleanedPayee(words.joinToString(" ").replace(EDGES, ""), stripped)
}

/** The form two names must share to count as the same merchant: letters and digits only, in lower case. */
fun payeeMatchKey(name: String): String = name.lowercase().filter(Char::isLetterOrDigit)

/**
 * Words that open or close at least three payees which otherwise differ, such as a card channel or a home city. Learned
 * from the budget's own names, so it works for any bank and country.
 */
fun learnPayeeBoilerplate(names: List<String>): Set<String> {
    val words = names.map { cleanPayeeName(it, emptySet()).name.split(' ') }.filter { it.size > 1 }
    val firsts = words.groupBy({ it.first().lowercase() }, { payeeMatchKey(it.drop(1).joinToString(" ")) })
    val lasts = words.groupBy({ it.last().lowercase() }, { payeeMatchKey(it.dropLast(1).joinToString(" ")) })
    return (firsts + lasts).filterValues { it.toSet().size >= MIN_BOILERPLATE_PAYEES }.keys
}

/** Payees whose cleaned names match, most used first; the most used of each is kept and its cleaned name suggested. */
fun payeeDuplicateGroups(payees: List<PayeeUsage>): List<PayeeDuplicateGroup> {
    val boilerplate = learnPayeeBoilerplate(payees.map { it.name })
    val cleaned = payees.associateWith { cleanPayeeName(it.name, boilerplate) }
    return payees
        .groupBy { payeeMatchKey(cleaned.getValue(it).name) }
        .filter { (key, members) -> key.isNotEmpty() && members.size > 1 }
        .values
        .map { members ->
            val ordered = members.sortedWith(compareByDescending<PayeeUsage> { it.transactions }.thenBy { it.name.length })
            val keep = ordered.first()
            PayeeDuplicateGroup(
                readableName(cleaned.getValue(keep).name),
                keep.id,
                ordered,
                members.flatMap { cleaned.getValue(it).stripped }.distinct(),
            )
        }.sortedByDescending { group -> group.members.sumOf { it.transactions } }
}

/** A name shouted in capitals, as banks write them, put in title case; anything already mixed is left alone. */
private fun readableName(name: String): String =
    if (name.any(Char::isLowerCase)) {
        name
    } else {
        name.lowercase().split(' ').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercaseChar) }
    }
