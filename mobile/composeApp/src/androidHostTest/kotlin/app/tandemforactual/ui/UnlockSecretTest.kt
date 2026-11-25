package app.tandemforactual.ui

import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.ui.core.session.EncryptedBudgetVault
import app.tandemforactual.ui.core.session.PendingEncryptedBudget
import app.tandemforactual.ui.core.session.UnlockSecret
import app.tandemforactual.ui.core.session.unlockSecret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class UnlockSecretTest {
    @Test
    fun keepsTheVaultKeyBesideThePasswordAndReadsOldSecrets() {
        val key = byteArrayOf(1, 2, 3, 4)
        val saved = UnlockSecret("p:a:ss", key).encode()
        assertEquals(UnlockSecret("p:a:ss", key), UnlockSecret.decode(saved))
        assertEquals(UnlockSecret("old password"), UnlockSecret.decode("old password"))
        assertEquals("plain", UnlockSecret("plain").encode())
        assertEquals(UnlockSecret("tandem-unlock-v2:!!!:x"), UnlockSecret.decode("tandem-unlock-v2:!!!:x"))
        assertNotEquals(UnlockSecret("p", key), UnlockSecret("p", byteArrayOf(9)))
        assertEquals(UnlockSecret("p", key).hashCode(), UnlockSecret("p", byteArrayOf(1, 2, 3, 4)).hashCode())
    }

    @Test
    fun savesTheKeyOnlyWhenTheVaultJustOpenedTheBudget() {
        val vault =
            object : EncryptedBudgetVault {
                override fun hasStore(budgetId: String) = true

                override suspend fun open(
                    budgetId: String,
                    password: String,
                ): TandemDatabase? = null

                override suspend fun create(
                    budgetId: String,
                    password: String,
                ): PendingEncryptedBudget = error("unused")

                override fun delete(budgetId: String) = Unit

                override fun deleteAll() = Unit

                override fun openedKey(budgetId: String) = if (budgetId == "open") byteArrayOf(7) else null
            }
        assertEquals(byteArrayOf(7).toList(), UnlockSecret.decode(unlockSecret(vault, "open", "pw")).vaultKey?.toList())
        assertEquals("pw", unlockSecret(vault, "other", "pw"))
        assertEquals("pw", unlockSecret(null, "open", "pw"))
        assertNull(UnlockSecret.decode(unlockSecret(null, "open", "pw")).vaultKey)
    }
}
