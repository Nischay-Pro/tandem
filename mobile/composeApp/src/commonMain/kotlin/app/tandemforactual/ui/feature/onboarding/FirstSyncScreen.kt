@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.onboarding
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.ui.ExternalConnectionsSection
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.buttons.SetupPrimaryButton
import app.tandemforactual.ui.design.feedback.AnimatedStatusText
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.feedback.TandemLoadingIndicator
import app.tandemforactual.ui.design.templates.OnboardingLayout
import app.tandemforactual.ui.design.templates.SetupHeadline
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.setup_cancel
import app.tandemforactual.ui.generated.resources.setup_complete_offline_copy_saved
import app.tandemforactual.ui.generated.resources.setup_continue
import app.tandemforactual.ui.generated.resources.setup_couldn_t_save_a_complete
import app.tandemforactual.ui.generated.resources.setup_downloading_a_local_copy_so
import app.tandemforactual.ui.generated.resources.setup_first_sync_complete
import app.tandemforactual.ui.generated.resources.setup_first_sync_needs_attention
import app.tandemforactual.ui.generated.resources.setup_keep_tandem_open_for_the
import app.tandemforactual.ui.generated.resources.setup_making_it_yours
import app.tandemforactual.ui.generated.resources.setup_open_budget
import app.tandemforactual.ui.generated.resources.setup_open_saved_copy
import app.tandemforactual.ui.generated.resources.setup_preparing_your_offline_copy
import app.tandemforactual.ui.generated.resources.setup_saving_your_budget_for_offline
import app.tandemforactual.ui.generated.resources.setup_transactions_saved
import app.tandemforactual.ui.generated.resources.setup_try_again
import app.tandemforactual.ui.generated.resources.setup_your_last_saved_copy_is
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FirstSyncScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onRetrySync: () -> Unit,
    notificationPermissionEnabled: Boolean?,
) {
    OnboardingLayout(
        content = {
            SetupStepHeader(step = 4, navigation = navigation)
            SetupHeadline(stringResource(Res.string.setup_making_it_yours), stringResource(Res.string.setup_downloading_a_local_copy_so))
            FirstSyncStatusBadge(mobileSession)
            FirstSyncStatusText(state, mobileSession, navigation)
            ExternalConnectionsSection(state)
        },
        actions = { FirstSyncActions(state, mobileSession, navigation, onRetrySync, notificationPermissionEnabled) },
    )
}

/** A check, warning or spinner showing where the first sync is. */
@Composable
private fun ColumnScope.FirstSyncStatusBadge(mobileSession: TandemMobileSession) {
    val palette = tandemPalette
    Box(
        Modifier
            .align(Alignment.CenterHorizontally)
            .size(136.dp)
            .clip(CircleShape)
            .background(palette.surface)
            .border(1.dp, palette.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            mobileSession.syncComplete ->
                Icon(
                    Lucide.Check,
                    contentDescription = stringResource(Res.string.setup_first_sync_complete),
                    modifier = Modifier.size(42.dp),
                    tint = palette.positive,
                )
            mobileSession.syncError != null && !mobileSession.isSyncing ->
                Icon(
                    Lucide.CircleAlert,
                    contentDescription = stringResource(Res.string.setup_first_sync_needs_attention),
                    modifier = Modifier.size(42.dp),
                    tint = palette.warning,
                )
            else -> TandemLoadingIndicator(Modifier.size(42.dp), palette.accent, 3.dp)
        }
    }
}

/** What the first sync is doing, and a way to open the saved copy when it fails. */
@Composable
private fun FirstSyncStatusText(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val palette = tandemPalette
    val syncMessage =
        when {
            mobileSession.isSyncing -> stringResource(Res.string.setup_saving_your_budget_for_offline)
            mobileSession.syncComplete -> stringResource(Res.string.setup_complete_offline_copy_saved)
            mobileSession.syncError != null -> stringResource(Res.string.setup_couldn_t_save_a_complete)
            else -> stringResource(Res.string.setup_preparing_your_offline_copy)
        }
    val syncMessageColor by
        animateColorAsState(
            targetValue = if (mobileSession.syncError != null && !mobileSession.isSyncing) palette.warning else palette.muted,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "first-sync-status-color",
        )
    AnimatedStatusText(
        syncMessage,
        syncMessageColor,
        fontSize = TandemTypography.compactBody,
        lineHeight = TandemTypography.bodyLineHeight,
        animateSizeChanges = true,
    )
    if (mobileSession.syncComplete) {
        BodyText(
            pluralStringResource(
                Res.plurals.setup_transactions_saved,
                mobileSession.syncedTransactionCount,
                mobileSession.syncedTransactionCount,
            ),
        )
    }
    if (mobileSession.syncError != null && !mobileSession.isSyncing) {
        InlineMessage(mobileSession.syncError.orEmpty(), InlineMessageKind.ERROR)
        if (mobileSession.cachedSnapshotBudgetId == state.selectedBudgetId &&
            mobileSession.hasCachedSnapshot &&
            !state.selectedBudgetEncrypted
        ) {
            BodyText(stringResource(Res.string.setup_your_last_saved_copy_is))
            SecondaryButton(stringResource(Res.string.setup_open_saved_copy)) {
                state.pendingBudgetSelectionOrigin = null
                selectLatestCachedMonth(state, mobileSession.budgetMonthIds, mobileSession.transactionRecords)
                navigation.selectTab(AppRoute.HOME)
            }
        }
    }
}

/** Continue once synced, retry or cancel on failure, or a note to keep the app open. */
@Composable
private fun FirstSyncActions(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    onRetrySync: () -> Unit,
    notificationPermissionEnabled: Boolean?,
) {
    val palette = tandemPalette
    when {
        mobileSession.syncComplete ->
            FirstSyncCompletionAction(
                state = state,
                navigation = navigation,
                syncComplete = true,
                notificationPermissionEnabled = notificationPermissionEnabled,
                actionLabel =
                    if (shouldShowNotificationOnboarding(
                            state.hasCompletedNotificationOnboarding,
                            notificationPermissionEnabled,
                        )
                    ) {
                        stringResource(Res.string.setup_continue)
                    } else {
                        stringResource(Res.string.setup_open_budget)
                    },
                onComplete = {
                    selectLatestCachedMonth(state, mobileSession.budgetMonthIds, mobileSession.transactionRecords)
                },
            )
        mobileSession.syncError != null && !mobileSession.isSyncing ->
            Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                SecondaryButton(stringResource(Res.string.setup_cancel), modifier = Modifier.weight(1f), onClick = navigation.back)
                SetupPrimaryButton(
                    stringResource(Res.string.setup_try_again),
                    modifier = Modifier.weight(1f),
                    onClick = onRetrySync,
                )
            }
        else ->
            Text(
                stringResource(Res.string.setup_keep_tandem_open_for_the),
                modifier = Modifier.fillMaxWidth(),
                color = palette.muted,
                fontSize = TandemTypography.caption,
                textAlign = TextAlign.Center,
            )
    }
}
