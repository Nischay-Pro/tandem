package app.tandemforactual.android

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetSystemNotificationsTest {
    private val notificationIds = listOf(4101, 4102, 4103)

    @After
    fun clearNotifications() {
        notificationManager().cancelAll()
    }

    @Test
    fun backgroundSyncAlertsUseTheBudgetActivityChannel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assumeTrue(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        }

        BudgetSystemNotifications.initialize(context)
        BudgetSystemNotifications.syncFailed(context)
        BudgetSystemNotifications.conflicts(context, count = 2)
        BudgetSystemNotifications.rejected(context, count = 1)

        val active = notificationManager().activeNotifications.associateBy { it.id }
        assertEquals(notificationIds.toSet(), active.keys.intersect(notificationIds.toSet()))
        assertNotification(active.getValue(4101).notification, "Budget sync needs attention")
        assertNotification(active.getValue(4102).notification, "Budget changes need review")
        assertNotification(active.getValue(4103).notification, "An edit wasn't applied")
    }

    private fun notificationManager() =
        InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(NotificationManager::class.java)

    private fun assertNotification(
        notification: Notification,
        expectedTitle: String,
    ) {
        assertNotNull(notification)
        assertEquals("tandem-budget-alerts", notification.channelId)
        assertEquals(expectedTitle, notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }
}
