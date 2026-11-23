@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sharedbills

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.tandemforactual.data.planning.MemberMonth
import app.tandemforactual.data.planning.ShareState
import app.tandemforactual.ui.design.feedback.Badge
import app.tandemforactual.ui.design.theme.tandemPalette

@Composable
internal fun shareStateColor(state: ShareState): Color =
    when (state) {
        ShareState.PAID -> tandemPalette.positive
        ShareState.PARTLY_PAID -> tandemPalette.warning
        ShareState.NOT_YET_DUE -> tandemPalette.muted
        ShareState.MISSING -> tandemPalette.negative
        ShareState.NOT_TRACKED -> tandemPalette.border
    }

/** Paid, partly paid, not due yet or missing, for one person's share this month. */
@Composable
internal fun ShareStateBadge(
    month: MemberMonth,
    money: (Long) -> String,
) {
    Badge(
        when (month.state) {
            ShareState.PAID -> "Paid"
            ShareState.PARTLY_PAID -> "${money(month.paidMinor)} of ${money(month.member.shareMinor)}"
            ShareState.NOT_YET_DUE -> "Not due yet"
            ShareState.MISSING -> "Not paid"
            ShareState.NOT_TRACKED -> "Not tracked"
        },
        shareStateColor(month.state),
    )
}
