package app.tandemforactual.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import app.tandemforactual.ui.TandemApp
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
  private var appStartupComplete = false
  private var tandemDatabase: TandemDatabase? = null
  private var notificationPermissionEnabled by mutableStateOf(false)
  private val notificationPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      notificationPermissionEnabled = granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    }
  private val appResumeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private val networkRestoredEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private val syncSignalState = SyncSignalState<Network>()
  private var networkCallbackRegistered = false
  private val connectivityManager by lazy {
    getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  }
  private val networkCallback =
    object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        if (syncSignalState.onNetworkAvailable(network)) networkRestoredEvents.tryEmit(Unit)
      }

      override fun onLost(network: Network) {
        syncSignalState.onNetworkLost(network)
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen: SplashScreen = installSplashScreen()
    val splashDeadline = SystemClock.uptimeMillis() + MAX_SYSTEM_SPLASH_WAIT_MILLIS
    splashScreen.setKeepOnScreenCondition {
      !appStartupComplete && SystemClock.uptimeMillis() < splashDeadline
    }
    super.onCreate(savedInstanceState)
    notificationPermissionEnabled = hasNotificationPermission()
    BudgetSystemNotifications.initialize(applicationContext)
    val database = openTandemDatabase(tandemDatabaseBuilder(applicationContext))
    tandemDatabase = database
    setContent {
      val onBackgroundSyncSettingsChanged: (Boolean, Boolean, String) -> Unit = remember(applicationContext) {
        { enabled, unmeteredOnly, budgetId ->
          BackgroundBudgetSyncScheduler.update(applicationContext, enabled, unmeteredOnly, budgetId)
        }
      }
      TandemApp(
        deviceName =
          Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "Android device",
        database = database,
        deviceCredentialStore = AndroidDeviceCredentialStore(applicationContext),
        nowEpochMillis = { System.currentTimeMillis() },
        allowInsecurePrivateNetwork = true,
        onBackgroundSyncSettingsChanged = onBackgroundSyncSettingsChanged,
        onRequestNotificationPermission = { requestNotificationPermission() },
        onOpenNotificationSettings = { openNotificationSettings() },
        notificationPermissionEnabled = notificationPermissionEnabled,
        onToggleNotificationPermission = { updateNotificationPermission(it) },
        onStartupComplete = {
          appStartupComplete = true
        },
        appResumeEvents = appResumeEvents,
        networkRestoredEvents = networkRestoredEvents,
      )
    }
  }

  private fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      notificationPermissionEnabled = true
      return
    }
    if (hasNotificationPermission()) {
      notificationPermissionEnabled = true
      return
    }
    notificationPreferences().edit().putBoolean(NOTIFICATION_PERMISSION_PROMPTED, true).apply()
    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
  }

  private fun updateNotificationPermission(enabled: Boolean) {
    if (!enabled) {
      openNotificationSettings()
      return
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasNotificationPermission()) {
      notificationPermissionEnabled = true
      return
    }
    val permissionWasRequested = notificationPreferences().getBoolean(NOTIFICATION_PERMISSION_PROMPTED, false)
    if (permissionWasRequested && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
      openNotificationSettings()
    } else {
      requestNotificationPermission()
    }
  }

  private fun notificationPreferences() =
    getSharedPreferences(NOTIFICATION_PREFERENCES, MODE_PRIVATE)

  private fun hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

  override fun onResume() {
    super.onResume()
    notificationPermissionEnabled = hasNotificationPermission()
    if (syncSignalState.onActivityResumed()) appResumeEvents.tryEmit(Unit)
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
      syncSignalState.onNetworkStarted(connectivityManager.activeNetwork)
      connectivityManager.registerDefaultNetworkCallback(networkCallback)
      networkCallbackRegistered = true
    }
  }

  override fun onStop() {
    if (networkCallbackRegistered) {
      connectivityManager.unregisterNetworkCallback(networkCallback)
      networkCallbackRegistered = false
      syncSignalState.onActivityStopped()
    }
    super.onStop()
  }

  override fun onDestroy() {
    super.onDestroy()
    tandemDatabase?.close()
    tandemDatabase = null
  }
}

private const val MAX_SYSTEM_SPLASH_WAIT_MILLIS = 2_500L
private const val NOTIFICATION_PREFERENCES = "tandem_notification_permissions"
private const val NOTIFICATION_PERMISSION_PROMPTED = "prompted_once"
