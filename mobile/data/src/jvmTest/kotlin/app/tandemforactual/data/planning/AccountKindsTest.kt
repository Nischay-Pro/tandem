package app.tandemforactual.data.planning

import androidx.room3.Room
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class AccountKindsTest {
    @Test
    fun guessesKindsFromTheNamesPeopleGiveAccounts() {
        assertEquals(AccountKind.BANK, guessAccountKind("Everyday checking"))
        assertEquals(AccountKind.BANK, guessAccountKind("Chase Chequing"))
        assertEquals(AccountKind.SAVINGS, guessAccountKind("Savings"))
        assertEquals(AccountKind.SAVINGS, guessAccountKind("Emergency fund"))
        assertEquals(AccountKind.CREDIT_CARD, guessAccountKind("Credit card"))
        assertEquals(AccountKind.CREDIT_CARD, guessAccountKind("Amex Gold"))
        assertEquals(AccountKind.CASH, guessAccountKind("Wallet"))
        assertEquals(AccountKind.INVESTMENT, guessAccountKind("Roth IRA"))
        assertEquals(AccountKind.INVESTMENT, guessAccountKind("401k"))
        assertEquals(AccountKind.LOAN, guessAccountKind("Car loan"))
        assertEquals(AccountKind.MORTGAGE, guessAccountKind("Home mortgage"))
        assertNull(guessAccountKind("Primary"))
        assertNull(guessAccountKind("Mirage"))
        assertNull(guessAccountKind(""))
    }

    @Test
    fun preferAStoredTypeThenTheBanksOwnName() {
        assertEquals(AccountKind.CREDIT_CARD, guessAccountKind("Primary", storedType = "credit-card"))
        assertEquals(AccountKind.CREDIT_CARD, guessAccountKind("Savings", storedType = "credit"))
        assertEquals(AccountKind.BANK, guessAccountKind("x", storedType = "Depository"))
        assertEquals(AccountKind.BANK, guessAccountKind("x", storedType = "checking"))
        assertEquals(AccountKind.BANK, guessAccountKind("x", storedType = "chequing"))
        assertEquals(AccountKind.BANK, guessAccountKind("x", storedType = "bank"))
        assertEquals(AccountKind.SAVINGS, guessAccountKind("x", storedType = "saving"))
        assertEquals(AccountKind.SAVINGS, guessAccountKind("x", storedType = "savings"))
        assertEquals(AccountKind.CREDIT_CARD, guessAccountKind("x", storedType = "credit_card"))
        assertEquals(AccountKind.CASH, guessAccountKind("x", storedType = "cash"))
        assertEquals(AccountKind.INVESTMENT, guessAccountKind("x", storedType = "investment"))
        assertEquals(AccountKind.INVESTMENT, guessAccountKind("x", storedType = "brokerage"))
        assertEquals(AccountKind.LOAN, guessAccountKind("x", storedType = "debt"))
        assertEquals(AccountKind.LOAN, guessAccountKind("x", storedType = "loan"))
        assertEquals(AccountKind.MORTGAGE, guessAccountKind("x", storedType = "mortgage"))
        assertEquals(AccountKind.SAVINGS, guessAccountKind("Joint", officialName = "High Yield Savings", storedType = "other"))
        assertEquals(AccountKind.LOAN, guessAccountKind("Savings", officialName = "Personal loan"))
    }

    @Test
    fun aChoiceWinsAndAnUnknownAccountIsOtherButUnconfirmed() {
        assertEquals(ResolvedAccountKind(AccountKind.SAVINGS, chosen = true), resolveAccountKind(AccountKind.SAVINGS, "Credit card"))
        assertEquals(ResolvedAccountKind(AccountKind.CREDIT_CARD, chosen = false), resolveAccountKind(null, "Credit card"))
        assertEquals(ResolvedAccountKind(AccountKind.OTHER, chosen = false), resolveAccountKind(null, "Primary"))
    }

    @Test
    fun kindsHaveOneKeyEachAndKnowWhichOweMoney() {
        AccountKind.entries.forEach { assertEquals(it, AccountKind.fromKey(it.key)) }
        assertNull(AccountKind.fromKey("checking"))
        assertNull(AccountKind.fromKey(null))
        assertEquals(
            setOf(AccountKind.CREDIT_CARD, AccountKind.LOAN, AccountKind.MORTGAGE),
            AccountKind.entries.filter { it.owesMoney }.toSet(),
        )
        assertEquals("Credit cards", AccountKind.CREDIT_CARD.groupLabel)
        assertEquals("Bank account", AccountKind.BANK.label)
    }

    @Test
    fun choicesRoundTripThroughJson() {
        val choice = AccountKindChoice("acc", AccountKind.MORTGAGE)
        assertEquals("kind:acc", choice.id)
        assertEquals(choice, AccountKindChoice.fromJson(choice.toJson()))
        assertNull(AccountKindChoice.fromJson(JsonObject(emptyMap())))
        assertNull(AccountKindChoice.fromJson(JsonObject(mapOf("accountId" to JsonPrimitive(""), "kind" to JsonPrimitive("cash")))))
        assertNull(AccountKindChoice.fromJson(JsonObject(mapOf("accountId" to JsonPrimitive("acc"), "kind" to JsonPrimitive("boat")))))
    }

    @Test
    fun aCardKeepsItsNetworkThroughJson() {
        val card = AccountKindChoice("acc", AccountKind.CREDIT_CARD, CardNetwork.AMEX)
        assertEquals(card, AccountKindChoice.fromJson(card.toJson()))
        assertEquals(JsonPrimitive("amex"), card.toJson()["network"])
        assertNull(AccountKindChoice("acc", AccountKind.CREDIT_CARD).toJson()["network"])
        val unknown =
            JsonObject(mapOf("accountId" to JsonPrimitive("acc"), "kind" to JsonPrimitive("credit_card"), "network" to JsonPrimitive("x")))
        assertNull(AccountKindChoice.fromJson(unknown)?.network)
        CardNetwork.entries.forEach { assertEquals(it, CardNetwork.fromKey(it.key)) }
        assertNull(CardNetwork.fromKey(null))
        assertEquals("American Express", CardNetwork.AMEX.label)
        assertEquals("diners-club", CardNetwork.DINERS.iconSlug)
    }

    @Test
    fun storesAndClearsChoices(): Unit =
        runBlocking {
            val database = openTandemDatabase(Room.inMemoryDatabaseBuilder<TandemDatabase>().setQueryCoroutineContext(Dispatchers.IO))
            try {
                val store = PlanningStore(database.planningRecordDao(), "b", PlanningCipher(PlanningCipher.newKey())) { 1L }
                store.save(AccountKindChoice("acc", AccountKind.CREDIT_CARD))
                assertEquals(listOf(AccountKindChoice("acc", AccountKind.CREDIT_CARD)), store.accountKinds().first())
                store.save(AccountKindChoice("acc", AccountKind.LOAN))
                assertEquals(listOf(AccountKindChoice("acc", AccountKind.LOAN)), store.accountKinds().first())
                assertEquals(listOf(AccountKindChoice.TYPE), store.backupRecords().map { it.second })
                store.deleteAccountKind("acc")
                assertTrue(store.accountKinds().first().isEmpty())
            } finally {
                database.close()
            }
        }
}
