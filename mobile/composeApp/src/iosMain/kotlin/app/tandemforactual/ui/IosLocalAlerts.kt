package app.tandemforactual.ui

import app.tandemforactual.data.store.LocalAlert
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/** Posts a policy-checked alert right away; each kind replaces its own earlier notification. */
internal fun postIosLocalAlert(alert: LocalAlert) {
    val content = UNMutableNotificationContent()
    content.setTitle(alert.title)
    content.setBody(alert.message)
    val request =
        UNNotificationRequest.requestWithIdentifier(
            "tandem-${alert.kind.name.lowercase()}",
            content,
            null,
        )
    UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, null)
}
