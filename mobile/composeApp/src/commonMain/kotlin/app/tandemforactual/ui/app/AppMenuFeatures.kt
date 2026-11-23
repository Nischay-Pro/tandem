@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.Search

/** Search across every feature, the person's pinned ones, and the rest folded under "All features". */
@Composable
internal fun MenuFeatures(
    state: AppState,
    items: List<AppMenuItem>,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    val pins = decodeMenuPins(state.menuPins)
    val togglePin: (AppMenuItem) -> Unit = { state.menuPins = toggleMenuPin(state.menuPins, it.label) }
    Spacer(Modifier.height(TandemSpacing.medium))
    TextFieldCard(
        "Find a feature",
        query,
        { query = it },
        TextFieldOptions(placeholder = "Reports, bills, retirement", leadingIcon = Lucide.Search),
    )
    if (query.isNotBlank()) {
        MenuSearchResults(searchMenu(items, query), query, pins, togglePin)
        return
    }
    val sections = menuSections(items, state.menuPins)
    MenuHeading("PINNED", if (editing) "Done" else "Edit") { editing = !editing }
    if (sections.pinned.isEmpty()) MenuNote("Pin the features you use most. Tap Edit, or search and tap the pin.")
    sections.pinned.forEach { MenuRow(it, it.label in pins, togglePin.takeIf { editing }) }
    AdvancedModeRow(state)
    AllFeatures(sections.others, editing, pins, togglePin)
}

@Composable
private fun MenuSearchResults(
    results: List<AppMenuItem>,
    query: String,
    pins: List<String>,
    togglePin: (AppMenuItem) -> Unit,
) {
    Spacer(Modifier.height(TandemSpacing.small))
    if (results.isEmpty()) MenuNote("Nothing matches “${query.trim()}”")
    results.forEach { MenuRow(it, it.label in pins, togglePin) }
}

@Composable
private fun AllFeatures(
    others: List<AppMenuItem>,
    editing: Boolean,
    pins: List<String>,
    togglePin: (AppMenuItem) -> Unit,
) {
    if (others.isEmpty()) return
    val palette = tandemPalette
    val reduceMotion = tandemReduceMotion
    var expanded by rememberSaveable { mutableStateOf(false) }
    val open = expanded || editing
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(TandemShapes.control))
            .toggleable(value = open, enabled = !editing, role = Role.Button) { expanded = it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "All features",
            Modifier.weight(1f),
            color = palette.text,
            fontSize = TandemTypography.action,
            fontWeight = FontWeight.Medium,
        )
        Text("${others.size}", color = palette.muted, fontSize = TandemTypography.compactBody)
        Icon(
            if (open) Lucide.ChevronUp else Lucide.ChevronDown,
            null,
            Modifier.padding(start = TandemSpacing.small).size(18.dp),
            tint = palette.muted,
        )
    }
    AnimatedVisibility(
        visible = open,
        enter =
            fadeIn(tandemMotionSpec(reduceMotion, TandemMotion.CONTROL_MILLIS)) +
                expandVertically(tandemMotionSpec(reduceMotion, TandemMotion.CONTROL_MILLIS)),
        exit =
            fadeOut(tandemMotionSpec(reduceMotion, TandemMotion.CONTROL_MILLIS)) +
                shrinkVertically(tandemMotionSpec(reduceMotion, TandemMotion.CONTROL_MILLIS)),
    ) {
        Column { others.forEach { MenuRow(it, it.label in pins, togglePin.takeIf { editing }) } }
    }
}

@Composable
private fun MenuHeading(
    title: String,
    action: String,
    onAction: () -> Unit,
) {
    val palette = tandemPalette
    Row(Modifier.fillMaxWidth().padding(top = TandemSpacing.large), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            Modifier.weight(1f).semantics { heading() },
            color = palette.muted,
            fontSize = TandemTypography.caption,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
        Text(
            action,
            Modifier
                .clip(RoundedCornerShape(TandemShapes.control))
                .clickable(role = Role.Button, onClick = onAction)
                .padding(horizontal = TandemSpacing.small, vertical = TandemSpacing.small),
            color = palette.accent,
            fontSize = TandemTypography.compactBody,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MenuNote(text: String) {
    Text(
        text,
        Modifier.padding(vertical = TandemSpacing.small),
        color = tandemPalette.muted,
        fontSize = TandemTypography.compactBody,
    )
}

/** One feature row; with [onPin] it also shows a pin the person can turn on or off. */
@Composable
internal fun MenuRow(
    item: AppMenuItem,
    pinned: Boolean = false,
    onPin: ((AppMenuItem) -> Unit)? = null,
) {
    val palette = tandemPalette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(TandemShapes.control))
            .clickable(role = Role.Button, onClick = item.onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, null, Modifier.size(22.dp), tint = palette.text)
        Column(Modifier.weight(1f).padding(start = TandemSpacing.medium)) {
            Text(
                item.label,
                color = palette.text,
                fontSize = TandemTypography.action,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.hint?.let {
                Text(
                    it,
                    color = if (item.hintIsAlert) palette.negative else palette.muted,
                    fontSize = TandemTypography.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.external) Icon(Lucide.ExternalLink, null, Modifier.padding(start = TandemSpacing.small).size(18.dp), tint = palette.muted)
        if (onPin != null) PinToggle(item.label, pinned) { onPin(item) }
    }
}

@Composable
private fun PinToggle(
    label: String,
    pinned: Boolean,
    onToggle: () -> Unit,
) {
    val palette = tandemPalette
    Box(
        Modifier
            .padding(start = TandemSpacing.small)
            .size(44.dp)
            .clip(CircleShape)
            .background(if (pinned) palette.accentSurface else Color.Transparent)
            .toggleable(value = pinned, role = Role.Checkbox) { onToggle() }
            .semantics { contentDescription = "Pin $label" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Lucide.Pin, null, Modifier.size(18.dp), tint = if (pinned) palette.accent else palette.muted)
    }
}
