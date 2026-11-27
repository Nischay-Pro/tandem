package app.tandemforactual.ui.core.security

/** The app switcher's snapshot leaves out balances whenever a lock or privacy setting says they're not for every eye. */
internal fun hidesContentInAppSwitcher(
    biometricLockEnabled: Boolean,
    hideAmounts: Boolean,
    encryptedBudget: Boolean,
): Boolean = biometricLockEnabled || hideAmounts || encryptedBudget
