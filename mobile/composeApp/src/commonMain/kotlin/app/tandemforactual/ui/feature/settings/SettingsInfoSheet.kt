@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal class InfoGroup(
    val title: StringResource,
    val points: List<StringResource>,
    val numbered: Boolean = false,
)

@Composable
internal fun SettingsInfoSheet(
    visible: Boolean,
    title: String,
    groups: List<InfoGroup>,
    onDismiss: () -> Unit,
) {
    TandemTaskSheet(visible = visible, onDismissRequest = onDismiss, onDismissed = {}) {
        SettingsInfoCard(title, groups, onDismiss)
    }
}

@Composable
internal fun SettingsInfoCard(
    title: String,
    groups: List<InfoGroup>,
    onDismiss: () -> Unit,
) {
    SheetCard(
        modifier = Modifier.padding(bottom = TandemSpacing.large),
        spacing = TandemSpacing.large,
        header = { TandemModalHeader(title, onClose = onDismiss, titleSize = TandemTypography.cardTitle) },
    ) {
        groups.forEach { InfoGroupView(it) }
    }
}

@Composable
private fun InfoGroupView(group: InfoGroup) {
    val palette = tandemPalette
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Text(
            stringResource(group.title),
            color = palette.muted,
            fontSize = TandemTypography.label,
            fontWeight = FontWeight.SemiBold,
        )
        group.points.forEachIndexed { index, point ->
            Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                Box(Modifier.padding(top = TandemSpacing.xxSmall).size(16.dp), contentAlignment = Alignment.Center) {
                    if (group.numbered) {
                        Box(Modifier.size(16.dp).clip(CircleShape).background(palette.accentSurface), contentAlignment = Alignment.Center) {
                            Text("${index + 1}", color = palette.accent, fontSize = TandemTypography.micro, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(palette.accent))
                    }
                }
                Text(
                    stringResource(point),
                    color = palette.text,
                    fontSize = TandemTypography.body,
                    lineHeight = TandemTypography.bodyLineHeight,
                )
            }
        }
    }
}
