package app.tandemforactual.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

internal object BudgetSystemNotifications {
    private const val CHANNEL_ID = "tandem-budget-alerts"
    private const val SYNC_FAILURE_ID = 4101
    private const val CONFLICT_ID = 4102
    private const val REJECTED_ID = 4103
    private const val SIGN_IN_REQUIRED_ID = 4104

    fun initialize(context: Context) {
        context.applicationContext.getSystemService(NotificationManager::class.java)?.let(::createChannel)
    }

    fun syncFailed(context: Context) {
        show(
            context,
            SYNC_FAILURE_ID,
            "Budget sync needs attention",
            "Tandem couldn’t refresh your budget. Open Tandem and try again.",
        )
    }

    fun signInRequired(context: Context) {
        show(
            context,
            SIGN_IN_REQUIRED_ID,
            "Sign in to Tandem",
            "Sign in again to sync this budget.",
        )
    }

    fun conflicts(context: Context, count: Int) {
        if (count <= 0) return
        val noun = if (count == 1) "budget change" else "budget changes"
        val verb = if (count == 1) "needs" else "need"
        show(
            context,
            CONFLICT_ID,
            "Needs review",
            "$count $noun $verb review in Tandem. Open Tandem to choose which version to keep.",
        )
    }

    fun rejected(context: Context, count: Int) {
        if (count <= 0) return
        val noun = if (count == 1) "edit" else "edits"
        val verb = if (count == 1) "needs" else "need"
        val review = if (count == 1) "Review the edit" else "Review the edits"
        show(
            context,
            REJECTED_ID,
            "Rejected",
            "$count rejected $noun $verb review. $review in Tandem.",
        )
    }

    private fun show(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
    ) {
        val applicationContext = context.applicationContext
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createChannel(manager)
        val openApp =
            PendingIntent.getActivity(
                applicationContext,
                notificationId,
                Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            Notification.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tandem_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(Notification.BigTextStyle().bigText(message))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
        manager.notify(notificationId, notification)
    }

    private fun createChannel(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Budget activity", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Background budget sync and review alerts"
            },
        )
    }
}
