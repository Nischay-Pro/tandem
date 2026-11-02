@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.runtime.Composable

@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun TandemSystemBars(isDark: Boolean) = Unit

@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun TandemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = Unit
