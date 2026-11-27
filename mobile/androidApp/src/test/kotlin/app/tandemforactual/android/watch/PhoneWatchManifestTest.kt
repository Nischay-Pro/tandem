package app.tandemforactual.android.watch

import android.content.Intent
import android.net.Uri
import app.tandemforactual.wearable.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PhoneWatchManifestTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun theWatchCanReachThePhonesListener() {
        val intent =
            Intent("com.google.android.gms.wearable.MESSAGE_RECEIVED")
                .setData(Uri.parse("wear://watch/tandem/quick-add"))
                .setPackage(context.packageName)

        val services = context.packageManager.queryIntentServices(intent, 0)

        assertEquals(listOf(PhoneWatchService::class.java.name), services.map { it.serviceInfo.name })
    }

    @Test
    fun thePhoneAdvertisesItselfToTheWatch() {
        val capabilities = context.resources.getStringArray(R.array.android_wear_capabilities)

        assertEquals(listOf("tandem_phone"), capabilities.toList())
    }
}
