package app.tandemforactual.ui

import app.tandemforactual.ui.core.platform.IosBackgroundSyncHandle
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertTrue

class IosBackgroundSyncHandleTest {
    @Test
    fun cancelStopsTheBackgroundSyncJob() {
        val job = Job()
        val handle = IosBackgroundSyncHandle(job)

        assertTrue(job.isActive)

        handle.cancel()

        assertTrue(job.isCancelled)
    }
}
