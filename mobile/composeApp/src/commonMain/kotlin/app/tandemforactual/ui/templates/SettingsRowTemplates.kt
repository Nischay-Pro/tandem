@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.AnimatedReveal
import app.tandemforactual.ui.components.AnimatedStatusText
import app.tandemforactual.ui.components.LabelText
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.SelectableTile
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.crossfadeTransitions
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BriefcaseBusiness
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.CarFront
import com.composables.icons.lucide.Carrot
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Coffee
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.FingerprintPattern
import com.composables.icons.lucide.HeartPulse
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.LockKeyhole
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.NotepadText
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.PawPrint
import com.composables.icons.lucide.Plane
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Shirt
import com.composables.icons.lucide.ShoppingCart
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.Utensils
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.Zap

internal fun financeIcon(label: String): ImageVector? {
    val words = labelWords(label)

    if (words == listOf("account") || words == listOf("accounts")) return Lucide.WalletCards

    fun matches(vararg phrases: String): Boolean = phrases.any { containsPhrase(words, it) }

    return when {
        matches("biometric", "fingerprint") -> Lucide.FingerprintPattern
        matches("read-only", "read only", "view only") -> Lucide.Eye
        matches("password") -> Lucide.LockKeyhole
        matches("credential", "security key") -> Lucide.KeyRound
        matches("conflict", "attention needed", "needs attention", "review", "reason", "rejected") -> Lucide.CircleAlert
        matches("queued", "pending", "outbox", "changes", "local edits") -> Lucide.CloudSync
        matches("transaction", "transactions", "purchase", "receipt", "payee", "merchant") -> Lucide.Receipt
        matches("local data", "storage", "cached", "records", "snapshot") -> Lucide.Database
        matches("essentials") -> Lucide.House
        matches("upcoming", "due") -> Lucide.CalendarDays
        matches("pet", "pets", "veterinary", "vet clinic", "animal care") -> Lucide.PawPrint
        matches("flight", "flights", "airfare", "airport", "plane", "travel") -> Lucide.Plane
        matches("rent", "mortgage", "housing", "home maintenance", "home repair", "landlord") -> Lucide.House
        matches("electric", "utility", "utilities", "power bill", "water bill", "energy", "internet bill") -> Lucide.Zap
        matches("grocery", "groceries", "supermarket", "produce", "market", "household supplies") -> Lucide.Carrot
        matches("clothing", "clothes", "apparel", "garments", "shirts") -> Lucide.Shirt
        matches("shopping", "retail", "department store") -> Lucide.ShoppingCart
        matches("cafe", "coffee") -> Lucide.Coffee
        matches("dining", "restaurant", "takeout", "food and drink") -> Lucide.Utensils
        matches(
            "transport",
            "transit",
            "car payment",
            "auto repair",
            "fuel",
            "petrol",
            "gas station",
            "parking",
            "train fare",
        ) -> Lucide.CarFront
        matches("insurance", "coverage", "policy premium", "security", "privacy") -> Lucide.ShieldCheck
        matches("medical", "healthcare", "doctor", "pharmacy", "dental", "hospital") -> Lucide.HeartPulse
        matches("paycheck", "payroll", "salary", "income") -> Lucide.BriefcaseBusiness
        matches(
            "balance",
            "currency",
            "money",
            "amount",
            "number format",
            "assigned",
            "available",
            "ready to assign",
        ) -> Lucide.WalletCards
        matches("date", "calendar", "start of week", "planning period", "month", "due", "posted") -> Lucide.CalendarDays
        matches("sync", "synchronization", "cloud") -> Lucide.CloudSync
        matches("server", "connection", "api") -> Lucide.Server
        matches("device", "phone") -> Lucide.Smartphone
        matches("theme", "appearance", "accent", "color", "background", "motion") -> Lucide.Palette
        matches("layout", "home screen") -> Lucide.LayoutDashboard
        matches("storage", "database", "local data", "cache") -> Lucide.Database
        matches("notification", "reminder", "alert") -> Lucide.Bell
        matches("category", "categories", "group", "budget") -> Lucide.Tags
        matches("about", "help", "information", "project", "library", "license") -> Lucide.Info
        matches("notes", "memo") -> Lucide.NotepadText
        matches("payee", "merchant", "transaction", "purchase", "activity", "history") -> Lucide.Receipt
        else -> null
    }
}

internal fun containsPhrase(
    words: List<String>,
    phrase: String,
): Boolean {
    val phraseWords = labelWords(phrase)
    return phraseWords.isNotEmpty() && words.windowed(phraseWords.size).any { it == phraseWords }
}

@Composable
internal fun SettingRowLayout(
    title: String,
    subtitle: String?,
    enabled: Boolean,
    compactTitle: Boolean = false,
    semiboldTitle: Boolean = false,
    interactionModifier: Modifier = Modifier,
    control: @Composable RowScope.() -> Unit,
) {
    val palette = tandemPalette
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = TandemLayout.listRowMinHeight)
                .then(interactionModifier)
                .padding(horizontal = TandemLayout.listRowHorizontalInset, vertical = TandemLayout.listRowVerticalInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemLayout.listRowGap),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            Text(
                title,
                color = if (enabled) palette.text else palette.muted,
                fontSize = if (compactTitle) TandemTypography.compactBody else TandemTypography.body,
                fontWeight = if (semiboldTitle) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = palette.muted,
                    fontSize = TandemTypography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        control()
    }
}

@Composable
internal fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val palette = tandemPalette
    val thumbOffset by
        animateDpAsState(
            targetValue = if (checked) 18.dp else 0.dp,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
            label = "toggle-thumb-position",
        )
    val trackColor by
        animateColorAsState(
            targetValue = if (checked && enabled) palette.accent else palette.raised,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "toggle-track-color",
        )
    val thumbColor by
        animateColorAsState(
            targetValue = if (checked && enabled) palette.accentContent else palette.muted,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "toggle-thumb-color",
        )
    SettingRowLayout(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        compactTitle = true,
        interactionModifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    ) {
        Box(
            Modifier
                .width(42.dp)
                .height(24.dp)
                .clip(CircleShape)
                .background(if (enabled) trackColor else palette.raised)
                .padding(TandemSpacing.extraSmall),
        ) {
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (enabled) thumbColor else palette.muted),
            )
        }
    }
}

@Composable
internal fun CheckBoxRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val palette = tandemPalette
    val accent = if (enabled) palette.accent else palette.muted
    SettingRowLayout(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        interactionModifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(TandemShapes.small))
                .background(if (checked) accent else Color.Transparent)
                .border(1.5.dp, accent, RoundedCornerShape(TandemShapes.small))
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(17.dp), tint = palette.accentContent)
        }
    }
}

@Composable
internal fun SettingRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: () -> Unit,
) {
    ListRow(title, subtitle, value, onClick = onClick)
}

@Composable
internal fun ChoiceRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val selectionColor by
        animateColorAsState(
            targetValue = if (selected) palette.accent else palette.muted,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "choice-selection-color",
        )
    val selectionProgress by
        animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
            label = "choice-selection-progress",
        )
    SelectableTile(
        selected = selected,
        contentDescription = title,
        onSelect = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TandemShapes.large),
        selectedBackground = palette.surface,
        unselectedBackground = palette.surface,
        selectedBorder = palette.border,
        unselectedBorder = palette.border,
    ) {
        SettingRowLayout(
            title = title,
            subtitle = subtitle,
            enabled = true,
            semiboldTitle = true,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, selectionColor, CircleShape)
                    .padding(TandemSpacing.extraSmall),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = selectionProgress
                            scaleX = 0.75f + 0.25f * selectionProgress
                            scaleY = 0.75f + 0.25f * selectionProgress
                        }.clip(CircleShape)
                        .background(palette.accent),
                )
            }
        }
    }
}

@Composable
internal fun PreviewNote(text: String) {
    val palette = tandemPalette
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(TandemShapes.medium))
                .background(palette.raised)
                .padding(TandemSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        verticalAlignment = Alignment.Top,
    ) {
        Text("i", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = TandemTypography.body)
        AnimatedStatusText(
            text,
            palette.muted,
            fontSize = TandemTypography.label,
            lineHeight = TandemTypography.compactLineHeight,
            animateSizeChanges = true,
        )
    }
}

@Composable
internal fun InfoBubble(text: String) {
    val palette = tandemPalette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TandemShapes.medium))
            .background(palette.syncing.copy(alpha = 0.14f))
            .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Icon(Lucide.Info, contentDescription = null, modifier = Modifier.size(20.dp), tint = palette.syncing)
        Text(text, color = palette.text, fontSize = TandemTypography.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun AmountHero(
    label: String,
    amount: String,
    supporting: String?,
    action: (() -> Unit)? = null,
) {
    val palette = tandemPalette
    SurfaceCard(onClick = action) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            horizontalAlignment = Alignment.Start,
        ) {
            LabelText(label)
            Text(
                amount,
                color = palette.text,
                fontSize = TandemTypography.amount,
                lineHeight = TandemTypography.amountLineHeight,
                fontWeight = FontWeight.Bold,
            )
            supporting?.let { Text(it, color = palette.muted, fontSize = TandemTypography.label) }
        }
    }
}

@Composable
internal fun AnimatedAttentionContent(
    showingAttention: Boolean,
    animateSizeChanges: Boolean = false,
    content: @Composable AnimatedContentScope.(Boolean) -> Unit,
) {
    val reduceMotion = tandemReduceMotion
    AnimatedContent(
        targetState = showingAttention,
        modifier =
            if (animateSizeChanges) {
                Modifier.animateContentSize(tandemMotionSpec(reduceMotion, TandemMotion.CONTENT_SIZE_MILLIS))
            } else {
                Modifier
            },
        transitionSpec = {
            val (enter, exit) = crossfadeTransitions(reduceMotion, TandemMotion.STATUS_CHANGE_MILLIS)
            enter togetherWith exit using null
        },
        contentAlignment = Alignment.TopStart,
        label = "attention-content-transition",
        content = content,
    )
}

@Composable
internal fun AnimatedPreviewNote(text: String?) {
    var retainedText by remember { mutableStateOf(text) }
    LaunchedEffect(text) {
        if (text != null) retainedText = text
    }
    AnimatedReveal(visible = text != null) {
        PreviewNote(text ?: retainedText.orEmpty())
    }
}
