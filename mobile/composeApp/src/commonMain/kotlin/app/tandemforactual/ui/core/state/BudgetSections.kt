@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.state
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.vector.ImageVector
import app.tandemforactual.ui.design.templates.financeIcon
import com.composables.icons.lucide.Baby
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.Bike
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.BriefcaseBusiness
import com.composables.icons.lucide.Building2
import com.composables.icons.lucide.BusFront
import com.composables.icons.lucide.CarFront
import com.composables.icons.lucide.Carrot
import com.composables.icons.lucide.Cat
import com.composables.icons.lucide.Coffee
import com.composables.icons.lucide.Dog
import com.composables.icons.lucide.Droplets
import com.composables.icons.lucide.Dumbbell
import com.composables.icons.lucide.Flower2
import com.composables.icons.lucide.Fuel
import com.composables.icons.lucide.Gamepad2
import com.composables.icons.lucide.Gift
import com.composables.icons.lucide.GraduationCap
import com.composables.icons.lucide.HeartHandshake
import com.composables.icons.lucide.HeartPulse
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Leaf
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music2
import com.composables.icons.lucide.NotepadText
import com.composables.icons.lucide.PawPrint
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.Pill
import com.composables.icons.lucide.Plane
import com.composables.icons.lucide.PlugZap
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Shirt
import com.composables.icons.lucide.ShoppingBag
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Sofa
import com.composables.icons.lucide.Stethoscope
import com.composables.icons.lucide.TrainFront
import com.composables.icons.lucide.Tv
import com.composables.icons.lucide.Utensils
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.Wrench
import com.composables.icons.lucide.Zap

internal fun AppState.isBudgetSectionCollapsed(sectionKey: String): Boolean =
    collapsedBudgetSections.split('\u001f').any { it == sectionKey }

internal fun AppState.toggleBudgetSection(sectionKey: String) {
    val collapsed = collapsedBudgetSections.split('\u001f').filter(String::isNotBlank).toMutableSet()
    if (!collapsed.add(sectionKey)) collapsed.remove(sectionKey)
    collapsedBudgetSections = collapsed.joinToString("\u001f")
}

internal fun AppState.collapseBudgetSections(sectionKeys: Iterable<String>) {
    val collapsed = collapsedBudgetSections.split('\u001f').filter(String::isNotBlank).toMutableSet()
    collapsed.addAll(sectionKeys)
    collapsedBudgetSections = collapsed.joinToString("\u001f")
}

internal fun AppState.isCategoryPinned(categoryId: String): Boolean =
    pinnedCategoryIds.split('\u001f').any { it == categoryPinKey(categoryId) || it == categoryId }

internal val categoryIconChoices =
    listOf(
        "Automatic",
        "Home",
        "Rent",
        "Furniture",
        "Maintenance",
        "Utilities",
        "Water",
        "Groceries",
        "Dining",
        "Coffee",
        "Shopping",
        "Clothing",
        "Travel",
        "Car",
        "Fuel",
        "Transit",
        "Train",
        "Bike",
        "Health",
        "Medicine",
        "Dental",
        "Pet",
        "Cat",
        "Dog",
        "Family",
        "Education",
        "Books",
        "Gifts",
        "Income",
        "Paycheck",
        "Savings",
        "Money",
        "Insurance",
        "Phone",
        "Internet",
        "Electricity",
        "Entertainment",
        "Music",
        "Games",
        "Fitness",
        "Garden",
        "Plants",
        "Charity",
        "Business",
        "Taxes",
        "Other",
    )

internal fun categoryIconVector(choice: String): ImageVector? =
    when (choice) {
        "Home" -> Lucide.House
        "Food" -> Lucide.Carrot
        "Transport" -> Lucide.CarFront
        "Rent" -> Lucide.Building2
        "Furniture" -> Lucide.Sofa
        "Maintenance" -> Lucide.Wrench
        "Utilities" -> Lucide.PlugZap
        "Water" -> Lucide.Droplets
        "Groceries" -> Lucide.Carrot
        "Dining" -> Lucide.Utensils
        "Coffee" -> Lucide.Coffee
        "Shopping" -> Lucide.ShoppingBag
        "Clothing" -> Lucide.Shirt
        "Travel" -> Lucide.Plane
        "Car" -> Lucide.CarFront
        "Fuel" -> Lucide.Fuel
        "Transit" -> Lucide.BusFront
        "Train" -> Lucide.TrainFront
        "Bike" -> Lucide.Bike
        "Health" -> Lucide.HeartPulse
        "Medicine" -> Lucide.Pill
        "Dental" -> Lucide.Stethoscope
        "Pet" -> Lucide.PawPrint
        "Cat" -> Lucide.Cat
        "Dog" -> Lucide.Dog
        "Family" -> Lucide.Baby
        "Education" -> Lucide.GraduationCap
        "Books" -> Lucide.BookOpen
        "Gifts" -> Lucide.Gift
        "Income" -> Lucide.BriefcaseBusiness
        "Paycheck" -> Lucide.Banknote
        "Savings" -> Lucide.PiggyBank
        "Money" -> Lucide.WalletCards
        "Insurance" -> Lucide.ShieldCheck
        "Phone" -> Lucide.Smartphone
        "Internet" -> Lucide.Wifi
        "Electricity" -> Lucide.Zap
        "Entertainment" -> Lucide.Tv
        "Music" -> Lucide.Music2
        "Games" -> Lucide.Gamepad2
        "Fitness" -> Lucide.Dumbbell
        "Garden" -> Lucide.Flower2
        "Plants" -> Lucide.Leaf
        "Charity" -> Lucide.HeartHandshake
        "Business" -> Lucide.Landmark
        "Taxes" -> Lucide.Receipt
        "Other" -> Lucide.NotepadText
        else -> null
    }

internal fun AppState.categoryIconChoice(categoryId: String): String? {
    val key = categoryPinKey(categoryId)
    return categoryIconOverrides
        .split('\u001f')
        .firstNotNullOfOrNull { entry ->
            val delimiter = entry.indexOf('\u001e')
            if (delimiter < 0 || entry.substring(0, delimiter) != key) null else entry.substring(delimiter + 1)
        }
}

internal fun AppState.setCategoryIconChoice(
    categoryId: String,
    choice: String,
) {
    val key = categoryPinKey(categoryId)
    val choices =
        categoryIconOverrides
            .split('\u001f')
            .mapNotNull { entry ->
                val delimiter = entry.indexOf('\u001e')
                if (delimiter < 0) null else entry.substring(0, delimiter) to entry.substring(delimiter + 1)
            }.toMap()
            .toMutableMap()
    if (choice == "Automatic") choices.remove(key) else choices[key] = choice
    categoryIconOverrides = choices.entries.joinToString("\u001f") { (id, icon) -> "$id\u001e$icon" }
}

internal fun AppState.categoryIconFor(
    categoryId: String,
    categoryName: String,
): ImageVector = categoryIconChoice(categoryId)?.let(::categoryIconVector) ?: financeIcon(categoryName) ?: Lucide.Receipt

internal fun AppState.togglePinnedCategory(categoryId: String) {
    val pinned = pinnedCategoryIds.split('\u001f').filter(String::isNotBlank).toMutableSet()
    val key = categoryPinKey(categoryId)
    if (key in pinned || categoryId in pinned) {
        pinned.remove(key)
        pinned.remove(categoryId)
    } else {
        pinned.add(key)
    }
    pinnedCategoryIds = pinned.joinToString("\u001f")
}

internal fun AppState.categoryPinKey(categoryId: String): String = "$selectedBudgetId:$categoryId"
