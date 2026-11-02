package app.tandemforactual.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.ui.TandemApp

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val openPreview = intent.getBooleanExtra("preview", false)
    val database = openTandemDatabase(tandemDatabaseBuilder(applicationContext))
    setContent {
      TandemApp(
        startInPreview = openPreview,
        database = database,
        deviceCredentialStore = if (openPreview) null else AndroidDeviceCredentialStore(applicationContext),
        nowEpochMillis = { System.currentTimeMillis() },
        allowInsecureLoopback = BuildConfig.DEBUG,
      )
    }
  }
}
