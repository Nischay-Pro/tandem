package app.tandemforactual.android

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import app.tandemforactual.ui.DeviceAuthenticator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

class AndroidDeviceAuthenticator(
  private val activity: FragmentActivity,
) : DeviceAuthenticator {
  private val secrets by lazy { activity.getSharedPreferences(SECRETS_FILE, Context.MODE_PRIVATE) }

  override fun isAvailable(): Boolean =
    BiometricManager.from(activity).canAuthenticate(APP_LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

  override fun canStoreSecrets(): Boolean =
    BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

  override fun authenticate(
    title: String,
    onResult: (Boolean) -> Unit,
  ) {
    val info =
      BiometricPrompt.PromptInfo
        .Builder()
        .setTitle(title)
        .setAllowedAuthenticators(APP_LOCK_AUTHENTICATORS)
        .build()
    prompt(info, null) { onResult(it != null) }
  }

  override fun hasStoredSecret(id: String): Boolean = secrets.contains(dataKey(id))

  override fun storeSecret(
    id: String,
    secret: String,
    title: String,
    onResult: (Boolean) -> Unit,
  ) {
    val cipher =
      cipherOrNull { Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) } }
        ?: return onResult(false)
    prompt(cryptoPromptInfo(title), BiometricPrompt.CryptoObject(cipher)) { result ->
      val authorized = result?.cryptoObject?.cipher ?: return@prompt onResult(false)
      val stored =
        runCatching {
          val encrypted = authorized.doFinal(secret.encodeToByteArray())
          secrets.edit {
            putString(dataKey(id), encode(encrypted))
            putString(ivKey(id), encode(authorized.iv))
          }
        }.isSuccess
      onResult(stored)
    }
  }

  override fun readSecret(
    id: String,
    title: String,
    onResult: (String?) -> Unit,
  ) {
    val data = secrets.getString(dataKey(id), null)
    val iv = secrets.getString(ivKey(id), null)
    if (data == null || iv == null) return onResult(null)
    val cipher =
      cipherOrNull {
        Cipher.getInstance(TRANSFORMATION).apply {
          init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, decode(iv)))
        }
      } ?: return onResult(null)
    prompt(cryptoPromptInfo(title), BiometricPrompt.CryptoObject(cipher)) { result ->
      val authorized = result?.cryptoObject?.cipher ?: return@prompt onResult(null)
      onResult(runCatching { authorized.doFinal(decode(data)).decodeToString() }.getOrNull())
    }
  }

  override fun deleteSecret(id: String) {
    secrets.edit {
      remove(dataKey(id))
      remove(ivKey(id))
    }
  }

  override fun deleteAllSecrets() {
    val checks = secrets.all.filterKeys { it.endsWith(CHECK_SUFFIX) }
    secrets.edit {
      clear()
      checks.forEach { (key, value) -> putString(key, value as String) }
    }
    runCatching { keyStore().deleteEntry(KEY_ALIAS) }
  }

  override fun rememberPasswordCheck(
    id: String,
    password: String,
  ) {
    val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
    secrets.edit { putString(checkKey(id), "${encode(salt)}:${encode(derive(password, salt))}") }
  }

  override fun matchesPasswordCheck(
    id: String,
    password: String,
  ): Boolean? {
    val stored = secrets.getString(checkKey(id), null) ?: return null
    val (salt, hash) = stored.split(":").takeIf { it.size == 2 }?.map(::decode) ?: return null
    return MessageDigest.isEqual(derive(password, salt), hash)
  }

  override fun forgetPasswordCheck(id: String) {
    secrets.edit { remove(checkKey(id)) }
  }

  private fun derive(
    password: String,
    salt: ByteArray,
  ): ByteArray {
    val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BITS)
    return try {
      SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }

  private fun checkKey(id: String) = "$id$CHECK_SUFFIX"

  private fun cipherOrNull(create: () -> Cipher): Cipher? =
    try {
      create()
    } catch (_: KeyPermanentlyInvalidatedException) {
      deleteAllSecrets()
      null
    } catch (_: Exception) {
      null
    }

  private fun prompt(
    info: BiometricPrompt.PromptInfo,
    crypto: BiometricPrompt.CryptoObject?,
    onResult: (BiometricPrompt.AuthenticationResult?) -> Unit,
  ) {
    if (activity.isFinishing || activity.supportFragmentManager.isStateSaved) return onResult(null)
    val biometricPrompt =
      BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onResult(result)
          }

          override fun onAuthenticationError(
            errorCode: Int,
            errString: CharSequence,
          ) {
            onResult(null)
          }
        },
      )
    if (crypto == null) biometricPrompt.authenticate(info) else biometricPrompt.authenticate(info, crypto)
  }

  private fun cryptoPromptInfo(title: String): BiometricPrompt.PromptInfo =
    BiometricPrompt.PromptInfo
      .Builder()
      .setTitle(title)
      .setAllowedAuthenticators(BIOMETRIC_STRONG)
      .setNegativeButtonText("Cancel")
      .build()

  private fun secretKey(): SecretKey {
    (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
    val spec =
      KeyGenParameterSpec
        .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setUserAuthenticationRequired(true)
        .setInvalidatedByBiometricEnrollment(true)
        .apply {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
          } else {
            @Suppress("DEPRECATION")
            setUserAuthenticationValidityDurationSeconds(-1)
          }
        }.build()
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
      init(spec)
      generateKey()
    }
  }

  private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

  private fun dataKey(id: String) = "$id.data"

  private fun ivKey(id: String) = "$id.iv"

  private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

  private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)

  private companion object {
    const val APP_LOCK_AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val KEY_ALIAS = "tandem-budget-passwords"
    const val SECRETS_FILE = "tandem_biometric_secrets"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_TAG_BITS = 128
    const val CHECK_SUFFIX = ".check"
    const val SALT_BYTES = 16
    const val PBKDF2_ITERATIONS = 310_000
    const val HASH_BITS = 256
  }
}
