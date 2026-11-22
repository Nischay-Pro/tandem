@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemPalette

@Composable
internal fun SetupHeadline(
    title: String,
    supportingText: String,
) {
    val palette = tandemPalette
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
        DisplayHeadline(title)
        HeadlineDetail(supportingText)
    }
}
