@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun TandemSystemBars(
    isDark: Boolean,
    background: Color,
) = Unit

@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun TandemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = Unit
