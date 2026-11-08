package app.tandemforactual.ui

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosDeviceCredentialStoreTest {
    // Keychain lifecycle needs an app-hosted iOS test target; the standalone simulator host returns errSecNotAvailable.
    @Ignore
    @Test
    fun keychainCredentialCanBeAddedUpdatedReadAndCleared() {
        val store = IosDeviceCredentialStore()
        store.clear()
        try {
            assertNull(store.read())

            store.write("first-device-token")
            assertEquals("first-device-token", store.read())

            store.write("updated-device-token")
            assertEquals("updated-device-token", store.read())
        } finally {
            store.clear()
        }

        assertNull(store.read())
    }
}
