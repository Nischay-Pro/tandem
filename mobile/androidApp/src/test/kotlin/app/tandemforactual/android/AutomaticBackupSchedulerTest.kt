package app.tandemforactual.android

import androidx.work.NetworkType
import app.tandemforactual.ui.core.session.SyncNetwork
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AutomaticBackupSchedulerTest {
    @Test
    fun folderBackupsRunWithoutANetwork() {
        val request = AutomaticBackupScheduler.periodicRequest()

        assertEquals(NetworkType.NOT_REQUIRED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(TimeUnit.HOURS.toMillis(1), request.workSpec.intervalDuration)
    }

    @Test
    fun wifiOnlyWebDavUploadsWaitForAnUnmeteredNetwork() {
        val request = AutomaticBackupScheduler.uploadRequest(SyncNetwork.WIFI)

        assertEquals(NetworkType.UNMETERED, request.workSpec.constraints.requiredNetworkType)
        assertFalse(request.workSpec.isPeriodic)
    }

    @Test
    fun wifiOrMobileDataWebDavUploadsNeedAnyConnection() {
        val request = AutomaticBackupScheduler.uploadRequest(SyncNetwork.ANY)

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(NetworkType.CONNECTED, AutomaticBackupScheduler.networkType(SyncNetwork.MOBILE_DATA))
    }

    @Test
    fun aQueuedUploadKeepsTheNewestBackupUntilCleared() {
        val pending = PendingWebDavUpload(createTempDirectory().toFile().resolve("upload"))
        assertNull(pending.read())

        pending.write(PendingWebDavUpload.Upload("old.zip", "b1", "Home"), byteArrayOf(1))
        pending.write(PendingWebDavUpload.Upload("new.tandembackup", "b1", "Home\nand away"), byteArrayOf(2, 3))

        assertEquals(PendingWebDavUpload.Upload("new.tandembackup", "b1", "Home\nand away"), pending.read())
        assertArrayEquals(byteArrayOf(2, 3), pending.content())
        pending.clear()
        assertNull(pending.read())
    }
}
