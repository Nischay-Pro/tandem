package app.tandemforactual.ui.core.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Baby
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.Briefcase
import com.composables.icons.lucide.Building2
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Dog
import com.composables.icons.lucide.Fuel
import com.composables.icons.lucide.Gift
import com.composables.icons.lucide.GraduationCap
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.Plane
import com.composables.icons.lucide.ShoppingCart
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Stethoscope
import com.composables.icons.lucide.Utensils
import com.composables.icons.lucide.WalletCards

/** The symbols an account can show instead of its usual icon, by the name they're saved under. */
internal val accountIconChoices: List<Pair<String, ImageVector>> =
    listOf(
        "wallet" to Lucide.WalletCards,
        "bank" to Lucide.Landmark,
        "piggy-bank" to Lucide.PiggyBank,
        "credit-card" to Lucide.CreditCard,
        "cash" to Lucide.Banknote,
        "coins" to Lucide.Coins,
        "work" to Lucide.Briefcase,
        "business" to Lucide.Building2,
        "home" to Lucide.House,
        "travel" to Lucide.Plane,
        "study" to Lucide.GraduationCap,
        "shopping" to Lucide.ShoppingCart,
        "food" to Lucide.Utensils,
        "car" to Lucide.Fuel,
        "health" to Lucide.Stethoscope,
        "gift" to Lucide.Gift,
        "family" to Lucide.Baby,
        "pet" to Lucide.Dog,
        "phone" to Lucide.Smartphone,
    )

internal fun accountIconFor(name: String?): ImageVector? = accountIconChoices.firstOrNull { it.first == name }?.second
