package app.tandemforactual.ui.core.session.schedules

import app.tandemforactual.data.database.OutboxOperationEntity

/** What a queued schedule action's status means, in words the person sees. */
internal fun scheduleActionStatusText(operation: OutboxOperationEntity): String {
    val actionLabel =
        when (operation.action) {
            "skip" -> "Skip"
            "post" -> "Post"
            "edit" -> "Edit"
            "create" -> "New schedule"
            "delete" -> "Delete"
            else -> "Schedule change"
        }
    return when (operation.status) {
        "queued" ->
            if (operation.lastErrorCode in setOf("invalid_device_credential", "sign_in_required", "sign_in_expired")) {
                "Sign in again to send this scheduled action to Actual."
            } else {
                "$actionLabel saved on this device. Waiting for a connection to Tandem."
            }
        "sending" -> "$actionLabel is being sent to Actual."
        "uncertain" -> "Tandem couldn’t confirm this $actionLabel. Check Actual, then check the status here."
        "conflict" -> "The schedule changed in Actual. Refresh it before choosing an action."
        "rejected" -> "Actual rejected this $actionLabel. Check the schedule in Actual."
        else -> ""
    }
}

internal fun scheduleActionStatusAction(operation: OutboxOperationEntity): String? =
    when {
        operation.status == "uncertain" -> "Check status"
        operation.status == "queued" && operation.attemptCount == 0 -> "Undo"
        operation.status == "queued" -> "Try again"
        else -> null
    }
