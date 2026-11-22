package app.tandemforactual.ui.core.session

/** Platform-backed storage for this device's bearer credential after Actual sign-in. */
interface DeviceCredentialStore {
    fun read(): String?

    fun write(value: String)

    fun clear()
}
