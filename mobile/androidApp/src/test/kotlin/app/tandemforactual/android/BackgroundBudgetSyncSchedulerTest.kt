package app.tandemforactual.android

import androidx.work.NetworkType
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundBudgetSyncSchedulerTest {
    @Test
    fun wifiOnlyUsesUnmeteredNetworkAndSixHourInterval() {
        val request = BackgroundBudgetSyncScheduler.periodicRequest(wifiOnly = true)

        assertEquals(NetworkType.UNMETERED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(TimeUnit.HOURS.toMillis(6), request.workSpec.intervalDuration)
    }

    @Test
    fun unrestrictedBackgroundSyncRequiresAConnectedNetwork() {
        val request = BackgroundBudgetSyncScheduler.periodicRequest(wifiOnly = false)

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }
}
