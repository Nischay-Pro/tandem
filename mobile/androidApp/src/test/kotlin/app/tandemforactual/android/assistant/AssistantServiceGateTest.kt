package app.tandemforactual.android.assistant

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AssistantServiceGateTest {
    @Test
    @Config(sdk = [35])
    fun theServiceStaysOffBeforeAndroid16() {
        assertEquals(false, serviceEnabled())
    }

    @Test
    @Config(sdk = [37])
    fun theServiceIsOnFromAndroid16() {
        assertEquals(true, serviceEnabled())
    }

    private fun serviceEnabled(): Boolean {
        val context: Context = RuntimeEnvironment.getApplication()
        val component = ComponentName(context, "app.tandemforactual.android.assistant.TandemAppFunctionService")
        return context.packageManager.getServiceInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS).enabled
    }
}
