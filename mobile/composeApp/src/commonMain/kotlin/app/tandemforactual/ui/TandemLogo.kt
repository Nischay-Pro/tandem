@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.tandem_logo_squircle
import org.jetbrains.compose.resources.painterResource

/** Displays the canonical supplied Tandem squircle logo in shared UI. */
@Composable
internal fun TandemBrandMark(
    modifier: Modifier = Modifier,
    contentDescription: String = "Tandem",
) {
    Image(
        painter = painterResource(Res.drawable.tandem_logo_squircle),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
