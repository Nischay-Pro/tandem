package app.tandemforactual.api.client

import app.tandemforactual.data.actual.ActualSnapshot
import app.tandemforactual.data.store.RemoteBudgetSnapshot

data class BudgetRecordKey(
    val type: String,
    val id: String,
)

data class ActualBudgetParity(
    val missingFromDirect: Set<BudgetRecordKey>,
    val addedByDirect: Set<BudgetRecordKey>,
    val changed: Set<BudgetRecordKey>,
    val displaySettingsMatch: Boolean,
) {
    val matches: Boolean
        get() = missingFromDirect.isEmpty() && addedByDirect.isEmpty() && changed.isEmpty() && displaySettingsMatch
}

/** Compares the synchronized budget records before allowing a migration to select the direct write path. */
fun ActualSnapshot.compareWithGateway(gateway: RemoteBudgetSnapshot): ActualBudgetParity {
    val directRecords = records.associateBy { BudgetRecordKey(it.type, it.id) }
    val gatewayRecords = gateway.records.associateBy { BudgetRecordKey(it.recordType, it.recordId) }
    val sharedKeys = directRecords.keys intersect gatewayRecords.keys
    return ActualBudgetParity(
        missingFromDirect = gatewayRecords.keys - directRecords.keys,
        addedByDirect = directRecords.keys - gatewayRecords.keys,
        changed =
            sharedKeys.filterTo(mutableSetOf()) { key ->
                val direct = directRecords.getValue(key)
                val legacy = gatewayRecords.getValue(key)
                direct.revision != legacy.revision || direct.body != BudgetSyncJson.parseJsonObject(legacy.bodyJson)
            },
        displaySettingsMatch = displaySettings == gateway.displaySettings,
    )
}
