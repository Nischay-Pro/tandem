package app.tandemforactual.android

import androidx.work.NetworkType
import app.tandemforactual.ui.core.session.SyncNetwork
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundBudgetSyncSchedulerTest {
    @Test
    fun unmeteredOnlyUsesUnmeteredNetworkAndSixHourInterval() {
        val request = BackgroundBudgetSyncScheduler.periodicRequest(SyncNetwork.WIFI)

        assertEquals(NetworkType.UNMETERED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(TimeUnit.HOURS.toMillis(6), request.workSpec.intervalDuration)
    }

    @Test
    fun unrestrictedBackgroundSyncRequiresAConnectedNetwork() {
        val request = BackgroundBudgetSyncScheduler.periodicRequest(SyncNetwork.ANY)

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun mobileDataOnlyRequiresAMeteredNetwork() {
        val request = BackgroundBudgetSyncScheduler.periodicRequest(SyncNetwork.MOBILE_DATA)

        assertEquals(NetworkType.METERED, request.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun anAutomatedEntrySyncsOnceOverTheChosenNetworkEvenWithScheduledSyncOff() {
        val request = BackgroundBudgetSyncScheduler.afterAutomationRequest(SyncNetwork.WIFI)

        assertEquals(NetworkType.UNMETERED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(true, request.workSpec.input.getBoolean(BackgroundBudgetSyncScheduler.AFTER_AUTOMATION, false))
        assertEquals(false, request.workSpec.isPeriodic)
    }
}
