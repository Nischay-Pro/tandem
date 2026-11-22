@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide

@Composable
internal fun BiometricPasswordCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = tandemPalette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TandemShapes.medium))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = TandemSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(TandemShapes.small))
                .background(if (checked) palette.accent else palette.raised)
                .border(1.5.dp, if (checked) palette.accent else palette.border, RoundedCornerShape(TandemShapes.small)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = palette.accentContent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
            Text("Use biometrics to unlock this budget", color = palette.text, fontSize = TandemTypography.compactBody)
            Text(
                "Stores the password on this device, protected by your fingerprint or face",
                color = palette.muted,
                fontSize = TandemTypography.caption,
            )
        }
    }
}
