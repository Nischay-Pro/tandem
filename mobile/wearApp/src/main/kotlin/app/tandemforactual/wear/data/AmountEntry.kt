package app.tandemforactual.wear.data

/**
 * An amount typed the way a till takes it: each digit shifts in from the right, so 1, 2, 5, 0 reads 12.50 with two
 * decimals. Turning the crown moves it a whole unit at a time.
 */
data class AmountEntry(
    val minor: Long = 0,
    val spending: Boolean = true,
    val fractionDigits: Int = 2,
) {
    private val step: Long get() = if (fractionDigits <= 0) 100 else if (fractionDigits == 1) 10 else 1

    /** The signed amount in hundredths, negative for money out. */
    val signedMinor: Long get() = if (spending) -minor else minor

    fun digit(value: Int): AmountEntry {
        val next = minor * 10 + value * step
        return if (next > MAX_MINOR) this else copy(minor = next)
    }

    fun backspace(): AmountEntry = copy(minor = (minor / (10 * step)) * step)

    fun turn(units: Int): AmountEntry = copy(minor = (minor + units * 100L).coerceIn(0, MAX_MINOR))

    fun flipSign(): AmountEntry = copy(spending = !spending)

    companion object {
        const val MAX_MINOR = 99_999_999L
        private val SPOKEN = Regex("""(\d[\d\s,.]*)""")

        /** The first amount in dictated text such as "twelve fifty" written as "12.50" or "€8,20"; null when there's none. */
        fun spoken(
            text: String,
            fractionDigits: Int,
        ): Long? {
            val number = SPOKEN.find(text)?.value?.filterNot(Char::isWhitespace)?.trimEnd(',', '.') ?: return null
            val mark = number.indexOfLast { it == '.' || it == ',' }
            val decimals = if (mark >= 0) number.length - mark - 1 else 0
            val whole = if (mark >= 0 && decimals in 1..2) number.substring(0, mark) else number
            val fraction = if (mark >= 0 && decimals in 1..2) number.substring(mark + 1) else ""
            val units = whole.filter(Char::isDigit).ifEmpty { "0" }.toLongOrNull() ?: return null
            val hundredths = if (fractionDigits <= 0) 0 else fraction.padEnd(2, '0').take(2).toLong()
            return (units * 100 + hundredths).takeIf { it in 1..MAX_MINOR }
        }
    }
}
