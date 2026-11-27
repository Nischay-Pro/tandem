@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.usage

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lucide

internal const val WHERE_USED_TITLE = "Where is this used?"

/** The row on a detail screen that opens [WhereUsedSheet]. */
@Composable
internal fun WhereUsedRow(onClick: () -> Unit) {
    ListRow(
        WHERE_USED_TITLE,
        "Transactions, rules and more",
        leading = RowLeading.Icon(Lucide.Link),
        onClick = onClick,
    )
}

/** Where to go from a count; null leaves that row as plain text. */
internal class UsageLinks(
    val transactions: (() -> Unit)? = null,
    val rules: (() -> Unit)? = null,
) {
    fun forKind(kind: UsageLineKind): (() -> Unit)? =
        when (kind) {
            UsageLineKind.TRANSACTIONS -> transactions
            UsageLineKind.RULES -> rules
            else -> null
        }
}

/** Read-only counts for one item, counted each time it opens, with links to the matching transactions or rules. */
@Composable
internal fun WhereUsedSheet(
    visible: Boolean,
    mobileSession: TandemMobileSession,
    target: UsageTarget,
    links: UsageLinks,
    onDismiss: () -> Unit,
) {
    TandemTaskSheet(visible = visible, onDismissRequest = onDismiss, onDismissed = {}) {
        val usage = rememberRecordUsage(mobileSession, target.budgetId, target.subject, target.recordId)
        SheetCard(
            maxWidth = 480.dp,
            header = { TandemModalHeader(title = WHERE_USED_TITLE, onClose = onDismiss, leadingIcon = Lucide.Link) },
        ) {
            WhereUsedBody(target.name, usage, target.subject, target.formatBalance, links)
        }
    }
}

/** The item a [WhereUsedSheet] counts for. */
internal class UsageTarget(
    val budgetId: String,
    val subject: UsageSubject,
    val recordId: String,
    val name: String,
    val formatBalance: (Long) -> String = Long::toString,
)

/** The sheet's content, also shown inline where an item's actions already live in a sheet. */
@Composable
internal fun WhereUsedBody(
    name: String,
    state: UsageState,
    subject: UsageSubject,
    formatBalance: (Long) -> String = Long::toString,
    links: UsageLinks,
) {
    Text(
        "Everything in this budget that uses $name.",
        color = tandemPalette.muted,
        fontSize = TandemTypography.body,
    )
    SurfaceCard {
        UsageContent(state, subject, formatBalance) { kind -> links.forKind(kind) }
    }
}
