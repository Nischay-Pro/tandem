package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosDeviceCredentialStoreTest {
    @Test
    fun keychainCredentialCanBeAddedUpdatedReadAndCleared() {
        val store = IosDeviceCredentialStore()
        keychainStep("clear before test") { store.clear() }
        try {
            keychainStep("read empty credential") { assertNull(store.read()) }

            keychainStep("add credential") { store.write("first-device-token") }
            keychainStep("read added credential") { assertEquals("first-device-token", store.read()) }

            keychainStep("update credential") { store.write("updated-device-token") }
            keychainStep("read updated credential") { assertEquals("updated-device-token", store.read()) }
        } finally {
            keychainStep("clear after test") { store.clear() }
        }

        keychainStep("verify cleared credential") { assertNull(store.read()) }
    }

    private inline fun <T> keychainStep(
        operation: String,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (failure: Throwable) {
            println("Keychain test failed during $operation: ${failure.message}")
            throw failure
        }
}
