package app.tandemforactual.data.database

import app.tandemforactual.data.actual.ActualDatabase

/** The device's copy of each Actual budget, one private, backup-excluded SQLite file per budget. */
interface ActualBudgetFiles {
    fun open(budgetId: String): ActualDatabase?

    fun install(
        budgetId: String,
        bytes: ByteArray,
    ): ActualDatabase

    fun remove(budgetId: String)
}

/** Budget ids become file names, so only Actual's id characters are accepted. */
internal fun budgetFileName(budgetId: String): String {
    require(Regex("^[A-Za-z0-9-]{1,100}$").matches(budgetId)) { "Invalid budget id." }
    return "$budgetId.sqlite"
}
