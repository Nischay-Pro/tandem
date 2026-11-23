package app.tandemforactual.ui.core.session

/** The one encrypted budget allowed to sync in the background, with its password, as kept in a device-protected store. */
object BackgroundBudgetKey {
    fun encode(
        budgetId: String,
        password: String,
    ): String = "$budgetId\n$password"

    fun decode(value: String?): Pair<String, String>? =
        value
            ?.split('\n', limit = 2)
            ?.takeIf { it.size == 2 && it[0].isNotBlank() && it[1].isNotEmpty() }
            ?.let { it[0] to it[1] }

    /** Which budget the store holds a key for, or null when it holds none or can't be read. */
    fun budgetIn(store: DeviceCredentialStore?): String? = runCatching { decode(store?.read())?.first }.getOrNull()
}
