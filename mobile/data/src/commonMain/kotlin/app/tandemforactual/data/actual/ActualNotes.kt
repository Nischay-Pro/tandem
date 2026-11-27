package app.tandemforactual.data.actual

private val noteMonth = Regex("^\\d{4}-(0[1-9]|1[0-2])$")

/** Ids in Actual's `notes` table: categories and groups use their own id, accounts and budget months a prefixed one. */
object ActualNoteIds {
    fun account(accountId: String) = "account-$accountId"

    fun month(month: String) = "budget-$month"
}

/** Actual's `notes-save` for a category; an empty note clears it. */
fun ActualBudgetEdits.setCategoryNote(
    id: String,
    note: String,
) = writer.batch {
    categories().firstOrNull { it["id"] == id } ?: fail("Category does not exist in this budget")
    saveNote(id, note)
}

fun ActualBudgetEdits.setCategoryGroupNote(
    id: String,
    note: String,
) = writer.batch {
    groups().firstOrNull { it["id"] == id } ?: fail("Category group does not exist in this budget")
    saveNote(id, note)
}

fun ActualBudgetEdits.setAccountNote(
    id: String,
    note: String,
) = writer.batch {
    accounts().firstOrNull { it["id"] == id } ?: fail("Account does not exist in this budget")
    saveNote(ActualNoteIds.account(id), note)
}

/** [month] is `YYYY-MM`. */
fun ActualBudgetEdits.setMonthNote(
    month: String,
    note: String,
) = writer.batch {
    if (!noteMonth.matches(month)) fail("Choose a month in this budget")
    saveNote(ActualNoteIds.month(month), note)
}

private fun ActualBudgetEdits.saveNote(
    id: String,
    note: String,
) {
    val clean = note.trim()
    val existing = db.first("SELECT note FROM notes WHERE id = ?", id)?.get("note") as String?
    if (existing.orEmpty() != clean) writer.update("notes", id, mapOf("note" to clean))
}
