package app.tandemforactual.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.ui.TandemApp
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
  private val appResumeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private val networkRestoredEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private var activeNetwork: Network? = null
  private var networkStateInitialized = false
  private var networkCallbackRegistered = false
  private var hasBeenResumed = false
  private val connectivityManager by lazy {
    getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  }
  private val networkCallback =
    object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        if (networkStateInitialized && activeNetwork == null) networkRestoredEvents.tryEmit(Unit)
        activeNetwork = network
        networkStateInitialized = true
      }

      override fun onLost(network: Network) {
        if (activeNetwork == network) activeNetwork = null
      }
    }

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
        appResumeEvents = appResumeEvents,
        networkRestoredEvents = networkRestoredEvents,
      )
    }
  }

  override fun onStart() {
    super.onStart()
    if (!networkCallbackRegistered) {
      activeNetwork = connectivityManager.activeNetwork
      networkStateInitialized = true
      connectivityManager.registerDefaultNetworkCallback(networkCallback)
      networkCallbackRegistered = true
    }
  }

  override fun onResume() {
    super.onResume()
    if (hasBeenResumed) appResumeEvents.tryEmit(Unit)
    hasBeenResumed = true
  }

  override fun onStop() {
    if (networkCallbackRegistered) {
      connectivityManager.unregisterNetworkCallback(networkCallback)
      networkCallbackRegistered = false
      activeNetwork = null
      networkStateInitialized = false
    }
    super.onStop()
  }
}
