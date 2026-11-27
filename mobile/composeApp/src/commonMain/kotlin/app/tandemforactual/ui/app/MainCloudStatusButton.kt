@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import app.tandemforactual.ui.core.state.MainCloudStatus
import app.tandemforactual.ui.core.state.mainCloudStatusAccessibilityDescription
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudAlert
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.Lucide

@Composable
internal fun MainCloudStatusButton(
    syncStatus: MainCloudStatus,
    pendingWriteStatuses: Collection<String>,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val cloudTint by
        animateColorAsState(
            targetValue =
                when (syncStatus) {
                    MainCloudStatus.OFF -> palette.muted
                    MainCloudStatus.SYNCED -> palette.positive
                    MainCloudStatus.SYNCING -> palette.syncing
                    MainCloudStatus.WAITING -> palette.muted
                    MainCloudStatus.ERROR -> palette.negative
                },
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "cloud-tint",
        )
    val breathing = syncStatus == MainCloudStatus.SYNCING && !tandemReduceMotion
    val breath by
        rememberInfiniteTransition(label = "cloud-breath").animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(800, easing = TandemMotion.movementEasing), RepeatMode.Reverse),
            label = "cloud-breath-phase",
        )
    val emphasis by animateFloatAsState(
        targetValue = if (breathing) breath else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cloud-breath-emphasis",
    )
    TandemIconButton(
        modifier =
            Modifier.graphicsLayer {
                alpha = 0.45f + 0.55f * emphasis
                scaleX = 0.9f + 0.1f * emphasis
                scaleY = 0.9f + 0.1f * emphasis
            },
        icon =
            when (syncStatus) {
                MainCloudStatus.OFF -> Lucide.CloudOff
                MainCloudStatus.ERROR -> Lucide.CloudAlert
                MainCloudStatus.WAITING -> Lucide.CloudUpload
                else -> Lucide.Cloud
            },
        contentDescription = mainCloudStatusAccessibilityDescription(syncStatus, pendingWriteStatuses),
        onClickLabel = "Open sync center",
        onClick = onClick,
        iconSizeDp = 25,
        tint = cloudTint,
    )
}
