package app.tandemforactual.android.tasker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import app.tandemforactual.android.MainActivity
import app.tandemforactual.android.R
import app.tandemforactual.ui.core.automation.AutomatedTransaction
import app.tandemforactual.ui.core.automation.AutomationTarget
import app.tandemforactual.ui.core.automation.AutomationUndo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Every transaction an automation app adds gets its own notification with Undo. */
internal object AutomationNotifications {
    private const val CHANNEL_ID = "tandem-automation"
    const val EXTRA_BUDGET = "budget"
    const val EXTRA_ENCRYPTED = "encrypted"
    const val EXTRA_TRANSACTION = "transaction"
    const val EXTRA_NOTIFICATION = "notification"

    fun added(
        context: Context,
        target: AutomationTarget,
        added: AutomatedTransaction,
        source: String,
    ) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Added by automation apps", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Each transaction an app such as Tasker adds, with Undo"
            },
        )
        val id = added.transactionId.hashCode()
        val undo =
            Intent(appContext, AutomationUndoReceiver::class.java)
                .putExtra(EXTRA_BUDGET, target.budgetId)
                .putExtra(EXTRA_ENCRYPTED, target.encrypted)
                .putExtra(EXTRA_TRANSACTION, added.transactionId)
                .putExtra(EXTRA_NOTIFICATION, id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open =
            Intent(appContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val notification =
            Notification
                .Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tandem_notification)
                .setContentTitle(added.summary)
                .setContentText("Added by $source. Undo removes it.")
                .setContentIntent(PendingIntent.getActivity(appContext, id, open, flags))
                .addAction(Notification.Action.Builder(null, "Undo", PendingIntent.getBroadcast(appContext, id, undo, flags)).build())
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(
                    Notification
                        .Builder(appContext, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_tandem_notification)
                        .setContentTitle("Transaction added")
                        .setContentText("Unlock to see the details.")
                        .build(),
                ).build()
        manager.notify(id, notification)
    }
}

/** Takes back an automated transaction from its notification. */
class AutomationUndoReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val budgetId = intent.getStringExtra(AutomationNotifications.EXTRA_BUDGET) ?: return
        val transactionId = intent.getStringExtra(AutomationNotifications.EXTRA_TRANSACTION) ?: return
        val target = AutomationTarget(budgetId, intent.getBooleanExtra(AutomationNotifications.EXTRA_ENCRYPTED, false))
        val appContext = context.applicationContext
        appContext
            .getSystemService(
                NotificationManager::class.java,
            )?.cancel(intent.getIntExtra(AutomationNotifications.EXTRA_NOTIFICATION, 0))
        val pending = goAsync()
        scope.launch {
            val message =
                when (runCatching { AutomationCalls.undo(appContext, target, transactionId) }.getOrNull()) {
                    AutomationUndo.REMOVED -> "Transaction removed"
                    AutomationUndo.DELETING -> "Transaction removed. Tandem deletes it in Actual on the next sync."
                    else -> "Open Tandem to remove this transaction. It changed after it was added."
                }
            withContext(Dispatchers.Main) { Toast.makeText(appContext, message, Toast.LENGTH_LONG).show() }
            pending.finish()
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
