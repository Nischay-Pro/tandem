package app.tandemforactual.data.actual

/** Calendar months as Actual writes them: `YYYY-MM`. */
internal object ActualMonths {
    fun of(
        year: Int,
        month: Int,
    ): String = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}"

    fun fromDateInt(date: Long): String = of((date / 10000).toInt(), (date / 100 % 100).toInt())

    fun fromIsoDate(date: String): String = date.substring(0, 7)

    fun add(
        month: String,
        count: Int,
    ): String {
        val index = month.substring(0, 4).toInt() * 12 + month.substring(5, 7).toInt() - 1 + count
        return of(index.floorDiv(12), index.mod(12) + 1)
    }

    fun rangeInclusive(
        start: String,
        end: String,
    ): List<String> = generateSequence(start) { add(it, 1) }.takeWhile { it <= end }.toList()

    fun dbMonth(month: String): Long = month.replace("-", "").toLong()

    fun firstDay(month: String): Long = dbMonth(month) * 100 + 1

    fun lastDay(month: String): Long = dbMonth(month) * 100 + 31

    fun dateString(date: Long): String =
        "${(date / 10000).toString().padStart(
            4,
            '0',
        )}-${(date / 100 % 100).toString().padStart(2, '0')}-${(date % 100).toString().padStart(2, '0')}"

    fun dateInt(isoDate: String): Long = isoDate.replace("-", "").toLong()
}
