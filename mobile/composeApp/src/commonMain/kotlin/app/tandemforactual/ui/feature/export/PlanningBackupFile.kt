package app.tandemforactual.ui.feature.export

import app.tandemforactual.data.planning.CustomReport
import app.tandemforactual.data.planning.DebtTerms
import app.tandemforactual.data.planning.JurisdictionPeriod
import app.tandemforactual.data.planning.PensionEstimate
import app.tandemforactual.data.planning.PlanningBackupSeal
import app.tandemforactual.data.planning.PlanningSettings
import app.tandemforactual.data.planning.Reimbursement
import app.tandemforactual.data.planning.RetirementProfile
import app.tandemforactual.data.planning.SavedSearch
import app.tandemforactual.data.planning.SavingsGoal
import app.tandemforactual.data.planning.SharedBill
import app.tandemforactual.ui.core.session.PLANNING_BACKUP_FORMAT
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.planningBackupJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The planning records in a backup file, after opening it with [passphrase] when it is sealed. */
internal sealed interface BackupContents {
    data class Records(
        val records: List<Triple<String, String, JsonObject>>,
    ) : BackupContents

    data object NeedsPassphrase : BackupContents

    data object WrongPassphrase : BackupContents

    data object NotABackup : BackupContents
}

internal fun readBackupFile(
    contents: String,
    passphrase: String?,
): BackupContents {
    if (!PlanningBackupSeal.isSealed(contents)) {
        return readPlanningBackup(contents)?.let(BackupContents::Records) ?: BackupContents.NotABackup
    }
    if (passphrase == null) return BackupContents.NeedsPassphrase
    val opened = PlanningBackupSeal.open(contents, passphrase) ?: return BackupContents.WrongPassphrase
    return readPlanningBackup(opened)?.let(BackupContents::Records) ?: BackupContents.NotABackup
}

internal fun sealedPlanningBackup(
    session: TandemMobileSession,
    passphrase: String,
): String = PlanningBackupSeal.seal(planningBackup(session), passphrase)

internal fun planningBackup(session: TandemMobileSession): String {
    val records =
        session.planning.savingsGoals.map { Triple(it.id, SavingsGoal.TYPE, it.toJson()) } +
            session.planning.debtTerms.map { Triple(it.id, DebtTerms.TYPE, it.toJson()) } +
            session.planning.retirementProfiles.map { Triple(it.planId, RetirementProfile.TYPE, it.toJson()) } +
            session.planning.reimbursements.map { Triple(it.transactionId, Reimbursement.TYPE, it.toJson()) } +
            session.planning.customReports.map { Triple(it.id, CustomReport.TYPE, it.toJson()) } +
            session.planning.savedSearches.map { Triple(it.id, SavedSearch.TYPE, it.toJson()) } +
            session.planning.jurisdictionPeriods.map { Triple(it.id, JurisdictionPeriod.TYPE, it.toJson()) } +
            session.planning.sharedBills.map { Triple(it.id, SharedBill.TYPE, it.toJson()) } +
            session.planning.pensionEstimates.map { Triple(it.pensionId, PensionEstimate.TYPE, it.toJson()) } +
            listOf(Triple(PlanningSettings.ID, PlanningSettings.TYPE, session.planning.settings.toJson()))
    return planningBackupJson(records)
}

internal fun readPlanningBackup(contents: String): List<Triple<String, String, JsonObject>>? =
    runCatching {
        val root = Json.parseToJsonElement(contents).jsonObject
        if ((root["format"] as? JsonPrimitive)?.content != PLANNING_BACKUP_FORMAT) return null
        root
            .getValue("records")
            .jsonArray
            .map { element ->
                val record = element.jsonObject
                Triple(
                    record.getValue("id").jsonPrimitive.content,
                    record.getValue("type").jsonPrimitive.content,
                    record.getValue("body").jsonObject,
                )
            }.filter { (_, type, _) -> type.startsWith("planning.") }
    }.getOrNull()
