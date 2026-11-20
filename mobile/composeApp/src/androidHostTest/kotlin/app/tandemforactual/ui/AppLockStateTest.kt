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

    @Test
    fun wrongPasswordsWaitLongerAfterEachBlockOfFive() {
        kotlin.test.assertEquals(0L, unlockWaitMillis(4))
        kotlin.test.assertEquals(30_000L, unlockWaitMillis(5))
        kotlin.test.assertEquals(0L, unlockWaitMillis(6))
        kotlin.test.assertEquals(120_000L, unlockWaitMillis(10))
        kotlin.test.assertEquals(600_000L, unlockWaitMillis(15))
        kotlin.test.assertEquals(1_800_000L, unlockWaitMillis(40))
    }

    @Test
    fun theUnlockWaitSurvivesARestart() {
        val original =
            AppState().apply {
                unlockFailureCount = 5
                unlockRetryAtEpochMillis = 90_000L
            }
        val restored = AppState().apply { restorePersistentSettings(original.persistentSettings()) }
        kotlin.test.assertEquals(5, restored.unlockFailureCount)
        kotlin.test.assertEquals(90_000L, restored.unlockRetryAtEpochMillis)
    }
}
