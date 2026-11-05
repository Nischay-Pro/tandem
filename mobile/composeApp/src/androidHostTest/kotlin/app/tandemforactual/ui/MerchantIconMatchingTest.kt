package app.tandemforactual.ui

import com.composables.icons.lucide.CarFront
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ShieldCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MerchantIconMatchingTest {
    @Test
    fun categoryIconsUseWholeWordsAndSpecificPhrases() {
        assertEquals(Lucide.CarFront, financeIcon("Petrol"))
        assertNull(financeIcon("Current account"))
        assertNull(financeIcon("Home Depot"))
        assertEquals(Lucide.ShieldCheck, financeIcon("Home insurance"))
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
            }

        assertEquals("target", matcher.findPayeeIcon("Target"))
        assertNull(matcher.findPayeeIcon("Target Date Fund Fees"))
        assertNull(matcher.findPayeeIcon("Shell Beach Rentals"))
        assertNull(matcher.findPayeeIcon("Royal Dutch Shell"))
        assertNull(matcher.findPayeeIcon("Amazon Web Services"))
        assertNull(matcher.findPayeeIcon("Amazon Pay refund"))
        assertNull(matcher.findPayeeIcon("Uber One"))
        assertNull(matcher.findPayeeIcon("Paris Hilton"))
        assertNull(matcher.findPayeeIcon("Costco Gas"))
        assertEquals("walmart", matcher.findPayeeIcon("Walmart #1234"))
        assertEquals("walmart", matcher.findPayeeIcon("Walmart location 231"))
        assertEquals("whole-foods", matcher.findPayeeIcon("Whole Foods Store 42"))
    }
}
