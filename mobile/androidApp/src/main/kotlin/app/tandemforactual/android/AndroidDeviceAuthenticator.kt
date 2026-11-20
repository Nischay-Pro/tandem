package app.tandemforactual.android

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.tandemforactual.ui.DeviceAuthenticator

class AndroidDeviceAuthenticator(
  private val activity: FragmentActivity,
) : DeviceAuthenticator {
  override fun isAvailable(): Boolean =
    BiometricManager.from(activity).canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

  override fun authenticate(
    title: String,
    onResult: (Boolean) -> Unit,
  ) {
    if (activity.isFinishing || activity.supportFragmentManager.isStateSaved) {
      onResult(false)
      return
    }
    val prompt =
      BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onResult(true)
          }

          override fun onAuthenticationError(
            errorCode: Int,
            errString: CharSequence,
          ) {
            onResult(false)
          }
        },
      )
    prompt.authenticate(
      BiometricPrompt.PromptInfo
        .Builder()
        .setTitle(title)
        .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
        .build(),
    )
  }

  private companion object {
    const val ALLOWED_AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
  }
}
