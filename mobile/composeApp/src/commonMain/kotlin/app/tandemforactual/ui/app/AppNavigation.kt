@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Icon
import androidx.compose.material.LocalElevationOverlay
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.InterfaceMode
import app.tandemforactual.ui.core.state.LocalInterfaceMode
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.nav_accounts
import app.tandemforactual.ui.generated.resources.nav_activity
import app.tandemforactual.ui.generated.resources.nav_budget
import app.tandemforactual.ui.generated.resources.nav_home
import app.tandemforactual.ui.generated.resources.nav_reports
import app.tandemforactual.ui.generated.resources.nav_settings
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FloatingTransactionAction(
    compact: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingAction(
        compact = compact,
        enabled = enabled,
        label = "Add transaction",
        contentDescription = if (enabled) "Add transaction" else "Read-only mode is on",
        icon = Lucide.Plus,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
internal fun FloatingSyncAction(
    compact: Boolean,
    syncing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingAction(
        compact = compact,
        enabled = enabled,
        label = "Sync",
        contentDescription = "Sync",
        icon = Lucide.CloudSync,
        loading = syncing,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
internal fun TandemHeaderMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = tandemPalette
    val colors =
        MaterialTheme.colors.copy(
            surface = palette.surface,
            background = palette.surface,
            onSurface = palette.text,
        )
    val shapes = MaterialTheme.shapes.copy(small = RoundedCornerShape(TandemShapes.large))
    MaterialTheme(colors = colors, shapes = shapes) {
        CompositionLocalProvider(LocalElevationOverlay provides null) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = onDismiss,
                modifier = Modifier.widthIn(min = 200.dp, max = 280.dp),
            ) {
                Column(Modifier.padding(vertical = TandemSpacing.extraSmall), content = content)
            }
        }
    }
}

@Composable
internal fun HeaderMenuAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(TandemShapes.control))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = palette.muted)
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = palette.text,
            fontSize = TandemTypography.compactBody,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Navigation labels grow with the system font size up to 130%, so they still fit the tab bar and rail. */
internal fun navigationLabelDensity(density: Density): Density = Density(density.density, density.fontScale.coerceAtMost(1.3f))

@Composable
internal fun navigationTabs(): List<Pair<AppRoute, String>> = navigationTabs(LocalInterfaceMode.current)

/** Minimal keeps four destinations; Advanced adds Accounts and Reports and reaches Settings from Home. */
@Composable
internal fun navigationTabs(mode: InterfaceMode): List<Pair<AppRoute, String>> =
    navigationRoutes(mode).map { route ->
        route to
            stringResource(
                when (route) {
                    AppRoute.HOME -> Res.string.nav_home
                    AppRoute.BUDGET -> Res.string.nav_budget
                    AppRoute.ACCOUNTS -> Res.string.nav_accounts
                    AppRoute.ACTIVITY -> Res.string.nav_activity
                    AppRoute.REPORTS -> Res.string.nav_reports
                    else -> Res.string.nav_settings
                },
            )
    }

internal fun navigationRoutes(mode: InterfaceMode): List<AppRoute> =
    when (mode) {
        InterfaceMode.MINIMAL -> listOf(AppRoute.HOME, AppRoute.ACTIVITY, AppRoute.BUDGET, AppRoute.SETTINGS)
        InterfaceMode.ADVANCED -> listOf(AppRoute.HOME, AppRoute.ACCOUNTS, AppRoute.ACTIVITY, AppRoute.BUDGET, AppRoute.REPORTS)
    }
