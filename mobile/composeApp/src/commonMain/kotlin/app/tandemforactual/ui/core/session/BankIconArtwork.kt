package app.tandemforactual.ui.core.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import app.tandemforactual.api.client.BankIcon
import app.tandemforactual.api.client.BankIconFetcher
import app.tandemforactual.api.client.BankIconFormat
import app.tandemforactual.api.client.BankIcons
import app.tandemforactual.api.client.FileBankIconStore
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
 * The icon for a linked bank's domain: the cached copy straight away, and a download in the background when
 * [download] is on and there isn't one yet. Null when there is no icon, so callers fall back to the icon pack.
 */
@Composable
internal fun rememberBankIcon(
    bankId: String?,
    download: Boolean,
): Painter? {
    val icons = LocalBankIcons.current
    val painter by produceState<Painter?>(null, icons, bankId, download) {
        if (icons == null || bankId == null) return@produceState
        value = withContext(Dispatchers.Default) { runCatching { icons.cached(bankId)?.painter() }.getOrNull() }
        if (value == null && download) {
            value = withContext(Dispatchers.Default) { runCatching { icons.icon(bankId)?.painter() }.getOrNull() }
        }
    }
    return painter
}

@OptIn(ExperimentalResourceApi::class)
private fun BankIcon.painter(): Painter? = if (format == BankIconFormat.SVG) null else BitmapPainter(bytes.decodeToImageBitmap())
