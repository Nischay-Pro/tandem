package app.tandemforactual.android

import androidx.work.NetworkType
import app.tandemforactual.ui.core.session.SyncNetwork
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomaticBackupSchedulerTest {
    @Test
    fun wifiOnlyBackupsWaitForAnUnmeteredNetwork() {
        val request = AutomaticBackupScheduler.periodicRequest(SyncNetwork.WIFI)

        assertEquals(NetworkType.UNMETERED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(TimeUnit.HOURS.toMillis(1), request.workSpec.intervalDuration)
    }

    @Test
    fun wifiOrMobileDataBackupsNeedAnyConnection() {
        val request = AutomaticBackupScheduler.periodicRequest(SyncNetwork.ANY)

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(NetworkType.CONNECTED, AutomaticBackupScheduler.networkType(SyncNetwork.MOBILE_DATA))
    }
}
