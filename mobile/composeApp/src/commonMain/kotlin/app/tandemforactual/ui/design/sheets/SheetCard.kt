@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemPalette

internal val SHEET_CARD_MAX_WIDTH = 440.dp
internal const val SHEET_CARD_BODY_TAG = "sheet-card-body"

/**
 * The card inside a task sheet or dialog. Its width is capped, and in a short window the [body] shrinks and scrolls
 * while the [header] and [actions] stay in view. Pass [scrollBody] false when the body holds its own scrolling list,
 * and give that list `Modifier.weight(1f, fill = false)` so it shrinks instead.
 */
@Composable
internal fun SheetCard(
    modifier: Modifier = Modifier,
    maxWidth: Dp = SHEET_CARD_MAX_WIDTH,
    spacing: Dp = TandemSpacing.medium,
    scrollBody: Boolean = true,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val divider = tandemPalette.border
    SurfaceCard(
        modifier
            .padding(horizontal = TandemSpacing.large)
            .wrapContentWidth()
            .widthIn(max = maxWidth)
            .fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            header?.let { SheetCardSection(spacing, Modifier, it) }
            SheetCardSection(
                spacing,
                Modifier
                    .weight(1f, fill = false)
                    .testTag(SHEET_CARD_BODY_TAG)
                    .then(if (scrollBody) Modifier.verticalScroll(scroll) else Modifier),
                body,
            )
            actions?.let {
                SheetCardSection(
                    spacing,
                    Modifier.drawBehind {
                        if (scroll.canScrollForward) {
                            val y = -spacing.toPx() / 2
                            drawLine(divider, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        }
                    },
                    it,
                )
            }
        }
    }
}

@Composable
private fun SheetCardSection(
    spacing: Dp,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}
