package app.tandemforactual.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.ui.TandemApp
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
  private val notificationPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
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
    BudgetSystemNotifications.initialize(applicationContext)
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
        onRequestNotificationPermission = { requestNotificationPermission() },
        onOpenNotificationSettings = { openNotificationSettings() },
        appResumeEvents = appResumeEvents,
        networkRestoredEvents = networkRestoredEvents,
      )
    }
  }

  private fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  private fun openNotificationSettings() {
    val intent =
      Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
      }
    startActivity(intent)
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
