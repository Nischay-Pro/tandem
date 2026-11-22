@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette

/** Explanatory text at the top of a screen, kept in a card so it lines up with the sections below. */
@Composable
internal fun IntroCard(
    vararg paragraphs: String,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            paragraphs.forEach { paragraph ->
                Text(
                    paragraph,
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.compactBody,
                    lineHeight = TandemTypography.bodyLineHeight,
                )
            }
        }
    }
}
