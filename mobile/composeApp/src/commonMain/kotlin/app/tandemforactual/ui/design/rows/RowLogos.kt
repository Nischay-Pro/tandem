package app.tandemforactual.ui.design.rows

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.DrawableResource

/**
 * Finds a payee's or an account's logo by name, so every row naming one shows the same logo. An account's own bank
 * icon, when [bankIcon] has one, comes before the icon pack.
 */
internal class RowLogos(
    val payee: (String) -> DrawableResource?,
    val account: (String) -> DrawableResource?,
    val bankIcon: @Composable (accountName: String) -> Painter? = { null },
)

internal val LocalRowLogos = staticCompositionLocalOf<RowLogos?> { null }
