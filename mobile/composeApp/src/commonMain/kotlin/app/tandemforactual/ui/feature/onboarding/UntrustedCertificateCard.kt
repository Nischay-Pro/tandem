@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import app.tandemforactual.api.client.PresentedCertificate
import app.tandemforactual.api.client.TrustedCertificates
import app.tandemforactual.ui.core.format.parseServerHeaders
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.loadActualLoginMethods
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.setup_certificate_check
import app.tandemforactual.ui.generated.resources.setup_certificate_fingerprint
import app.tandemforactual.ui.generated.resources.setup_certificate_trust
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ShieldCheck
import io.ktor.http.Url
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * After this server's certificate was refused, its fingerprint to compare with the one the server shows, and a way to
 * trust it for this server only and try again.
 */
@Composable
internal fun UntrustedCertificateCard(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val refused = refusedCertificateFor(state.serverUrl) ?: return
    val scope = rememberCoroutineScope()
    val palette = tandemPalette
    SurfaceCard {
        Column(Modifier.fillMaxWidth().padding(TandemSpacing.medium), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            Text(
                stringResource(Res.string.setup_certificate_check, refused.host),
                color = palette.text,
                fontSize = TandemTypography.compactBody,
            )
            Text(stringResource(Res.string.setup_certificate_fingerprint), color = palette.muted, fontSize = TandemTypography.caption)
            Text(refused.fingerprint, color = palette.text, fontSize = TandemTypography.caption, fontFamily = FontFamily.Monospace)
            TandemActionButton(
                stringResource(Res.string.setup_certificate_trust),
                TandemActionButtonTone.NEUTRAL,
                Modifier.fillMaxWidth(),
                icon = Lucide.ShieldCheck,
            ) {
                state.trustedCertificates = trustedWith(state.trustedCertificates, refused)
                scope.launch {
                    mobileSession.loadActualLoginMethods(state.serverUrl, parseServerHeaders(state.serverHeaders).getOrDefault(emptyMap()))
                }
            }
        }
    }
}

/** The certificate last refused, when it came from the server at [serverUrl]. */
internal fun refusedCertificateFor(serverUrl: String): PresentedCertificate? {
    val host = runCatching { Url(serverUrl).host }.getOrNull()?.lowercase() ?: return null
    return TrustedCertificates.lastRefused?.takeIf { it.host.lowercase() == host }
}

/** [saved] trusted certificates with [certificate] added, in the form they're kept in. */
internal fun trustedWith(
    saved: String,
    certificate: PresentedCertificate,
): String {
    TrustedCertificates.restore(saved)
    TrustedCertificates.trust(certificate)
    return TrustedCertificates.encode()
}
