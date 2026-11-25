package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActualTransactionOperationsTest {
    private class Budget(
        val db: ActualDatabase,
        val operations: ActualTransactionOperations,
    ) {
        val reader = ActualBudgetReader(db)
        val checking = account("Checking")
        val savings = account("Savings")
        val savingsPayee = db.first("SELECT id FROM payees WHERE transfer_acct = ?", savings)?.get("id") as String
        val groceries = db.first("SELECT id FROM categories WHERE name = 'Groceries'")?.get("id") as String
        val create =
            buildJsonObject {
                put("account", checking)
                put("amount", -1234)
                put("date", "2026-11-21")
                put("payee", JsonNull)
                put("category", groceries)
                put("notes", JsonNull)
                put("cleared", false)
            }

        fun account(name: String) = db.first("SELECT id FROM accounts WHERE name = ?", name)?.get("id") as String

        fun id() = "tandem:${UUID.randomUUID()}"

        fun submit(
            id: String,
            action: String,
            patch: JsonObject,
            base: JsonObject? = null,
            operationId: String = UUID.randomUUID().toString(),
        ) = operations.submit(operationId, id, action, patch, base)
    }

    private fun withBudget(block: Budget.() -> Unit) {
        val path = File.createTempFile("actual-transactions", ".sqlite")

        fun fixture(name: String) = checkNotNull(javaClass.getResource("/actual/parity-envelope/$name")).readBytes()
        path.writeBytes(ZipArchive.read(fixture("budget.zip")).getValue("db.sqlite"))
        val db = ActualDatabase(BundledSQLiteDriver().open(path.path))
        try {
            val now = { 1_795_255_200_000L }
            val store = ActualMessageStore(db)
            val clock = store.loadClock()
            store.resetNode(clock)
            val response = SyncProtocol.decodeResponse(fixture("sync-response.bin"))
            val engine =
                ActualSyncEngine(store, {
                    SyncResponse(
                        response.messages.filter { msg ->
                            msg.timestamp > it.since
                        },
                        response.merkle,
                    )
                }, "group", "file", now = now)
            runBlocking { engine.fullSync(clock, null) }
            Budget(db, ActualTransactionOperations(ActualWriter(store, clock, now) { UUID.randomUUID().toString() })).block()
        } finally {
            db.close()
            path.delete()
        }
    }

    @Test
    fun persistsOutcomesAndChecksStaleEdits() =
        withBudget {
            val id = id()
            val result = submit(id, "create", create)
            assertEquals(OperationStatus.COMMITTED, result.status)
            assertEquals(result, submit(id, "create", JsonObject(emptyMap()), operationId = result.operationId))
            assertEquals(OperationStatus.COMMITTED, submit(id, "create", create).status)
            val patch = buildJsonObject { put("amount", -1000) }
            assertEquals(listOf("amount"), submit(id, "create", JsonObject(create + patch)).conflictingFields)
            val base = checkNotNull(reader.transaction(id))
            assertEquals(OperationStatus.COMMITTED, submit(id, "update", patch, base).status)
            assertEquals(OperationStatus.COMMITTED, submit(id, "update", patch, base).status)
            assertEquals("same-field-edit", submit(id, "update", buildJsonObject { put("amount", -10) }, base).conflictType)
            val empty = JsonObject(emptyMap())
            assertEquals("delete-versus-edit", submit(id, "delete", empty, base).conflictType)
            assertEquals(OperationStatus.COMMITTED, submit(id, "delete", empty, reader.transaction(id)).status)
            assertNull(reader.transaction(id))
            assertEquals(OperationStatus.COMMITTED, submit(id, "delete", empty, base).status)
            assertEquals("deleted-remotely", submit(id, "update", patch, base).conflictType)
        }

    @Test
    fun maintainsTransferPairsAndValidatesReferences() =
        withBudget {
            val transfer = JsonObject(create + mapOf("payee" to JsonPrimitive(savingsPayee), "category" to JsonNull))
            val id = id()
            assertEquals(OperationStatus.COMMITTED, submit(id, "create", transfer).status)
            val base = checkNotNull(reader.transaction(id))
            val other = (base.getValue("transferId") as JsonPrimitive).content
            val update = buildJsonObject { put("amount", -7000) }
            assertEquals(OperationStatus.COMMITTED, submit(id, "update", update, base).status)
            assertEquals(JsonPrimitive(7000), reader.transaction(other)?.get("amount"))
            assertEquals(
                OperationStatus.COMMITTED,
                submit(id, "update", buildJsonObject { put("payee", JsonNull) }, reader.transaction(id)).status,
            )
            assertNull(reader.transaction(other))
            assertEquals("transfer-pair-changed", submit(id, "update", update, base).conflictType)
            for ((field, value, expected) in listOf(
                Triple("account", "missing", "account-not-found"),
                Triple("account", account("Old wallet"), "account-closed"),
                Triple("account", savings, "transfer-account-same-as-source"),
                Triple("payee", "missing", "payee-not-found"),
                Triple("category", "missing", "category-not-found"),
            )) {
                assertEquals(expected, submit(id(), "create", JsonObject(transfer + (field to JsonPrimitive(value)))).errorCode)
            }
        }

    @Test
    fun rejectsMalformedRequestsBeforeWriting() =
        withBudget {
            val id = id()
            assertFailsWith<InvalidTransactionRequestException> { submit(id, "unknown", create) }
            assertFailsWith<InvalidTransactionRequestException> { submit("unstable", "create", create) }
            assertFailsWith<InvalidTransactionRequestException> { submit(id, "create", JsonObject(create - "amount")) }
            assertFailsWith<InvalidTransactionRequestException> { submit(id, "create", JsonObject(create + ("typo" to JsonNull))) }
            for ((field, value) in listOf(
                "account" to JsonPrimitive(""),
                "amount" to JsonPrimitive("123"),
                "date" to JsonPrimitive("November 21"),
                "cleared" to JsonPrimitive("true"),
                "notes" to JsonArray(emptyList()),
                "category" to JsonPrimitive(123),
            )) {
                assertFailsWith<InvalidTransactionRequestException> { submit(id, "create", JsonObject(create + (field to value))) }
            }
            assertNull(reader.transaction(id))
            assertEquals(OperationStatus.COMMITTED, submit(id, "create", create).status)
            val base = reader.transaction(id)
            assertFailsWith<InvalidTransactionRequestException> { submit(id, "update", JsonObject(emptyMap())) }
            assertFailsWith<InvalidTransactionRequestException> { submit(id, "update", JsonObject(emptyMap()), base) }
            assertFailsWith<InvalidTransactionRequestException> { submit(id, "update", JsonObject(mapOf("typo" to JsonNull)), base) }
        }

    @Test
    fun handlesSplitAndBrokenTransferConflicts() =
        withBudget {
            val records = reader.read("2026-11-21").records.filter { it.type == "transaction" }
            val split = records.first { it.body.value("isParent") == true }
            val child = records.first { it.body.value("parentId") == split.id }
            val category = buildJsonObject { put("category", groceries) }
            assertEquals("same-field-edit", submit(split.id, "update", category, split.body).conflictType)
            val changedStructure = JsonObject(split.body + ("subtransactions" to JsonArray(emptyList())))
            assertEquals("same-field-edit", submit(split.id, "update", category, changedStructure).conflictType)
            assertEquals("delete-versus-edit", submit(split.id, "delete", JsonObject(emptyMap()), changedStructure).conflictType)
            val date = buildJsonObject { put("date", "2026-11-22") }
            assertEquals("split-child-field-not-editable", submit(child.id, "update", date, child.body).errorCode)
            assertEquals(OperationStatus.COMMITTED, submit(split.id, "delete", JsonObject(emptyMap()), split.body).status)
            assertNull(reader.transaction(child.id))

            val transfer = JsonObject(create + mapOf("payee" to JsonPrimitive(savingsPayee), "category" to JsonNull))
            val id = id()
            assertEquals(OperationStatus.COMMITTED, submit(id, "create", transfer).status)
            val base = checkNotNull(reader.transaction(id))
            val counterpart = base.value("transferId") as String
            val patch = buildJsonObject { put("amount", -7000) }
            db.execute("UPDATE transactions SET amount = 1 WHERE id = ?", counterpart)
            assertEquals(listOf("transferId", "amount"), submit(id, "update", patch, base).conflictingFields)
            db.execute("UPDATE transactions SET tombstone = 1 WHERE id = ?", counterpart)
            assertEquals("transfer-pair-incomplete", submit(id, "update", patch, base).conflictType)
        }

    @Test
    fun createsASplitWhoseLinesAddUpToTheTransaction() =
        withBudget {
            val other =
                db
                    .first(
                        "SELECT id FROM categories WHERE tombstone = 0 AND hidden = 0 AND id != ? ORDER BY name",
                        groceries,
                    )?.get("id") as String

            fun line(
                amount: Long,
                category: String?,
                notes: String? = null,
            ) = buildJsonObject {
                put("amount", amount)
                put("category", category)
                if (notes != null) put("notes", notes)
            }

            fun split(vararg lines: JsonObject) = JsonObject(create + ("subtransactions" to JsonArray(lines.toList())))
            val id = id()
            assertEquals(OperationStatus.COMMITTED, submit(id, "create", split(line(-1000, groceries, "food"), line(-234, other))).status)
            val created = checkNotNull(reader.transaction(id))
            assertEquals(JsonPrimitive(true), created["isParent"])
            assertEquals(JsonNull, created["category"])
            val children = created["subtransactions"] as JsonArray
            assertEquals(listOf(-1000L, -234L), children.map { ((it as JsonObject)["amount"] as JsonPrimitive).content.toLong() })
            assertEquals(JsonPrimitive("food"), (children[0] as JsonObject)["notes"])
            assertEquals(JsonPrimitive(other), (children[1] as JsonObject)["category"])

            assertFailsWith<InvalidTransactionRequestException> { submit(id(), "create", split(line(-1000, groceries), line(-1, other))) }
            assertFailsWith<InvalidTransactionRequestException> { submit(id(), "create", split(line(-1234, groceries))) }
            assertFailsWith<InvalidTransactionRequestException> {
                submit(id(), "create", JsonObject(create + ("subtransactions" to JsonPrimitive("x"))))
            }
            assertFailsWith<InvalidTransactionRequestException> {
                submit(id(), "create", JsonObject(create + ("subtransactions" to JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2))))))
            }
            assertFailsWith<InvalidTransactionRequestException> {
                submit(id(), "create", split(buildJsonObject { put("category", groceries) }, line(-1234, other)))
            }
            assertFailsWith<InvalidTransactionRequestException> {
                submit(
                    id(),
                    "create",
                    split(
                        buildJsonObject {
                            put("amount", -1234)
                            put("payee", "x")
                        },
                        line(0, other),
                    ),
                )
            }
            assertEquals(
                "category-not-found",
                submit(id(), "create", split(line(-1000, "missing"), line(-234, other))).errorCode,
            )
            val unsplit = id()
            assertEquals(OperationStatus.COMMITTED, submit(unsplit, "create", split(line(-1000, null), line(-234, null))).status)

            val brokerage = account("Brokerage")
            val offBudget = id()
            val onBrokerage =
                JsonObject(split(line(-1000, groceries), line(-234, other)) + ("account" to JsonPrimitive(brokerage)))
            assertEquals(OperationStatus.COMMITTED, submit(offBudget, "create", onBrokerage).status)
            assertEquals(JsonPrimitive(false), reader.transaction(offBudget)?.get("isParent"))
        }

    @Test
    fun aSplitsLinesFollowTheirParentsAccountDatePayeeAndClearedState() =
        withBudget {
            val payees =
                db
                    .query(
                        "SELECT id FROM payees WHERE tombstone = 0 AND transfer_acct IS NULL ORDER BY id",
                    ).map { it["id"] as String }
            val (shop, market, own) = payees

            fun line(amount: Long) =
                buildJsonObject {
                    put("amount", amount)
                    put("category", groceries)
                }

            fun lines(id: String) =
                db
                    .query(
                        "SELECT account, date, cleared, payee FROM v_transactions WHERE parent_id = ? ORDER BY sort_order DESC",
                        db.first("SELECT id FROM v_transactions WHERE imported_id = ?", id)?.get("id") ?: id,
                    ).map { listOf(it["account"], (it["date"] as Number).toLong(), it["cleared"], it["payee"]) }

            val id = id()
            val split =
                JsonObject(
                    create + ("payee" to JsonPrimitive(shop)) + ("subtransactions" to JsonArray(listOf(line(-1000), line(-234)))),
                )
            assertEquals(OperationStatus.COMMITTED, submit(id, "create", split).status)
            val second =
                (
                    (
                        reader.transaction(
                            id,
                        )!!["subtransactions"] as JsonArray
                    )[1] as JsonObject
                )["id"]!!.let { (it as JsonPrimitive).content }
            val ownPayee = buildJsonObject { put("payee", own) }
            assertEquals(OperationStatus.COMMITTED, submit(second, "update", ownPayee, reader.transaction(second)).status)

            val moved =
                buildJsonObject {
                    put("account", savings)
                    put("date", "2026-11-23")
                    put("cleared", true)
                    put("payee", market)
                }
            assertEquals(OperationStatus.COMMITTED, submit(id, "update", moved, reader.transaction(id)).status)
            assertEquals(
                listOf(listOf(savings, 20261123L, 1L, market), listOf(savings, 20261123L, 1L, own)),
                lines(id),
            )

            val before = checkNotNull(reader.transaction(id))
            val kept = (before["subtransactions"] as JsonArray).map { (it as JsonObject)["id"]!! }
            val resplit =
                buildJsonObject {
                    put("payee", shop)
                    put(
                        "subtransactions",
                        JsonArray(
                            listOf(
                                JsonObject(line(-1100) + ("id" to kept[0])),
                                JsonObject(
                                    line(-134) + ("id" to kept[1]),
                                ),
                            ),
                        ),
                    )
                }
            assertEquals(OperationStatus.COMMITTED, submit(id, "update", resplit, before).status)
            assertEquals(listOf(listOf(savings, 20261123L, 1L, shop), listOf(savings, 20261123L, 1L, own)), lines(id))
        }

    @Test
    fun editsAWholeSplitAndTurnsItBackIntoOneTransaction() =
        withBudget {
            val other =
                db
                    .first(
                        "SELECT id FROM categories WHERE tombstone = 0 AND hidden = 0 AND id != ? ORDER BY name",
                        groceries,
                    )?.get("id") as String

            fun line(
                amount: Long,
                category: String?,
                id: String? = null,
            ) = buildJsonObject {
                if (id != null) put("id", id)
                put("amount", amount)
                put("category", category)
            }

            fun lines(vararg lines: JsonObject) = JsonArray(lines.toList())

            fun JsonObject.lineIds() = (this["subtransactions"] as JsonArray).map { ((it as JsonObject)["id"] as JsonPrimitive).content }

            fun JsonObject.lineAmounts() =
                (this["subtransactions"] as JsonArray).map {
                    ((it as JsonObject)["amount"] as JsonPrimitive).content.toLong()
                }
            val id = id()
            submit(id, "create", JsonObject(create + ("subtransactions" to lines(line(-1000, groceries), line(-234, other)))))
            val base = checkNotNull(reader.transaction(id))
            val (first, second) = base.lineIds()

            val edit =
                buildJsonObject {
                    put("subtransactions", lines(line(-600, other, first), line(-500, groceries), line(-500, other)))
                    put("amount", -1600)
                    put("notes", "shared")
                }
            assertEquals(OperationStatus.COMMITTED, submit(id, "update", edit, base).status)
            val edited = checkNotNull(reader.transaction(id))
            assertEquals(listOf(-600L, -500L, -500L), edited.lineAmounts())
            assertEquals(first, edited.lineIds().first())
            assertEquals(false, second in edited.lineIds())
            assertEquals(JsonPrimitive(-1600), edited["amount"])
            assertEquals(JsonPrimitive("shared"), edited["notes"])

            assertEquals(OperationStatus.CONFLICT, submit(id, "update", buildJsonObject { put("subtransactions", lines()) }, base).status)
            val stale = JsonObject(edited + ("notes" to JsonPrimitive("before")))
            assertEquals(
                OperationStatus.CONFLICT,
                submit(
                    id,
                    "update",
                    buildJsonObject {
                        put("subtransactions", lines())
                        put("notes", "mine")
                    },
                    stale,
                ).status,
            )

            fun reject(
                patch: JsonObject,
                target: String = id,
                snapshot: JsonObject = edited,
            ) = submit(target, "update", patch, snapshot).errorCode
            val childId = edited.lineIds().first()
            assertEquals(
                "split-not-editable",
                reject(
                    buildJsonObject {
                        put("subtransactions", lines())
                    },
                    childId,
                    checkNotNull(reader.transaction(childId)),
                ),
            )
            assertEquals(
                "split-line-not-found",
                reject(buildJsonObject { put("subtransactions", lines(line(-800, groceries, "missing"), line(-800, other))) }),
            )
            assertEquals(
                "category-not-found",
                reject(buildJsonObject { put("subtransactions", lines(line(-800, "missing"), line(-800, other))) }),
            )
            assertEquals(
                "category-not-found",
                reject(
                    buildJsonObject {
                        put("subtransactions", lines())
                        put("category", "missing")
                    },
                ),
            )
            assertEquals(
                "payee-not-found",
                reject(
                    buildJsonObject {
                        put("subtransactions", lines())
                        put("payee", "missing")
                    },
                ),
            )
            assertFailsWith<InvalidTransactionRequestException> {
                reject(
                    buildJsonObject {
                        put("subtransactions", lines())
                        put("date", "2026-11-22")
                    },
                )
            }
            assertFailsWith<InvalidTransactionRequestException> {
                reject(buildJsonObject { put("subtransactions", lines(line(-800, groceries), line(-1, other))) })
            }

            assertEquals(
                OperationStatus.COMMITTED,
                submit(
                    id,
                    "update",
                    buildJsonObject {
                        put("subtransactions", lines())
                        put("category", groceries)
                    },
                    edited,
                ).status,
            )
            val single = checkNotNull(reader.transaction(id))
            assertEquals(JsonPrimitive(false), single["isParent"])
            assertEquals(JsonPrimitive(groceries), single["category"])
            assertEquals(emptyList(), single.lineIds())

            assertEquals(
                OperationStatus.COMMITTED,
                submit(
                    id,
                    "update",
                    buildJsonObject { put("subtransactions", lines(line(-1000, groceries), line(-600, other))) },
                    single,
                ).status,
            )
            assertEquals(JsonPrimitive(true), reader.transaction(id)?.get("isParent"))

            val transfer = id()
            submit(transfer, "create", JsonObject(create + mapOf("payee" to JsonPrimitive(savingsPayee), "category" to JsonNull)))
            val transferBase = checkNotNull(reader.transaction(transfer))
            assertEquals("split-not-editable", reject(buildJsonObject { put("subtransactions", lines()) }, transfer, transferBase))
        }

    @Test
    fun offBudgetAccountsTakeTransactionsWithoutACategory() =
        withBudget {
            val brokerage = account("Brokerage")
            val id = id()
            val adjustment = JsonObject(create + mapOf("account" to JsonPrimitive(brokerage), "amount" to JsonPrimitive(25_000)))
            assertEquals(OperationStatus.COMMITTED, submit(id, "create", adjustment).status)
            val created = checkNotNull(reader.transaction(id))
            assertEquals(JsonPrimitive(brokerage), created["account"])
            assertEquals(JsonPrimitive(25_000), created["amount"])
            assertEquals(JsonNull, created["category"])

            val moved = id()
            assertEquals(OperationStatus.COMMITTED, submit(moved, "create", create).status)
            val base = checkNotNull(reader.transaction(moved))
            assertEquals(OperationStatus.COMMITTED, submit(moved, "update", buildJsonObject { put("account", brokerage) }, base).status)
            assertEquals(JsonNull, reader.transaction(moved)?.get("category"))
        }

    @Test
    fun validatesUpdatedAccountsPayeesAndCategories() =
        withBudget {
            val id = id()
            assertEquals(OperationStatus.COMMITTED, submit(id, "create", create).status)
            val base = checkNotNull(reader.transaction(id))
            val closed = account("Old wallet")
            val closedPayee = db.first("SELECT id FROM payees WHERE transfer_acct = ?", closed)?.get("id") as String
            val hidden = "hidden-category-for-test"
            db.execute("INSERT INTO categories (id, name, hidden) VALUES (?, 'Hidden', 1)", hidden)
            for ((field, value, expected) in listOf(
                Triple("account", "missing", "account-not-found"),
                Triple("account", closed, "account-closed"),
                Triple("payee", "missing", "payee-not-found"),
                Triple("payee", closedPayee, "transfer-account-closed"),
                Triple("category", hidden, "category-hidden"),
            )) {
                assertEquals(expected, submit(id, "update", buildJsonObject { put(field, value) }, base).errorCode)
            }
            assertEquals(OperationStatus.COMMITTED, submit(id, "update", buildJsonObject { put("payee", savingsPayee) }, base).status)
            val transfer = checkNotNull(reader.transaction(id))
            val other = transfer.value("transferId") as String
            assertEquals(OperationStatus.COMMITTED, submit(id, "delete", JsonObject(emptyMap()), transfer).status)
            assertNull(reader.transaction(other))
        }

    @Test
    fun preservesTheSplitChildWhenDeletingItsTransferCounterpart() =
        withBudget {
            val child = reader.read("2026-11-21").records.first { it.type == "transaction" && it.body.value("isChild") == true }
            val id = id()
            assertEquals(OperationStatus.COMMITTED, submit(id, "create", create).status)
            val rowId = db.first("SELECT id FROM v_transactions WHERE imported_id = ?", id)?.get("id")
            db.execute("UPDATE transactions SET transferred_id = ? WHERE id = ?", child.id, rowId)
            db.execute("UPDATE transactions SET transferred_id = ?, amount = 1234 WHERE id = ?", rowId, child.id)
            assertEquals(OperationStatus.COMMITTED, submit(id, "delete", JsonObject(emptyMap()), reader.transaction(id)).status)
            assertEquals(JsonNull, checkNotNull(reader.transaction(child.id))["transferId"])
        }

    @Test
    fun clearsCategoryWhenMovingToAnOffBudgetAccount() =
        withBudget {
            val id = id()
            assertEquals(OperationStatus.COMMITTED, submit(id, "create", create).status)
            val patch = buildJsonObject { put("account", account("Brokerage")) }
            assertEquals(OperationStatus.COMMITTED, submit(id, "update", patch, reader.transaction(id)).status)
            assertEquals(JsonNull, checkNotNull(reader.transaction(id))["category"])
        }

    @Test
    fun readsDecimalAndStructuredJsonValues() {
        assertEquals(1.5, scalar(JsonPrimitive(1.5)))
        val values = JsonArray(listOf(JsonPrimitive("field")))
        assertEquals(values, scalar(values))
    }
}
