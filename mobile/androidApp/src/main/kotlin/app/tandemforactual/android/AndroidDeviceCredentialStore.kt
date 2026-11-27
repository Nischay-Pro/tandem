package app.tandemforactual.android

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.tandemforactual.ui.core.session.DeviceCredentialStore
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore-sealed storage for one secret value; [name] keeps separate secrets apart. Keys need no user authentication
 * because background sync and backups read them while the phone is locked; they stay in StrongBox where the phone has
 * one. With [freshKeyOnWrite], each saved value gets a new key, so a replaced secret's key is gone with it.
 */
internal class AndroidDeviceCredentialStore(
    context: Context,
    name: String = "device_session",
    private val freshKeyOnWrite: Boolean = false,
) : DeviceCredentialStore {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val credentialKey = name
    private val keyAlias = if (name == "device_session") "app.tandemforactual.device-session.v1" else "app.tandemforactual.$name.v1"

    override fun read(): String? {
        val encoded = preferences.getString(credentialKey, null) ?: return null
        val encrypted = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        require(encrypted.size > IV_SIZE) { "Stored device credential is invalid" }
        val iv = encrypted.copyOfRange(0, IV_SIZE)
        val ciphertext = encrypted.copyOfRange(IV_SIZE, encrypted.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(TAG_SIZE_BITS, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    override fun write(value: String) {
        if (freshKeyOnWrite) deleteKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        } catch (_: Exception) {
            deleteKey()
            cipher.init(Cipher.ENCRYPT_MODE, generateKey(strongBox = false))
        }
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val encoded = Base64.encodeToString(encrypted, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        check(preferences.edit().putString(credentialKey, encoded).commit()) {
            "Could not save device credential"
        }
    }

    override fun clear() {
        check(preferences.edit().remove(credentialKey).commit()) {
            "Could not clear device credential"
        }
        deleteKey()
    }

    private fun deleteKey() {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(keyAlias)) keyStore.deleteEntry(keyAlias)
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val storedKey = keyStore.getKey(keyAlias, null)
        if (storedKey is SecretKey) return storedKey

        val strongBox = prefersStrongBox(Build.VERSION.SDK_INT, hasStrongBox())
        return try {
            generateKey(strongBox)
        } catch (error: Exception) {
            if (!strongBox) throw error
            generateKey(strongBox = false)
        }
    }

    private fun generateKey(strongBox: Boolean): SecretKey {
        val spec =
            KeyGenParameterSpec
                .Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .setKeySize(KEY_SIZE_BITS)
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) spec.setIsStrongBoxBacked(true)
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(spec.build())
        return generator.generateKey()
    }

    private fun hasStrongBox(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    private companion object {
        const val PREFERENCES_NAME = "tandem_secure_session"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_SIZE_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}

internal fun prefersStrongBox(
    sdkInt: Int,
    hasStrongBox: Boolean,
): Boolean = hasStrongBox && sdkInt >= Build.VERSION_CODES.P
