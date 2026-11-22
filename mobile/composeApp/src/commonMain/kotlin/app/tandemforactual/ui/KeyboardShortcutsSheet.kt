@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemModalHeader
import app.tandemforactual.ui.components.TandemTaskSheet

@Composable
internal fun KeyboardShortcutsSheet(
    visible: Boolean,
    mode: InterfaceMode,
    onClose: () -> Unit,
) {
    val palette = tandemPalette
    val command = if (platformUsesCommandKey()) "⌘" else "Ctrl"
    TandemTaskSheet(visible = visible, onDismissRequest = onClose, onDismissed = {}) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = TandemSpacing.large)) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            ) {
                TandemModalHeader("Keyboard shortcuts", onClose = onClose)
                shortcutHelpEntries(mode, command).forEach { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = TandemSpacing.extraSmall)
                            .semantics(mergeDescendants = true) { contentDescription = "${entry.action}: ${entry.keys}" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                    ) {
                        Text(entry.action, Modifier.weight(1f), color = palette.text, fontSize = TandemTypography.body)
                        Text(
                            entry.keys,
                            Modifier
                                .background(palette.raised, RoundedCornerShape(TandemShapes.small))
                                .border(1.dp, palette.border, RoundedCornerShape(TandemShapes.small))
                                .padding(horizontal = TandemSpacing.small, vertical = TandemSpacing.xxSmall),
                            color = palette.text,
                            fontSize = TandemTypography.compactBody,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
