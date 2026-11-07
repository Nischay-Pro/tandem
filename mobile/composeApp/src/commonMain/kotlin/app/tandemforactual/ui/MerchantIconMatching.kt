package app.tandemforactual.ui

private val storeNumberSuffix = Regex("(?:(?:store|location|branch) )?\\d+[a-z]?(?: (?:(?:store|location|branch) )?\\d+[a-z]?)*")

internal val supplementaryMerchantAliases =
    mapOf(
        // Costco fuel transactions are commonly labeled separately from the warehouse.
        "costco" to listOf("Costco Gas"),
    )

internal val accountIssuerSlugs =
    setOf(
        "aib",
        "american-express",
        "anb",
        "axis-bank",
        "bank-of-america",
        "barclays",
        "bbk",
        "bnp-paribas",
        "brex",
        "caixabank",
        "capital-one",
        "chase",
        "chime",
        "citibank",
        "commerzbank",
        "commonwealth-bank",
        "credit-suisse",
        "deutsche-bank",
        "discover",
        "hdfc-bank",
        "hsbc",
        "icici-bank",
        "mastercard",
        "maybank",
        "monzo",
        "n26",
        "nab",
        "nubank",
        "punjab-national-bank",
        "raiffeisen-bank-international",
        "revolut",
        "santander",
        "sberbank",
        "starling-bank",
        "visa",
        "wells-fargo",
        "wise",
    )

internal val supplementaryAccountIssuerAliases =
    mapOf(
        "american-express" to listOf("Amex"),
        "bank-of-america" to listOf("BofA", "BoA"),
        "hdfc-bank" to listOf("HDFC"),
        "icici-bank" to listOf("ICICI"),
        "mastercard" to listOf("Master Card"),
        "punjab-national-bank" to listOf("PNB"),
    )

internal class MerchantAliasMatcher<T> {
    private class Node<T> {
        val children = mutableMapOf<String, Node<T>>()
        var value: T? = null
        var titleMatch = false
        var licensePriority = Int.MAX_VALUE
    }

    private val root = Node<T>()

    fun add(
        label: String,
        value: T,
        licensePriority: Int,
        isTitle: Boolean,
    ) {
        val words = labelWords(label)
        if (words.isEmpty()) return

        var node = root
        for (word in words) node = node.children.getOrPut(word) { Node() }

        if (
            node.value == null ||
            licensePriority < node.licensePriority ||
            (licensePriority == node.licensePriority && isTitle && !node.titleMatch)
        ) {
            node.value = value
            node.titleMatch = isTitle
            node.licensePriority = licensePriority
        }
    }

    fun findPayeeIcon(payee: String): T? {
        val words = labelWords(payee)
        if (words.isEmpty()) return null

        var node = root
        var bestValue: T? = null
        for (index in words.indices) {
            node = node.children[words[index]] ?: break
            val value = node.value ?: continue
            val suffix = words.drop(index + 1)
            if (suffix.isEmpty() || isStoreNumberSuffix(suffix)) bestValue = value
        }
        return bestValue
    }

    fun findAccountIcon(accountName: String): T? {
        val words = labelWords(accountName)
        if (words.isEmpty()) return null

        var bestValue: T? = null
        var bestLength = 0
        var bestPriority = Int.MAX_VALUE
        for (start in words.indices) {
            var node = root
            for (end in start until words.size) {
                node = node.children[words[end]] ?: break
                val value = node.value ?: continue
                val matchLength = end - start + 1
                if (matchLength > bestLength || (matchLength == bestLength && node.licensePriority < bestPriority)) {
                    bestValue = value
                    bestLength = matchLength
                    bestPriority = node.licensePriority
                }
            }
        }
        return bestValue
    }
}

internal fun labelWords(label: String): List<String> {
    val words = mutableListOf<String>()
    val word = StringBuilder()
    label.lowercase().forEach { character ->
        if (character.isLetterOrDigit()) {
            word.append(character)
        } else if (word.isNotEmpty()) {
            words += word.toString()
            word.clear()
        }
    }
    if (word.isNotEmpty()) words += word.toString()
    return words
}

private fun isStoreNumberSuffix(words: List<String>): Boolean = words.isNotEmpty() && storeNumberSuffix.matches(words.joinToString(" "))
