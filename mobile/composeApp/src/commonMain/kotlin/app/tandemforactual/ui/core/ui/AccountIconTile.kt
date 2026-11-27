@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import app.tandemforactual.ui.design.icons.logoTileColor
import app.tandemforactual.ui.design.icons.rememberMerchantArtwork
import app.tandemforactual.ui.design.rows.LocalRowLogos
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.tandemPalette

/**
 * An account's icon as every screen shows it: the bank's own icon, else its logo from the icon pack, else the icon for
 * its account type.
 */
@Composable
internal fun AccountIconTile(
    accountName: String,
    accountType: String? = null,
    modifier: Modifier = Modifier,
) {
    val logos = LocalRowLogos.current
    val bankIcon = logos?.bankIcon?.invoke(accountName)
    val packLogo = rememberMerchantArtwork(logos?.account?.invoke(accountName))
    val artwork = bankIcon ?: packLogo
    Box(
        modifier
            .size(TandemLayout.listRowLeadingSize)
            .clip(RoundedCornerShape(TandemShapes.medium))
            .background(logoTileColor(artwork, tandemPalette.raised)),
        contentAlignment = Alignment.Center,
    ) {
        if (artwork != null) {
            Image(
                artwork,
                contentDescription = null,
                modifier = Modifier.size(TandemLayout.listRowLogoSize),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(
                accountTypeIcon(accountName, accountType),
                contentDescription = null,
                modifier = Modifier.size(TandemLayout.listRowIconSize),
                tint = tandemPalette.accent,
            )
        }
    }
}
