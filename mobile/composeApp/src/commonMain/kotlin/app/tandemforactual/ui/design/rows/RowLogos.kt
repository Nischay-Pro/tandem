package app.tandemforactual.ui.design.rows

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import app.tandemforactual.ui.design.icons.LogoPainter
import org.jetbrains.compose.resources.DrawableResource

/**
 * Finds a payee's or an account's logo by name, so every row naming one shows the same logo. A symbol the person
 * picked for an account, in [chosenIcon], comes first, then its own bank icon from [bankIcon], then the icon pack.
 */
internal class RowLogos(
    val payee: (String) -> DrawableResource?,
    val account: (String) -> DrawableResource?,
    val bankIcon: @Composable (accountName: String) -> LogoPainter? = { null },
    val bySlug: (String) -> DrawableResource? = { null },
    val chosenIcon: (accountName: String) -> ImageVector? = { null },
)

internal val LocalRowLogos = staticCompositionLocalOf<RowLogos?> { null }
