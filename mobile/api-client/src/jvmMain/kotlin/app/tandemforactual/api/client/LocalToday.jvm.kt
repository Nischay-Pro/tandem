package app.tandemforactual.api.client

import java.time.LocalDate

internal actual fun localTodayIsoDate(): String = LocalDate.now().toString()
