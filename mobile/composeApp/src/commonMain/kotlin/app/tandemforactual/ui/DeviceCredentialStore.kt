package app.tandemforactual.ui

/** Platform-backed storage for this device's bearer credential after Actual sign-in. */
interface DeviceCredentialStore {
    fun read(): String?

    fun write(value: String)

    fun clear()
}
