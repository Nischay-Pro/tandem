package app.tandemforactual.ui

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.bank.BankProviders
import app.tandemforactual.ui.feature.banking.homeBankRegions
import app.tandemforactual.ui.feature.banking.splitBankProviders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BankRegionsTest {
    @Test
    fun showsTheBudgetCountrysProvidersAndTucksTheRestAway() {
        val (nearby, elsewhere) = splitBankProviders(BankProviders.all, homeBankRegions("usd"), emptySet())
        assertEquals(listOf("SimpleFIN"), nearby.map { it.displayName })
        assertTrue(elsewhere.none { Region.US in it.regions })
        assertEquals(BankProviders.all.size, nearby.size + elsewhere.size)
    }

    @Test
    fun keepsLinkedProvidersAndShowsEverythingWhenTheCountryIsUnknown() {
        val linked = splitBankProviders(BankProviders.all, homeBankRegions("CAD"), setOf("goCardless")).first
        assertEquals(setOf("simpleFin", "goCardless"), linked.map { it.source }.toSet())
        assertEquals(emptySet(), homeBankRegions("JPY"))
        assertEquals(emptySet(), homeBankRegions(null))
        assertEquals(BankProviders.all, splitBankProviders(BankProviders.all, emptySet(), emptySet()).first)
    }
}
