@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.theme.TandemSpacing

@Composable
internal fun UncertainWriteRecoveryActions(
    showCheckAgain: Boolean,
    showRetryAndRemove: Boolean,
    isSyncing: Boolean,
    readOnly: Boolean,
    onCheckAgain: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    when {
        showCheckAgain && showRetryAndRemove -> {
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                SecondaryButton(
                    "Check again",
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCheckAgain,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                    PrimaryButton(
                        "Try again",
                        enabled = !readOnly && !isSyncing,
                        modifier = Modifier.weight(1f),
                        onClick = onRetry,
                    )
                    SecondaryButton(
                        "Remove edit",
                        modifier = Modifier.weight(1f),
                        enabled = !readOnly && !isSyncing,
                        onClick = onRemove,
                    )
                }
            }
        }
        showCheckAgain ->
            PrimaryButton(
                "Check again",
                enabled = !isSyncing,
                loading = isSyncing,
                onClick = onCheckAgain,
            )
        showRetryAndRemove ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                PrimaryButton(
                    "Try again",
                    enabled = !readOnly && !isSyncing,
                    modifier = Modifier.weight(1f),
                    onClick = onRetry,
                )
                SecondaryButton(
                    "Remove edit",
                    modifier = Modifier.weight(1f),
                    enabled = !readOnly && !isSyncing,
                    onClick = onRemove,
                )
            }
    }
}
