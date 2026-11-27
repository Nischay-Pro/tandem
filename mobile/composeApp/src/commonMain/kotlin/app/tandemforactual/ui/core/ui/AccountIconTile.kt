@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.AccountKind
import app.tandemforactual.data.planning.CardNetwork
import app.tandemforactual.ui.design.icons.logoTileColor
import app.tandemforactual.ui.design.icons.rememberMerchantArtwork
import app.tandemforactual.ui.design.rows.LocalRowLogos
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.tandemPalette

/**
 * An account's icon as every screen shows it: the bank's own icon, else its logo from the icon pack, else the icon for
 * its account type. A credit card's network, when the person picked one, sits on its corner.
 */
@Composable
internal fun AccountIconTile(
    accountName: String,
    kind: AccountKind? = null,
    modifier: Modifier = Modifier,
    network: CardNetwork? = null,
) {
    val logos = LocalRowLogos.current
    val bankIcon = logos?.bankIcon?.invoke(accountName)
    val packLogo = rememberMerchantArtwork(logos?.account?.invoke(accountName))
    val artwork = bankIcon ?: packLogo
    val badge = rememberMerchantArtwork(network?.let { logos?.bySlug?.invoke(it.iconSlug) })
    Box(modifier.size(TandemLayout.listRowLeadingSize)) {
        Box(
            Modifier
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
                    accountTypeIcon(accountName, kind),
                    contentDescription = null,
                    modifier = Modifier.size(TandemLayout.listRowIconSize),
                    tint = tandemPalette.accent,
                )
            }
        }
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(TandemShapes.small))
                    .background(logoTileColor(badge, tandemPalette.raised))
                    .border(1.dp, tandemPalette.background, RoundedCornerShape(TandemShapes.small)),
                contentAlignment = Alignment.Center,
            ) {
                Image(badge, contentDescription = network?.label, modifier = Modifier.size(14.dp), contentScale = ContentScale.Fit)
            }
        }
    }
}
