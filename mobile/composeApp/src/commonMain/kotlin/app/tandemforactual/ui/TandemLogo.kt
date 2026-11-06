@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.tandem_mark
import app.tandemforactual.ui.generated.resources.tandem_mark_light
import org.jetbrains.compose.resources.painterResource

/** Displays the supplied Tandem mark in a squircle, choosing the variant for the current background. */
@Composable
internal fun TandemBrandMark(
    modifier: Modifier = Modifier,
    contentDescription: String = "Tandem",
) {
    val resource =
        if (tandemPalette.background.luminance() < 0.5f) {
            Res.drawable.tandem_mark
        } else {
            Res.drawable.tandem_mark_light
        }
    Image(
        painter = painterResource(resource),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
