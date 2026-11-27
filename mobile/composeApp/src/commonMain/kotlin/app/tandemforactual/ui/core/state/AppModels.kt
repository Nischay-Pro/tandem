@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.state
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

internal const val SUPPORTED_ACTUAL_SERVER_VERSION = "26.11.0"

internal data class AccountActivitySnapshot(
    val transactions: List<TransactionItem>,
    val offset: Int,
    val hasMore: Boolean,
)

internal val LocalRouteScrollState = staticCompositionLocalOf<ScrollState?> { null }

internal val LocalRouteLazyListState = staticCompositionLocalOf<LazyListState?> { null }

/** The route shown in the detail pane beside a list, so the list can mark its row. */
internal val LocalPaneDetailRoute = compositionLocalOf<AppRoute?> { null }
