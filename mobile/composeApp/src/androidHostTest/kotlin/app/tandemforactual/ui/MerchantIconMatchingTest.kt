package app.tandemforactual.ui

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
}
