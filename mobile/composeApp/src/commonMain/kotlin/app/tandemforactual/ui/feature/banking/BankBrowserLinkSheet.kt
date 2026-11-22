@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.banking

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.data.regions.global.bank.BankInstitution
import app.tandemforactual.data.regions.global.bank.BankProviderAccount
import app.tandemforactual.data.regions.global.bank.BrowserBankProvider
import app.tandemforactual.data.regions.global.bank.BrowserLink
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide

/**
 * Links a bank that needs a browser sign-in, as Actual's web app does: choose the country and bank, sign in at the bank,
 * then come back and pick which of its accounts to link. The server keeps the provider's credentials throughout.
 */
@Composable
internal fun BankBrowserLinkSheet(
    provider: BrowserBankProvider?,
    mobileSession: TandemMobileSession,
    onDismiss: () -> Unit,
    onPickAccount: (BrowserBankProvider, BankProviderAccount) -> Unit,
) {
    var shown by remember { mutableStateOf(provider) }
    if (provider != null) shown = provider
    TandemTaskSheet(visible = provider != null, onDismissRequest = onDismiss, onDismissed = { shown = null }, fullScreen = true) {
        val active = shown ?: return@TandemTaskSheet
        var step by remember(active) { mutableStateOf<LinkStep>(LinkStep.Country) }
        var error by remember(active) { mutableStateOf<String?>(null) }
        Column(Modifier.fillMaxSize().background(tandemPalette.background).imePadding()) {
            TandemModalHeader(
                "Link with ${active.displayName}",
                onClose = onDismiss,
                modifier = Modifier.padding(horizontal = TandemSpacing.screen, vertical = TandemSpacing.medium),
                leadingIcon = Lucide.Landmark,
            )
            error?.let {
                InlineMessage(
                    it,
                    InlineMessageKind.ERROR,
                    Modifier.padding(horizontal = TandemSpacing.screen),
                    onDismiss = { error = null },
                )
            }
            val reduceMotion = tandemReduceMotion
            AnimatedContent(
                step,
                transitionSpec = {
                    fadeIn(tandemMotionSpec(reduceMotion, TandemMotion.CONTROL_MILLIS)) togetherWith
                        fadeOut(tandemMotionSpec(reduceMotion, TandemMotion.CONTROL_MILLIS))
                },
                contentKey = { it::class },
                label = "bank-link-step",
            ) { current ->
                when (current) {
                    LinkStep.Country -> CountryStep(active) { step = LinkStep.Banks(it) }
                    is LinkStep.Banks ->
                        BanksStep(active, current.country, mobileSession, onBack = { step = LinkStep.Country }) {
                            error = null
                            step = LinkStep.Waiting(it, null)
                        }
                    is LinkStep.Waiting ->
                        WaitingStep(
                            active,
                            current,
                            mobileSession,
                            onStarted = { step = current.copy(link = it) },
                            onLinked = { step = LinkStep.Accounts(current.institution, it) },
                            onFailed = {
                                error = it
                                step = LinkStep.Banks(current.institution.country)
                            },
                        )
                    is LinkStep.Accounts -> AccountsStep(current) { onPickAccount(active, it) }
                }
            }
        }
    }
}

internal sealed interface LinkStep {
    data object Country : LinkStep

    data class Banks(
        val country: String,
    ) : LinkStep

    data class Waiting(
        val institution: BankInstitution,
        val link: BrowserLink?,
    ) : LinkStep

    data class Accounts(
        val institution: BankInstitution,
        val accounts: List<BankProviderAccount>,
    ) : LinkStep
}
