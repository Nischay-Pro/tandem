package app.tandemforactual.ui.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/** Whether account, category and payee names are masked for screen sharing, along with the amounts. */
internal val LocalMaskNames = compositionLocalOf { false }

/** The same length for every name, so a masked list doesn't hint at which name is which. */
internal const val MASKED_NAME = "•••••"

/** [name] as lists show it: masked while names are hidden. */
@Composable
@ReadOnlyComposable
internal fun maskedName(name: String): String = if (LocalMaskNames.current) MASKED_NAME else name
