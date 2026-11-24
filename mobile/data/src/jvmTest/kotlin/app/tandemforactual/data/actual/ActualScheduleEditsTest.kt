package app.tandemforactual.data.actual

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActualScheduleEditsTest {
    private fun fixture(): Pair<ActualDatabase, ActualScheduleEdits> {
        val name = "parity-envelope"
        val entries = ZipArchive.read(checkNotNull(javaClass.getResource("/actual/$name/budget.zip")).readBytes())
        val file =
            File.createTempFile("schedules", ".sqlite").apply {
                writeBytes(entries.getValue("db.sqlite"))
                deleteOnExit()
            }
        val db = ActualDatabase(BundledSQLiteDriver().open(file.path))
        val store = ActualMessageStore(db)
        val clock = store.loadClock()
        val server = SyncProtocol.decodeResponse(checkNotNull(javaClass.getResource("/actual/$name/sync-response.bin")).readBytes())
        runBlocking {
            ActualSyncEngine(
                store,
                { request -> SyncResponse(server.messages.filter { it.timestamp > request.since }, server.merkle) },
                "g",
                "f",
            ) {
                System.currentTimeMillis()
            }.fullSync(clock, null)
        }
        val writer = ActualWriter(store, clock, System::currentTimeMillis) { UUID.randomUUID().toString() }
        return db to ActualBudgetEdits(writer, { "2026-11-21" }).schedules
    }

    private fun ActualScheduleEdits.rejects(
        message: String,
        block: ActualScheduleEdits.() -> Unit,
    ) = assertEquals(message, assertFailsWith<ActualEditException> { block() }.message)

    private fun recur(build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) =
        buildJsonObject {
            put("frequency", "weekly")
            put("start", "2026-11-02")
            build()
        }

    private fun date(value: JsonObject) = buildJsonObject { put("date", value) }

    @Test
    fun schedulesFollowActual() {
        val (db, schedules) = fixture()
        val id = db.first("SELECT id FROM schedules WHERE tombstone = 0")!!["id"] as String
        val account = db.first("SELECT id FROM accounts WHERE name = 'Savings'")!!["id"] as String
        val payee = db.first("SELECT id FROM payees WHERE name = 'Market' AND tombstone = 0")!!["id"] as String
        assertEquals("2026-12-02", schedules.nextDate(id))
        assertEquals(null, schedules.nextDate("missing"))

        db.execute("UPDATE schedules SET posts_transaction = 1 WHERE id = ?", id)
        schedules.rename(id, "  Monthly rent ")
        assertEquals("Monthly rent", db.first("SELECT name FROM schedules WHERE id = ?", id)!!["name"])
        schedules.rejects("Schedule names must be between 1 and 100 characters") { rename(id, " ") }
        schedules.rejects("Choose an active schedule in this budget") { rename("missing", "x") }
        db.execute("INSERT INTO schedules (id, name, tombstone, completed) VALUES ('other', 'Taken', 0, 0)")
        schedules.rejects("A schedule with this name already exists") { rename(id, "Taken") }

        schedules.edit(
            id,
            buildJsonObject {
                put("name", "Rent")
                put("accountId", account)
                put("payeeId", payee)
                put("amountOp", "isapprox")
                put("amountMinor", -1_000)
            },
        )
        val rule =
            db.first(
                "SELECT conditions FROM rules WHERE id = (SELECT rule FROM schedules WHERE id = ?)",
                id,
            )!!["conditions"] as String
        assertTrue(account in rule && payee in rule && "isapprox" in rule)
        schedules.edit(
            id,
            date(
                recur {
                    put("interval", 2)
                    put("skipWeekend", false)
                },
            ),
        )
        assertEquals("2026-11-30", schedules.nextDate(id))
        schedules.skip(id, "2026-11-30")
        assertEquals("2026-12-14", schedules.nextDate(id))
        schedules.rejects(
            "This schedule changed in Actual. Refresh it before trying again.",
        ) { skip(id, "2026-11-30") }
        schedules.rejects("This schedule changed in Actual. Refresh it before trying again.") { skip("missing", "x") }
        schedules.edit(id, buildJsonObject { put("date", "2027-01-15") })
        assertEquals("2027-01-15", schedules.nextDate(id))
        schedules.rejects("A one-time schedule has no next date to skip") { skip(id, "2027-01-15") }
        schedules.edit(
            id,
            buildJsonObject {
                put("amountOp", "isbetween")
                put(
                    "amountRangeMinor",
                    buildJsonObject {
                        put("num1", -3)
                        put("num2", -1)
                    },
                )
            },
        )
        assertEquals(
            -2L,
            scheduledAmount(
                buildJsonObject {
                    put("num1", -3)
                    put("num2", -1)
                },
            ),
        )
        assertEquals(5L, scheduledAmount(JsonPrimitive("5.0")))
        assertEquals(0L, scheduledAmount(null))

        val ruleId = db.first("SELECT rule FROM schedules WHERE id = ?", id)!!["rule"] as String
        db.execute(
            "UPDATE rules SET actions = ? WHERE id = ?",
            """[{"op":"link-schedule","value":"$id"},{"op":"set","field":"amount","value":1},{"op":"set","field":"amount","value":2,"options":{"formula":"x"}}]""",
            ruleId,
        )
        schedules.edit(
            id,
            buildJsonObject {
                put("amountOp", "is")
                put("amountMinor", -7)
            },
        )
        val actions = db.first("SELECT actions FROM rules WHERE id = ?", ruleId)!!["actions"] as String
        assertTrue("\"value\":-7" in actions && "\"value\":2" in actions)

        schedules.rejects("Add at least one schedule change") { edit(id, JsonObject(emptyMap())) }
        schedules.rejects("Choose an account") { edit(id, buildJsonObject { put("accountId", " ") }) }
        schedules.rejects("Choose a payee") { edit(id, buildJsonObject { put("payeeId", " ") }) }
        schedules.rejects("Schedule names must not contain control characters") { edit(id, buildJsonObject { put("name", "a\u0007") }) }
        schedules.rejects("Schedule names must be between 1 and 100 characters") { edit(id, buildJsonObject { put("name", " ") }) }
        schedules.rejects("Choose a single amount or an amount range") {
            edit(
                id,
                buildJsonObject {
                    put("amountMinor", 1)
                    put(
                        "amountRangeMinor",
                        buildJsonObject {
                            put("num1", 1)
                            put("num2", 2)
                        },
                    )
                },
            )
        }
        schedules.rejects("Choose an amount rule") { edit(id, buildJsonObject { put("amountMinor", 1) }) }
        schedules.rejects("The amount rule does not match the amount values") {
            edit(
                id,
                buildJsonObject {
                    put("amountMinor", 1)
                    put("amountOp", "isbetween")
                },
            )
        }
        schedules.rejects("Schedule amounts must be whole cents within the supported limit") {
            edit(
                id,
                buildJsonObject {
                    put("amountMinor", 1.5)
                    put("amountOp", "is")
                },
            )
        }
        schedules.rejects("The minimum amount must not exceed the maximum") {
            edit(
                id,
                buildJsonObject {
                    put("amountOp", "isbetween")
                    put(
                        "amountRangeMinor",
                        buildJsonObject {
                            put("num1", 2)
                            put("num2", 1)
                        },
                    )
                },
            )
        }
        schedules.rejects("An amount rule requires an amount") { edit(id, buildJsonObject { put("amountOp", "is") }) }
        schedules.rejects("Choose an active schedule in this budget") { edit("missing", buildJsonObject { put("name", "x") }) }
        schedules.rejects("A schedule with this name already exists") { edit(id, buildJsonObject { put("name", "Taken") }) }
        schedules.rejects("Choose an open account in this budget") { edit(id, buildJsonObject { put("accountId", "missing") }) }
        schedules.rejects("Choose a payee in this budget") { edit(id, buildJsonObject { put("payeeId", "missing") }) }

        schedules.rejects("Enter a schedule date as YYYY-MM-DD") { edit(id, buildJsonObject { put("date", "2026-02-30") }) }
        schedules.rejects("Enter a schedule date as YYYY-MM-DD") { edit(id, buildJsonObject { put("date", 5) }) }
        schedules.rejects("Enter a schedule date as YYYY-MM-DD") { edit(id, buildJsonObject { put("date", JsonArray(emptyList())) }) }
        schedules.rejects("Choose a valid recurrence and start date") { edit(id, date(recur { put("frequency", "hourly") })) }
        schedules.rejects("Choose a valid recurrence and start date") { edit(id, date(buildJsonObject { put("frequency", "daily") })) }
        schedules.rejects("Repeat interval must be between 1 and 999") { edit(id, date(recur { put("interval", 0) })) }
        schedules.rejects("Invalid weekend setting") { edit(id, date(recur { put("skipWeekend", "yes") })) }
        schedules.rejects("Choose a valid weekend adjustment") { edit(id, date(recur { put("weekendSolveMode", "sideways") })) }
        schedules.rejects("Choose a valid repeat pattern") { edit(id, date(recur { put("patterns", "x") })) }
        schedules.rejects("Choose a valid repeat pattern") {
            edit(
                id,
                date(
                    recur {
                        put(
                            "patterns",
                            JsonArray(
                                listOf(
                                    buildJsonObject {
                                        put("type", "XX")
                                        put("value", 1)
                                    },
                                ),
                            ),
                        )
                    },
                ),
            )
        }
        schedules.rejects("Choose a valid repeat end") { edit(id, date(recur { put("endMode", "someday") })) }
        schedules.rejects(
            "Occurrence count must be between 1 and 100,000",
        ) { edit(id, date(recur { put("endMode", "after_n_occurrences") })) }
        schedules.rejects("The end date must be on or after the start date") {
            edit(
                id,
                date(
                    recur {
                        put("endMode", "on_date")
                        put("endDate", "2026-01-01")
                    },
                ),
            )
        }
        schedules.edit(
            id,
            date(
                recur {
                    put("frequency", "monthly")
                    put(
                        "patterns",
                        JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("type", "FR")
                                    put("value", 2)
                                },
                            ),
                        ),
                    )
                    put("weekendSolveMode", "after")
                    put("endMode", "after_n_occurrences")
                    put("endOccurrences", 3)
                },
            ),
        )
        assertEquals("2026-12-11", schedules.nextDate(id))
        schedules.edit(
            id,
            date(
                recur {
                    put("endMode", "on_date")
                    put("endDate", "2026-12-01")
                },
            ),
        )
        schedules.edit(
            id,
            date(
                recur {
                    put("frequency", "yearly")
                    put("endMode", "never")
                },
            ),
        )

        db.execute("UPDATE rules SET conditions = '[]' WHERE id = ?", ruleId)
        schedules.rejects("Ammount can not be found. There is a bug here") {
            edit(
                id,
                buildJsonObject {
                    put("amountOp", "is")
                    put("amountMinor", 1)
                },
            )
        }
        schedules.rejects("Date can not be found. Schedules can not be created without a date there is a bug here") {
            edit(id, buildJsonObject { put("date", "2027-01-01") })
        }
        schedules.edit(id, buildJsonObject { put("name", "Without conditions") })
        db.execute("UPDATE rules SET tombstone = 1 WHERE id = ?", ruleId)
        schedules.rejects("No rule found for schedule") { edit(id, buildJsonObject { put("name", "x") }) }
        db.execute("UPDATE rules SET tombstone = 0 WHERE id = ?", ruleId)

        schedules.rejects("Schedule does not exist in this budget") { delete("missing") }
        schedules.delete(id)
        assertEquals(1L, db.first("SELECT tombstone FROM schedules WHERE id = ?", id)!!["tombstone"])
        assertEquals(1L, db.first("SELECT tombstone FROM rules WHERE id = ?", ruleId)!!["tombstone"])
    }
}
