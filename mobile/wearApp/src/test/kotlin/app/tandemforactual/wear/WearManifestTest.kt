package app.tandemforactual.wear

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import app.tandemforactual.wear.tiles.LeftToSpendTileService
import app.tandemforactual.wear.tiles.NextBillTileService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WearManifestTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun theWatchAppNeedsThePhoneApp() {
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)

        assertTrue(info.metaData.containsKey("com.google.android.wearable.standalone"))
        assertFalse(info.metaData.getBoolean("com.google.android.wearable.standalone", true))
    }

    @Test
    fun theWatchAppSharesThePhoneAppsId() {
        assertTrue(context.packageName.startsWith("app.tandemforactual.android"))
    }

    @Test
    fun tilesOnlyBindForTheSystem() {
        listOf(LeftToSpendTileService::class.java, NextBillTileService::class.java).forEach {
            val service = context.packageManager.getServiceInfo(ComponentName(context, it), 0)
            assertEquals("com.google.android.wearable.permission.BIND_TILE_PROVIDER", service.permission)
        }
    }

    @Test
    fun thePhoneCanReachTheDataService() {
        val intent =
            Intent("com.google.android.gms.wearable.DATA_CHANGED")
                .setData(Uri.parse("wear://phone/tandem/summary"))
                .setPackage(context.packageName)

        assertEquals(1, context.packageManager.queryIntentServices(intent, 0).size)
    }
}
