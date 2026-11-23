package app.tandemforactual.ui.design.rows

import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.compose.resources.DrawableResource

/** Finds a payee's or an account's logo by name, so every row naming one shows the same logo. */
internal class RowLogos(
    val payee: (String) -> DrawableResource?,
    val account: (String) -> DrawableResource?,
)

internal val LocalRowLogos = staticCompositionLocalOf<RowLogos?> { null }
