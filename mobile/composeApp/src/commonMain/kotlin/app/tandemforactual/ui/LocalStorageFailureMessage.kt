package app.tandemforactual.ui

private val storageFullIndicators =
    listOf(
        "sqlite_full",
        "database or disk is full",
        "disk is full",
        "no space left on device",
    )

internal fun localStorageFailureMessage(
    error: Throwable,
    fallback: String,
): String {
    val visited = mutableSetOf<Throwable>()
    var cause: Throwable? = error
    while (cause != null && visited.add(cause)) {
        val message = cause.message?.lowercase().orEmpty()
        if (storageFullIndicators.any(message::contains)) {
            return "This device is out of storage. Free up space and try again."
        }
        cause = cause.cause
    }
    return fallback
}
