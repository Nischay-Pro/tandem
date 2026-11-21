package app.tandemforactual.data.database

import app.tandemforactual.data.actual.ActualDatabase

/** Files kept together for one direct budget installation. */
interface DirectBudgetFiles {
    val installationId: String

    fun installActual(bytes: ByteArray): ActualDatabase

    fun openActual(): ActualDatabase

    fun openMobileCache(): TandemDatabase
}
