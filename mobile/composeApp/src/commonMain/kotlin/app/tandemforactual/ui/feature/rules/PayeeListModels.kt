@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.rules
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Sparkles
import kotlinx.coroutines.flow.first

/** Live, non-transfer payees as id-name pairs sorted by name. */
@Composable
internal fun rememberManagedPayees(payeeRecords: List<BudgetRecordEntity>): List<Pair<String, String>> =
    remember(payeeRecords) {
        payeeRecords
            .filterNot { record ->
                val body = record.recordBody()
                record.localDeleted ||
                    record.remoteDeleted ||
                    body?.booleanValue("tombstone") == true ||
                    body?.stringValue("transfer_acct") != null
            }.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }

/** Visible categories as id-name pairs, led by a "No default" option. */
@Composable
internal fun rememberPayeeCategoryOptions(categoryRecords: List<BudgetRecordEntity>): List<Pair<String, String>> =
    remember(categoryRecords) {
        listOf("" to "No default") +
            categoryRecords
                .filterNot { record ->
                    record.localDeleted ||
                        record.remoteDeleted ||
                        record.recordBody()?.booleanValue("tombstone") == true ||
                        record.recordBody()?.booleanValue("hidden") == true
                }.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }
                .sortedBy { it.second.lowercase() }
    }

/** Payee count and the add-payee button above the payee list. */
@Composable
internal fun PayeeListSummary(
    summary: String,
    addEnabled: Boolean,
    onCleanUp: (() -> Unit)?,
    onAddPayee: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            summary,
            modifier = Modifier.weight(1f),
            color = tandemPalette.muted,
            fontSize = TandemTypography.caption,
        )
        if (onCleanUp != null) {
            TandemIconButton(
                Lucide.Sparkles,
                "Clean up payees",
                tint = tandemPalette.accent,
                containerColor = tandemPalette.accentSurface,
                onClick = onCleanUp,
            )
        }
        TandemIconButton(
            Lucide.Plus,
            "Add payee",
            enabled = addEnabled,
            tint = tandemPalette.accent,
            containerColor = tandemPalette.accentSurface,
            onClick = onAddPayee,
        )
    }
}

/** Empty state shown when the budget has no payees. */
internal fun LazyListScope.noPayeesItem() {
    item(key = "manage-payees-empty") {
        SurfaceCard {
            EmptyState(
                "No payees",
                "Add a payee to use it in a transaction.",
                modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
            )
        }
    }
}

/** Empty state shown when the search matches no payees. */
internal fun LazyListScope.noMatchingPayeesItem() {
    item(key = "manage-payees-no-matches") {
        SurfaceCard {
            EmptyState(
                "No matching payees",
                modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
            )
        }
    }
}

/** "120 payees", or "4 of 120 payees" while a search narrows the list. */
internal fun payeeCountSummary(
    shown: Int,
    total: Int,
    searching: Boolean,
): String = if (searching) "${formatInteger(shown)} of ${formatInteger(total)} payees" else "${formatInteger(total)} payees"
