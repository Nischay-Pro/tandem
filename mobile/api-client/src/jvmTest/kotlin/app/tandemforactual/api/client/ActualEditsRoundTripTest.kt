package app.tandemforactual.api.client

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.actual.ActualDatabase
import app.tandemforactual.data.actual.applyBudgetTemplate
import app.tandemforactual.data.actual.closeAccount
import app.tandemforactual.data.actual.copyBudgetMonth
import app.tandemforactual.data.actual.createAccount
import app.tandemforactual.data.actual.createCategory
import app.tandemforactual.data.actual.createCategoryGroup
import app.tandemforactual.data.actual.createPayee
import app.tandemforactual.data.actual.createRule
import app.tandemforactual.data.actual.deleteCategory
import app.tandemforactual.data.actual.holdBudgetForNextMonth
import app.tandemforactual.data.actual.manageCategory
import app.tandemforactual.data.actual.manageCategoryGroup
import app.tandemforactual.data.actual.mergePayee
import app.tandemforactual.data.actual.moveAccount
import app.tandemforactual.data.actual.moveMoney
import app.tandemforactual.data.actual.renameAccount
import app.tandemforactual.data.actual.renameCategory
import app.tandemforactual.data.actual.renamePayee
import app.tandemforactual.data.actual.setBudgetAmount
import app.tandemforactual.data.actual.setBudgetCarryover
import app.tandemforactual.data.actual.setPayeeDefaultCategory
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import java.io.File
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Runs only with ACTUAL_TEST_URL set: applies every direct budget edit through a real Actual server. */
class ActualEditsRoundTripTest {
    private val url = System.getenv("ACTUAL_TEST_URL")
    private val budgetName = System.getenv("ACTUAL_TEST_BUDGET") ?: "Parity envelope"
    private val out = System.getenv("ACTUAL_TEST_EDITS_OUT")

    private suspend fun download(
        client: ActualServerClient,
        fileId: String,
        path: File,
    ) = ActualDirectBudget.download(client, fileId, System::currentTimeMillis, {
        UUID.randomUUID().toString()
    }, null, { error("Not encrypted") }) {
        path.writeBytes(it)
        ActualDatabase(BundledSQLiteDriver().open(path.path))
    }

    @Test
    fun editsRoundTripThroughActual(): Unit =
        runBlocking {
            if (url == null) return@runBlocking
            val client = ActualServerClient(HttpClient(Java), url)
            client.signIn(System.getenv("ACTUAL_TEST_PASSWORD") ?: "demo-password")
            val file = client.listFiles().first { it.name == budgetName && !it.deleted }
            val directory = createTempDirectory("edits").toFile()
            val today =
                java.time.LocalDate
                    .now()
                    .toString()
            val month = today.substring(0, 7)
            val next =
                java.time.YearMonth
                    .parse(month)
                    .plusMonths(1)
                    .toString()
            val budget = download(client, file.fileId, directory.resolve("edit.sqlite"))
            budget.sync()
            val snapshot = budget.snapshot(today)

            fun id(
                type: String,
                name: String,
            ) = snapshot.records.first { it.type == type && (it.body["name"] as JsonPrimitive).content == name }.id
            val checking = id("account", "Checking")
            val groceries = id("category", "Groceries")
            val ids = linkedMapOf<String, String>()
            val tag = UUID.randomUUID().toString().take(6)
            budget.edit(today) { edits ->
                val wallet = edits.createAccount("Edit wallet $tag", false, null)
                edits.renameAccount(wallet, "Edit wallet renamed $tag")
                edits.moveAccount(wallet, checking)
                val spare = edits.createAccount("Spare wallet $tag", true, null)
                edits.closeAccount(spare, null, null)
                val payee = edits.createPayee("Edit payee $tag")
                edits.renamePayee(payee, "Edit payee renamed $tag")
                val merged = edits.createPayee("Merge me $tag")
                edits.mergePayee(merged, payee)
                edits.setPayeeDefaultCategory(payee, groceries)
                val group = edits.createCategoryGroup("Edit group $tag", false)
                val first = edits.createCategory(group, "Edit first $tag")
                val second = edits.createCategory(group, "Edit second $tag")
                edits.renameCategory(first, "Edit first renamed $tag")
                edits.manageCategory(second, "move", first, null)
                edits.manageCategoryGroup(group, "rename", "Edit group renamed $tag", null)
                edits.manageCategoryGroup(group, "move", null, null)
                edits.setBudgetAmount(second, month, 12_345)
                edits.setBudgetAmount(first, month, 2_000)
                edits.setBudgetCarryover(second, month, true)
                edits.moveMoney(month, second, first, 345)
                edits.copyBudgetMonth(next)
                edits.applyBudgetTemplate(next, listOf(second to 5_000L))
                edits.holdBudgetForNextMonth(month, 1_000)
                edits.manageCategory(first, "hide", null, null)
                edits.deleteCategory(first, second)
                val rule =
                    edits.createRule(
                        buildJsonObject {
                            put("stage", "pre")
                            put("conditionsOp", "or")
                            put(
                                "conditions",
                                JsonArray(
                                    listOf(
                                        buildJsonObject {
                                            put("field", "notes")
                                            put("op", "contains")
                                            put("value", "edit")
                                        },
                                    ),
                                ),
                            )
                            put(
                                "actions",
                                JsonArray(
                                    listOf(
                                        buildJsonObject {
                                            put("field", "cleared")
                                            put("op", "set")
                                            put("value", true)
                                        },
                                    ),
                                ),
                            )
                        },
                    )
                val scheduleId = snapshot.records.first { it.type == "schedule" }.id
                edits.schedules.rename(scheduleId, "Rent $tag")
                edits.schedules.edit(
                    scheduleId,
                    buildJsonObject {
                        put("amountOp", "isbetween")
                        put(
                            "amountRangeMinor",
                            buildJsonObject {
                                put("num1", -151_000)
                                put("num2", -149_000)
                            },
                        )
                        put(
                            "date",
                            buildJsonObject {
                                put("frequency", "monthly")
                                put("start", "2026-08-01")
                                put("interval", 1)
                                put(
                                    "patterns",
                                    JsonArray(
                                        listOf(
                                            buildJsonObject {
                                                put("type", "day")
                                                put("value", -1)
                                            },
                                        ),
                                    ),
                                )
                                put("skipWeekend", true)
                                put("weekendSolveMode", "before")
                                put("endMode", "never")
                            },
                        )
                    },
                )
                edits.schedules.skip(scheduleId, checkNotNull(edits.schedules.nextDate(scheduleId)))
                ids["schedule"] = scheduleId
                ids["posted"] = edits.schedules.post(scheduleId, checkNotNull(edits.schedules.nextDate(scheduleId)))
                ids +=
                    mapOf(
                        "wallet" to wallet,
                        "spare" to spare,
                        "payee" to payee,
                        "merged" to merged,
                        "group" to group,
                        "first" to first,
                        "second" to second,
                        "rule" to rule,
                    )
            }
            val bistro = id("payee", "Bistro & Bar")
            val ruled = "tandem:${UUID.randomUUID()}"
            val created =
                budget.submit(
                    UUID.randomUUID().toString(),
                    ruled,
                    "create",
                    buildJsonObject {
                        put("account", checking)
                        put("amount", -2_100)
                        put("date", today)
                        put("payee", bistro)
                        put("category", null as String?)
                        put("notes", "rules applied")
                        put("cleared", false)
                    },
                    null,
                )
            check(created.status == app.tandemforactual.data.actual.OperationStatus.COMMITTED)
            budget.sync()
            ids["ruled"] = ruled
            budget.close()

            val fresh = download(client, file.fileId, directory.resolve("fresh.sqlite"))
            fresh.refreshProjection(today)
            val reread = fresh.snapshot(today)
            fresh.close()
            if (out != null) {
                val records =
                    JsonArray(
                        reread.records.map {
                            JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive(it.type),
                                    "id" to JsonPrimitive(it.id),
                                    "body" to it.body,
                                    "revision" to JsonPrimitive(it.revision),
                                ),
                            )
                        },
                    )
                File(out).writeText(
                    JsonObject(
                        mapOf(
                            "today" to JsonPrimitive(today),
                            "ids" to JsonObject(ids.mapValues { JsonPrimitive(it.value) }),
                            "records" to records,
                        ),
                    ).toString(),
                )
            }
        }
}
