package app.tandemforactual.data.actual

/** Calendar arithmetic on Actual's `yyyy-MM-dd` dates and `yyyyMMdd` integers, using days since 1970-01-01. */
object ActualDates {
    fun toInt(isoDate: String): Long = isoDate.replace("-", "").toLong()

    fun dayNumber(date: Long): Long = daysFromCivil((date / 10_000).toInt(), (date / 100 % 100).toInt(), (date % 100).toInt())

    fun addDays(
        isoDate: String,
        days: Int,
    ): String {
        val (y, m, d) = isoDate.split('-').map(String::toInt)
        val (year, month, day) = civilFromDays(daysFromCivil(y, m, d) + days)
        return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }

    private fun daysFromCivil(
        year: Int,
        month: Int,
        day: Int,
    ): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
        val z = days + 719_468
        val era = (if (z >= 0) z else z - 146_096) / 146_097
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
        return Triple((yoe + era * 400 + if (month <= 2) 1 else 0).toInt(), month, day)
    }
}
