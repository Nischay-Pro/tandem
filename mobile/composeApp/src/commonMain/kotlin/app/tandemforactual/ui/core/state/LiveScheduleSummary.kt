package app.tandemforactual.ui.core.state

internal data class LiveScheduleSummary(
    val id: String,
    val name: String,
    val nextDate: String,
    val dateValueJson: String,
    val accountId: String?,
    val accountName: String?,
    val payeeId: String?,
    val amountMinor: Long?,
    val amountRangeMinor: Pair<Long, Long>?,
    val amountOp: String,
    val canSkip: Boolean,
)
