@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui

import androidx.compose.runtime.Composable
import app.tandemforactual.data.actual.LinkedAccount
import app.tandemforactual.ui.design.rows.RowBadge
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lucide

private const val STALE_AFTER_MILLIS = 3L * 24 * 60 * 60 * 1000

/** No mark when unlinked; green when synced lately, amber when it hasn’t synced in days, red when its last sync failed. */
@Composable
internal fun bankLinkBadge(
    link: LinkedAccount?,
    nowMillis: Long,
): RowBadge? {
    link ?: return null
    val palette = tandemPalette
    val last = link.lastSyncMillis
    return when {
        link.failure != null -> {
            RowBadge(null, palette.negative, "Bank sync failed")
        }

        last == null || nowMillis - last > STALE_AFTER_MILLIS -> {
            RowBadge(Lucide.Clock, palette.warning, "Bank sync is behind")
        }

        else -> {
            RowBadge(Lucide.Link, palette.positive, "Linked to a bank")
        }
    }
}
