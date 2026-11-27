package app.tandemforactual.ui.core.state

/** A move asked for from outside the category screen: [coverMinor] above zero covers that much overspending. */
internal data class MoveMoneyRequest(
    val coverMinor: Long,
)
