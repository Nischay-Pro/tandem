package app.tandemforactual.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat

/** Whether an activity is on screen, so background work only notifies when Tandem isn't open. */
internal object AppVisibility {
    @Volatile var visible = false
}

/** What kind of background work is running, each with its own notification channel. */
internal enum class BackgroundWork(
    val channelId: String,
    val channelName: String,
    val channelDescription: String,
    val notificationId: Int,
) {
    BACKUP("tandem-backups", "Backups", "Shows progress while an automatic backup runs in the background.", 4_201),
    SYNC("tandem-background-sync", "Background sync", "Shows while Tandem syncs your budget in the background.", 4_202),
}

/** An ongoing progress notification for work running while Tandem isn't open; nothing shows when the app is on screen. */
internal class BackgroundWorkProgress(
    private val context: Context,
    private val work: BackgroundWork,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    /** Shows [title] with a bar at [percent], or an indeterminate bar when it is null. */
    fun show(
        title: String,
        percent: Int? = null,
    ) {
        val manager = manager ?: return
        if (AppVisibility.visible || !manager.areNotificationsEnabled()) return clear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.createNotificationChannel(
            NotificationChannel(work.channelId, work.channelName, NotificationManager.IMPORTANCE_LOW).apply {
                description = work.channelDescription
            },
        )
        manager.notify(
            work.notificationId,
            NotificationCompat
                .Builder(context, work.channelId)
                .setSmallIcon(R.drawable.ic_tandem_notification)
                .setContentTitle(title)
                .setProgress(100, percent ?: 0, percent == null || percent == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build(),
        )
    }

    fun clear() {
        manager?.cancel(work.notificationId)
    }
}
