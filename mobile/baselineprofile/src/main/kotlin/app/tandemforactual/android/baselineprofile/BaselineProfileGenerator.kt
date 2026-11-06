package app.tandemforactual.android.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun coldStart() {
        baselineProfileRule.collect(
            packageName = PROFILE_PACKAGE,
            includeInStartupProfile = true,
        ) {
            launchPreview()
        }
    }

    @Test
    fun primaryTabs() {
        baselineProfileRule.collect(
            packageName = PROFILE_PACKAGE,
            includeInStartupProfile = false,
        ) {
            launchPreview()
            listOf("Accounts", "Activity", "Settings", "Budget").forEach { tab ->
                selectTab(tab)
            }
            scrollMainContent()
        }
    }

    private fun MacrobenchmarkScope.launchPreview() {
        pressHome()
        // Preview mode skips device credentials and auto-sync, so profiling cannot
        // transmit queued local edits while it exercises the budget UI.
        device.executeShellCommand("am force-stop $PROFILE_PACKAGE")
        device.executeShellCommand("am start -W -n $PROFILE_PACKAGE/$TANDEM_ACTIVITY --ez preview true")
        check(device.wait(Until.hasObject(By.desc("Budget")), CONTENT_TIMEOUT_MS)) {
            "Tandem did not open its budget preview."
        }
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.selectTab(label: String) {
        check(device.wait(Until.hasObject(By.desc(label)), CONTENT_TIMEOUT_MS)) {
            "Could not find the $label navigation tab after opening the cached budget."
        }
        device.findObject(By.desc(label)).click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.scrollMainContent() {
        val x = device.displayWidth / 2
        device.swipe(
            x,
            device.displayHeight * 3 / 4,
            x,
            device.displayHeight / 3,
            12,
        )
        device.waitForIdle()
    }

    private companion object {
        const val PROFILE_PACKAGE = "app.tandemforactual.android.profile"
        const val TANDEM_ACTIVITY = "app.tandemforactual.android.MainActivity"
        const val CONTENT_TIMEOUT_MS = 10_000L
    }
}
