@file:Suppress("ktlint:standard:function-naming")

package org.actualbudget.companion.ui

import androidx.compose.runtime.Composable

@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun CompanionSystemBars(isDark: Boolean) = Unit

@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun CompanionBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = Unit
