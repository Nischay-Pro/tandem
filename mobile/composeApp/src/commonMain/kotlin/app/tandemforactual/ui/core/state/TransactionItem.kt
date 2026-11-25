@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.state
import androidx.compose.ui.graphics.vector.ImageVector
import app.tandemforactual.data.store.BudgetDisplaySettings
import org.jetbrains.compose.resources.DrawableResource

internal data class TransactionItem(
    val payee: String,
    val category: String,
    val dateLabel: String,
    val amountMinorUnits: Long,
    val icon: String,
    val categoryIcon: ImageVector? = null,
    val merchantIcon: DrawableResource? = null,
    val account: String = "Checking",
    val accountId: String? = null,
    val recordId: String? = null,
    val notes: String = "",
    val displaySettings: BudgetDisplaySettings? = null,
    val dateIso: String = "",
    val payeeId: String? = null,
    val categoryId: String? = null,
    val cleared: Boolean? = null,
    val reconciled: Boolean? = null,
    val approved: Boolean? = null,
    val matched: Boolean? = null,
    val isTransfer: Boolean = false,
    val isSplitParent: Boolean = false,
    val isPendingSplit: Boolean = false,
)

/** Transactions still waiting for a category: not transfers, and not split parents whose lines carry the categories. */
internal fun List<TransactionItem>.uncategorized() =
    filter { it.categoryId == null && !it.isTransfer && !it.isSplitParent && !it.isPendingSplit }
