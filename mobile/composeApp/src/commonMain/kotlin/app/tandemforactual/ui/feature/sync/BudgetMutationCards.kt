@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import app.tandemforactual.ui.core.session.budgetMutationFailureMessage
import app.tandemforactual.ui.core.session.isRejectedBudgetOrderRequest
import app.tandemforactual.ui.core.ui.SyncStatusRing
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.LabelText
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.CloudAlert
import com.composables.icons.lucide.Lucide

@Composable
internal fun ReviewOutcomeCard(
    title: String,
    explanation: String,
    isRejected: Boolean,
) {
    val unknown = !isRejected && (title.startsWith("Status") || title.startsWith("Couldn"))
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
        SyncPageHeading(
            eyebrow =
                when {
                    isRejected -> "Action required"
                    unknown -> "Verify sync status"
                    else -> "Sync"
                },
            title = "${if (title == "Rejected") "Change rejected" else title}.",
            detail = explanation,
        )
        if (unknown) {
            SyncStatusRing(icon = Lucide.CloudAlert, color = tandemPalette.syncing, active = false, caption = null)
        }
    }
}

@Composable
internal fun BudgetMutationChangeDetails(
    title: String,
    detail: String?,
    amountChanges: List<BudgetAmountChange> = emptyList(),
) {
    SurfaceCard {
        Column(
            Modifier.fillMaxWidth().padding(TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            if (amountChanges.isNotEmpty()) {
                amountChanges.forEach { change ->
                    AmountTransitionLine(change)
                }
            } else {
                Text(title, color = tandemPalette.text, fontSize = TandemTypography.cardTitle, fontWeight = FontWeight.SemiBold)
                detail?.let { Text(it, color = tandemPalette.muted, fontSize = TandemTypography.body) }
            }
        }
    }
}

@Composable
internal fun BudgetMutationReasonCard(
    title: String,
    explanation: String,
) {
    SurfaceCard {
        Column(
            Modifier.fillMaxWidth().padding(TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            LabelText(title)
            Text(explanation, color = tandemPalette.text, fontSize = TandemTypography.body, lineHeight = TandemTypography.bodyLineHeight)
        }
    }
}

internal fun budgetMutationReason(
    operation: OutboxOperationEntity,
    mutation: QueuedBudgetReorder?,
    itemName: String,
): String =
    when {
        operation.status == "rejected" && isRejectedBudgetOrderRequest(operation.lastErrorCode, mutation?.kind) -> {
            budgetMutationFailureMessage(operation.lastErrorCode, mutation?.kind, itemName)
        }

        operation.status == "rejected" &&
            operation.lastErrorCode == "invalid_request" &&
            mutation?.kind == BudgetReorderKind.SET_CATEGORY_AMOUNT -> {
            "Tandem searched hidden categories only, so it could not find $itemName. It stopped the request before sending it to Actual; your assignment did not change."
        }

        operation.status == "rejected" -> {
            budgetMutationFailureMessage(operation.lastErrorCode, mutation?.kind, itemName)
        }

        operation.status == "uncertain" -> {
            "Tandem sent this change but did not receive confirmation. It may already be saved in Actual, so check your budget before trying again."
        }

        operation.status == "conflict" -> {
            "The budget changed while this request was being processed. Refresh the budget and review the latest values before choosing what to do."
        }

        operation.status == "queued" -> {
            "This change is saved on this device and is waiting to be sent to Actual."
        }

        operation.status == "sending" -> {
            "Tandem is sending this change to Actual."
        }

        else -> {
            "Tandem could not confirm the result of this change. Check Actual before trying again."
        }
    }
