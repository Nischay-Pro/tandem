@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import app.tandemforactual.api.client.BankIcon
import app.tandemforactual.api.client.BankIconFetcher
import app.tandemforactual.api.client.BankIconFormat
import app.tandemforactual.api.client.BankIcons
import app.tandemforactual.api.client.FileBankIconStore
import app.tandemforactual.ui.design.icons.LogoPainter
import app.tandemforactual.ui.design.icons.logoArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Bank icons from each bank's own website, set by the platform when it has somewhere to keep them. */
val LocalBankIcons = staticCompositionLocalOf<BankIcons?> { null }

/** Bank icons kept in [directory], downloaded with the platform's HTTP engine; SVG is skipped since it can't be drawn here. */
fun bankIcons(
    directory: String,
    now: () -> Long,
): BankIcons = BankIcons(FileBankIconStore(directory), BankIconFetcher.connect(BankIconFormat.entries.toSet() - BankIconFormat.SVG), now)

/**
 * The icon for a linked bank's domain while [download] is on: the cached copy straight away, and a download in the
 * background when there isn't one yet. Null when there is no icon, so callers fall back to the icon pack.
 */
@Composable
internal fun rememberBankIcon(
    bankId: String?,
    download: Boolean,
): LogoPainter? {
    val icons = LocalBankIcons.current
    val painter by produceState<LogoPainter?>(null, icons, bankId, download) {
        if (icons == null || bankId == null || !download) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.Default) { runCatching { icons.cached(bankId)?.painter() }.getOrNull() }
        if (value == null) {
            value = withContext(Dispatchers.Default) { runCatching { icons.icon(bankId)?.painter() }.getOrNull() }
        }
    }
    return painter
}

/** Deletes the downloaded bank icons whenever downloading them is off. */
@Composable
internal fun ForgetBankIconsWhenOff(enabled: Boolean) {
    val icons = LocalBankIcons.current
    LaunchedEffect(icons, enabled) {
        if (!enabled) withContext(Dispatchers.Default) { runCatching { icons?.clear() } }
    }
}

@OptIn(ExperimentalResourceApi::class)
private fun BankIcon.painter(): LogoPainter? =
    if (format ==
        BankIconFormat.SVG
    ) {
        null
    } else {
        LogoPainter(bytes.decodeToImageBitmap().logoArtwork())
    }
