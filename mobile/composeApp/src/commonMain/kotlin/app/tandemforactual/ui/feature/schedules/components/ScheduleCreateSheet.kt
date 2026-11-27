@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.schedules.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.CalendarPlus
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X

/** The new-schedule form in a sheet over the list of bills. */
@Composable
internal fun ScheduleCreateSheet(
    mobileSession: TandemMobileSession,
    selection: ScheduleSelectionState,
    form: @Composable () -> Unit,
) {
    val close = { if (!mobileSession.isPerformingBudgetAction) selection.creating = false }
    TandemTaskSheet(visible = selection.creating, onDismissRequest = close, onDismissed = { selection.linkTransactionId = null }) {
        SheetCard(maxWidth = 520.dp, header = { ScheduleCreateHeader(mobileSession, selection, close) }) { form() }
    }
}

/** The new-schedule form in the pane beside the list. */
@Composable
internal fun ScheduleCreatePane(
    mobileSession: TandemMobileSession,
    selection: ScheduleSelectionState,
    form: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TandemSpacing.screen, vertical = TandemSpacing.large),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        ScheduleCreateHeader(mobileSession, selection) { if (!mobileSession.isPerformingBudgetAction) selection.creating = false }
        form()
    }
}

@Composable
private fun ScheduleCreateHeader(
    mobileSession: TandemMobileSession,
    selection: ScheduleSelectionState,
    onClose: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Icon(Lucide.CalendarPlus, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
        Text(
            if (selection.linkTransactionId != null) "Make this a schedule" else "New schedule",
            modifier = Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.sectionTitle,
            fontFamily = tandemAlloFamily(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        TandemIconButton(
            Lucide.X,
            "Close",
            sizeDp = 40,
            iconSizeDp = 18,
            containerColor = tandemPalette.raised,
            enabled = !mobileSession.isPerformingBudgetAction,
            onClick = onClose,
        )
    }
}
