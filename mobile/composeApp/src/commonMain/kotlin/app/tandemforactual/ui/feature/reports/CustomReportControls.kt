@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reports
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.data.planning.CustomReport
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Save
import com.composables.icons.lucide.Trash2

/** The delete and save buttons under a custom report. */
@Composable
internal fun CustomReportActions(
    saved: CustomReport?,
    draft: CustomReport,
    onDelete: () -> Unit,
    onSave: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        if (saved != null) {
            TandemActionButton("Delete", TandemActionButtonTone.DESTRUCTIVE, Modifier.weight(1f), icon = Lucide.Trash2) {
                onDelete()
            }
        }
        TandemActionButton(
            when {
                saved == null -> "Save report"
                saved == draft -> "Saved"
                else -> "Save changes"
            },
            TandemActionButtonTone.PRIMARY,
            Modifier.weight(1f),
            icon = Lucide.Save,
            enabled = saved != draft,
        ) {
            onSave()
        }
    }
}

/** The from and to date fields shown for a custom period. */
@Composable
internal fun CustomRangeFields(
    draft: CustomReport,
    onChange: (CustomReport) -> Unit,
) {
    Row(Modifier.padding(horizontal = TandemSpacing.large), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Column(Modifier.weight(1f)) {
            TextFieldCard(
                "From",
                draft.startDate.orEmpty(),
                { onChange(draft.copy(startDate = it.take(10))) },
                TextFieldOptions(placeholder = "2026-01-01"),
            )
        }
        Column(Modifier.weight(1f)) {
            TextFieldCard(
                "To",
                draft.endDate.orEmpty(),
                { onChange(draft.copy(endDate = it.take(10))) },
                TextFieldOptions(placeholder = "2026-03-31"),
            )
        }
    }
}

@Composable
internal fun ControlLabel(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = TandemSpacing.large),
        color = tandemPalette.muted,
        fontSize = TandemTypography.caption,
        fontWeight = FontWeight.SemiBold,
    )
}

internal fun String?.isIsoDate() = this != null && Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(this)
