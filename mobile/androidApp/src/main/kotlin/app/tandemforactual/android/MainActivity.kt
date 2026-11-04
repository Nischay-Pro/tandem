package app.tandemforactual.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.ui.TandemApp

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val openPreview = intent.getBooleanExtra("preview", false)
    val database = openTandemDatabase(tandemDatabaseBuilder(applicationContext))
    setContent {
      val onBackgroundSyncSettingsChanged: (Boolean, Boolean, String) -> Unit = remember(applicationContext) {
        { enabled, wifiOnly, budgetId ->
          BackgroundBudgetSyncScheduler.update(applicationContext, enabled, wifiOnly, budgetId)
        }
      }
      TandemApp(
        startInPreview = openPreview,
        database = database,
        deviceCredentialStore = if (openPreview) null else AndroidDeviceCredentialStore(applicationContext),
        nowEpochMillis = { System.currentTimeMillis() },
        allowInsecureLoopback = BuildConfig.DEBUG,
        onBackgroundSyncSettingsChanged = onBackgroundSyncSettingsChanged,
      )
    }
  }
}
