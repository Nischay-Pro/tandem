package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockStateTest {
    @Test
    fun startsLockedAndStaysUnlockedAfterABriefSwitch() {
        var now = 0L
        val lock = AppLockState { now }
        assertFalse(lock.unlocked)

        lock.unlock()
        lock.onForegroundChanged(false)
        now += APP_LOCK_GRACE_MILLIS - 1
        lock.onForegroundChanged(true)

        assertTrue(lock.unlocked)
    }

    @Test
    fun locksAgainAfterTheGracePeriodInTheBackground() {
        var now = 0L
        val lock = AppLockState { now }
        lock.unlock()

        lock.onForegroundChanged(false)
        now += APP_LOCK_GRACE_MILLIS
        lock.onForegroundChanged(true)

        assertFalse(lock.unlocked)
    }

    @Test
    fun aRejectedSyncNeverUnlocksAnEncryptedBudget() {
        assertTrue(encryptedBudgetUnlockAccepted(syncSucceeded = true, serverReachable = true, offlineCheck = null))
        assertFalse(encryptedBudgetUnlockAccepted(syncSucceeded = false, serverReachable = true, offlineCheck = true))
        assertTrue(encryptedBudgetUnlockAccepted(syncSucceeded = false, serverReachable = false, offlineCheck = true))
        assertFalse(encryptedBudgetUnlockAccepted(syncSucceeded = false, serverReachable = false, offlineCheck = false))
        assertFalse(encryptedBudgetUnlockAccepted(syncSucceeded = false, serverReachable = false, offlineCheck = null))
    }
}
