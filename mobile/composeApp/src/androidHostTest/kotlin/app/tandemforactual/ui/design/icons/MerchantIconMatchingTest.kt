package app.tandemforactual.ui.design.icons

import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.liveAccountSummaries
import app.tandemforactual.ui.core.ui.accountTypeIcon
import app.tandemforactual.ui.design.templates.financeIcon
import app.tandemforactual.ui.feature.accounts.accountBalanceAccessibilityDescription
import app.tandemforactual.ui.feature.accounts.accountGroupOrder
import app.tandemforactual.ui.feature.accounts.accountListSubtitle
import app.tandemforactual.ui.feature.accounts.accountListTypeLabel
import app.tandemforactual.ui.feature.accounts.sortAccountsForList
import com.composables.icons.lucide.CarFront
import com.composables.icons.lucide.Carrot
import com.composables.icons.lucide.ChartNoAxesCombined
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.Plane
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Shirt
import com.composables.icons.lucide.ShoppingCart
import com.composables.icons.lucide.WalletCards
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MerchantIconMatchingTest {
    @Test
    fun categoryIconsUseWholeWordsAndSpecificPhrases() {
        assertEquals(Lucide.CarFront, financeIcon("Petrol"))
        assertNull(financeIcon("Carpet cleaning"))
        assertNull(financeIcon("Competition fees"))
        assertNull(financeIcon("Current account"))
        assertEquals(Lucide.WalletCards, financeIcon("Accounts"))
        assertNull(financeIcon("Parent loan"))
        assertNull(financeIcon("Different bank"))
        assertNull(financeIcon("Marketing"))
        assertNull(financeIcon("Home Depot"))
        assertEquals(Lucide.ShieldCheck, financeIcon("Home insurance"))
        assertEquals(Lucide.Plane, financeIcon("Flights"))
        assertEquals(Lucide.Shirt, financeIcon("Clothing"))
        assertEquals(Lucide.Carrot, financeIcon("Groceries"))
        assertEquals(Lucide.ShoppingCart, financeIcon("Shopping"))
    }

    @Test
    fun merchantIconsRequireALeadingAliasAndOnlyAllowStoreNumberSuffixes() {
        val matcher =
            MerchantAliasMatcher<String>().apply {
                add("Target", "target", licensePriority = 0, isTitle = true)
                add("Shell", "shell", licensePriority = 0, isTitle = true)
                add("Amazon", "amazon", licensePriority = 0, isTitle = true)
                add("Uber", "uber", licensePriority = 0, isTitle = true)
                add("Hilton", "hilton", licensePriority = 0, isTitle = true)
                add("Walmart", "walmart", licensePriority = 0, isTitle = true)
                add("Whole Foods", "whole-foods", licensePriority = 0, isTitle = true)
                add("Transfer", "transfer", licensePriority = 0, isTitle = true)
                add("Education", "education", licensePriority = 0, isTitle = true)
                add("Search", "search", licensePriority = 0, isTitle = true)
                add("Mobile", "mobile", licensePriority = 0, isTitle = true)
                add("Metro", "metro", licensePriority = 0, isTitle = true)
                add("Costco", "costco", licensePriority = 0, isTitle = true)
                add("Panera Bread", "panera", licensePriority = 0, isTitle = true)
                add("Panera", "panera", licensePriority = 0, isTitle = false)
                add("REI", "rei", licensePriority = 0, isTitle = true)
                add("REI Co-op", "rei", licensePriority = 0, isTitle = false)
                add("REI Co Op", "rei", licensePriority = 0, isTitle = false)
                supplementaryMerchantAliases["costco"].orEmpty().forEach {
                    add(it, "costco", licensePriority = 0, isTitle = false)
                }
            }

        assertEquals("target", matcher.findPayeeIcon("Target"))
        assertNull(matcher.findPayeeIcon("Target Date Fund Fees"))
        assertNull(matcher.findPayeeIcon("Shell Beach Rentals"))
        assertNull(matcher.findPayeeIcon("Royal Dutch Shell"))
        assertNull(matcher.findPayeeIcon("Amazon Web Services"))
        assertNull(matcher.findPayeeIcon("Amazon Pay refund"))
        assertNull(matcher.findPayeeIcon("Uber One"))
        assertNull(matcher.findPayeeIcon("Paris Hilton"))
        assertNull(matcher.findPayeeIcon("Savings transfer"))
        assertNull(matcher.findPayeeIcon("Education fees"))
        assertNull(matcher.findPayeeIcon("Search history"))
        assertNull(matcher.findPayeeIcon("Mobile service"))
        assertNull(matcher.findPayeeIcon("Metro card"))
        assertEquals("costco", matcher.findPayeeIcon("Costco Gas"))
        assertEquals("costco", matcher.findPayeeIcon("Costco Gas #0463"))
        assertNull(matcher.findPayeeIcon("Costco Gasoline"))
        assertEquals("panera", matcher.findPayeeIcon("Panera Bread"))
        assertEquals("panera", matcher.findPayeeIcon("Panera #0421"))
        assertNull(matcher.findPayeeIcon("Panera Bread Catering"))
        assertEquals("rei", matcher.findPayeeIcon("REI"))
        assertEquals("rei", matcher.findPayeeIcon("REI Co-op Store 31"))
        assertEquals("walmart", matcher.findPayeeIcon("Walmart #1234"))
        assertEquals("walmart", matcher.findPayeeIcon("Walmart location 231"))
        assertEquals("whole-foods", matcher.findPayeeIcon("Whole Foods Store 42"))
    }

    @Test
    fun accountRowsUseTypeIconsAndKnownIssuerArtwork() {
        assertEquals(Lucide.PiggyBank, accountTypeIcon("Household Savings"))
        assertEquals(Lucide.Landmark, accountTypeIcon("Household Checking"))
        assertEquals(Lucide.CreditCard, accountTypeIcon("Rewards Card"))
        assertEquals(Lucide.CreditCard, accountTypeIcon("Primary", "Credit"))
        assertEquals(Lucide.HandCoins, accountTypeIcon("Primary", "Loans"))
        assertEquals(Lucide.ChartNoAxesCombined, accountTypeIcon("Primary", "Tracking"))
        assertEquals(Lucide.WalletCards, accountTypeIcon("Primary", "Cash"))

        val issuerMatcher =
            MerchantAliasMatcher<String>().apply {
                add("Chase", "chase", licensePriority = 0, isTitle = true)
                add("American Express", "american-express", licensePriority = 0, isTitle = true)
                supplementaryAccountIssuerAliases["american-express"].orEmpty().forEach {
                    add(it, "american-express", licensePriority = 0, isTitle = false)
                }
            }
        assertEquals("chase", issuerMatcher.findAccountIcon("Chase Checking"))
        assertEquals("chase", issuerMatcher.findAccountIcon("Household Checking - JPMorgan Chase"))
        assertEquals("american-express", issuerMatcher.findAccountIcon("Amex Blue Cash"))
        assertNull(issuerMatcher.findAccountIcon("Household Checking"))

        assertTrue(
            accountIssuerSlugs.containsAll(
                setOf("bank-of-america", "chase", "american-express", "barclays", "hdfc-bank", "icici-bank"),
            ),
        )
    }

    @Test
    fun closedAccountsSortAfterEveryOpenAccountGroup() {
        val closedOrder = accountGroupOrder("Closed")

        assertTrue(closedOrder > accountGroupOrder("Other"))
        assertTrue(closedOrder > accountGroupOrder("Investment"))
        assertTrue(closedOrder > accountGroupOrder("Unrecognized account group"))
    }

    @Test
    fun accountSubtitleOnlyAddsTheOffBudgetTagWhenItAddsInformation() {
        assertEquals(
            "Off budget · Not synced yet",
            accountListSubtitle(LiveAccountSummary("off", "Tracking", 100, "investment", true), null),
        )
        assertTrue(
            accountListSubtitle(LiveAccountSummary("off", "Tracking", 100, "investment", true), 0)
                .startsWith("Off budget · Last synced "),
        )
        assertTrue(
            accountListSubtitle(LiveAccountSummary("on", "Checking", 100, "checking", false), 0)
                .orEmpty()
                .startsWith("Last synced "),
        )
        assertEquals(
            "Not synced yet",
            accountListSubtitle(LiveAccountSummary("on", "Checking", 100, "checking", false), null),
        )
        assertEquals(
            "Closed",
            accountListSubtitle(LiveAccountSummary("closed", "Closed", 0, "savings", false, closed = true), 0),
        )
    }

    @Test
    fun accountCardsExposeTypeAndSignedBalanceAccessibly() {
        assertEquals("Checking", accountListTypeLabel("checking"))
        assertEquals("Credit", accountListTypeLabel("credit-card"))
        assertEquals("Other", accountListTypeLabel(null))
        assertEquals("Negative balance, -\$315.45", accountBalanceAccessibilityDescription(-31545, "-\$315.45"))
        assertEquals("Balance unavailable", accountBalanceAccessibilityDescription(null, "Unavailable"))
    }

    @Test
    fun closedAccountsSortAfterOpenAccountsAndAccountSortOrderIsPreserved() {
        val ordered =
            sortAccountsForList(
                listOf(
                    LiveAccountSummary("closed", "Closed Account", 0, "checking", false, closed = true, sortOrder = 0.0),
                    LiveAccountSummary("later", "Later Checking", 0, "checking", false, sortOrder = 20.0),
                    LiveAccountSummary("earlier", "Earlier Checking", 0, "checking", false, sortOrder = 10.0),
                ),
            )

        assertEquals(listOf("earlier", "later", "closed"), ordered.map { it.id })
        assertEquals(Int.MAX_VALUE, accountGroupOrder("Closed"))
    }

    @Test
    fun accountsFollowActualsSortOrderIncludingFractionalValuesNotTheirNames() {
        val records =
            listOf(
                """{"id":"b","name":"AMEX","type":"checking","sort_order":2048.5}""",
                """{"id":"a","name":"Zeta","type":"checking","sort_order":1024}""",
                """{"id":"c","name":"Bank","type":"checking","sort_order":2048.25}""",
            ).map { json ->
                app.tandemforactual.data.database.BudgetRecordEntity(
                    budgetId = "budget-1",
                    recordType = "account",
                    recordId =
                        Json
                            .parseToJsonElement(json)
                            .jsonObject["id"]!!
                            .jsonPrimitive.content,
                    remoteJson = json,
                    remoteRevision = "r1",
                    localJson = null,
                    localDeleted = false,
                    remoteDeleted = false,
                    syncGeneration = "g1",
                    recordDate = null,
                    accountId = null,
                    categoryId = null,
                    payeeId = null,
                    amountMinor = null,
                    budgetMonth = null,
                    displayName =
                        Json
                            .parseToJsonElement(json)
                            .jsonObject["name"]!!
                            .jsonPrimitive.content,
                )
            }

        val ordered =
            sortAccountsForList(
                liveAccountSummaries(records),
            )

        assertEquals(listOf("a", "c", "b"), ordered.map { it.id })
    }
}
