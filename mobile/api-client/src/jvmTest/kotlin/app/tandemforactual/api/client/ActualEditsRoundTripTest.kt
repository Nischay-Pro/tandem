package app.tandemforactual.api.client

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.actual.ActualDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test

/** Runs only with ACTUAL_TEST_URL set: applies every direct budget edit through a real Actual server. */
class ActualEditsRoundTripTest {
    private val url = System.getenv("ACTUAL_TEST_URL")
    private val budgetName = System.getenv("ACTUAL_TEST_BUDGET") ?: "Parity envelope"
    private val out = System.getenv("ACTUAL_TEST_EDITS_OUT")

    private suspend fun download(
        client: ActualServerClient,
        fileId: String,
        path: File,
    ) = ActualDirectBudget.download(client, fileId, System::currentTimeMillis, { UUID.randomUUID().toString() }) {
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
            budget.close()

            val fresh = download(client, file.fileId, directory.resolve("fresh.sqlite"))
            fresh.sync()
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
