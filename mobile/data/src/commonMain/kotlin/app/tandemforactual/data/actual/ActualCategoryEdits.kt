package app.tandemforactual.data.actual

fun ActualBudgetEdits.createCategory(
    groupId: String,
    name: String,
): String =
    writer.batch {
        val group = groups().firstOrNull { it["id"] == groupId && !truthy(it["hidden"]) } ?: fail("Choose an active category group")
        if (categories().any { it["cat_group"] == groupId && (it["name"] as String).trim().lowercase() == name.trim().lowercase() }) {
            fail("A category with this name already exists in that group")
        }
        val peers =
            db.query(
                "SELECT id, sort_order FROM categories WHERE cat_group = ? AND tombstone = 0 ORDER BY sort_order, id",
                groupId,
            )
        val (updates, order) = shove(orders(peers), peers.firstOrNull()?.get("id") as String?)
        updates.forEach { writer.update("categories", it.id, mapOf("sort_order" to it.sortOrder)) }
        val id =
            writer.insert(
                "categories",
                linkedMapOf(
                    "name" to name.trim(),
                    "cat_group" to groupId,
                    "is_income" to (if (truthy(group["is_income"])) 1L else 0L),
                    "hidden" to 0L,
                    "sort_order" to order,
                ),
            )
        writer.insert("category_mapping", linkedMapOf("id" to id, "transferId" to id))
        id
    }

fun ActualBudgetEdits.renameCategory(
    id: String,
    name: String,
) = writer.batch {
    val category = categories().firstOrNull { it["id"] == id } ?: fail("Category does not exist in this budget")
    if (category["name"] != name) writer.update("categories", id, mapOf("name" to name.trim()))
}

fun ActualBudgetEdits.createCategoryGroup(
    name: String,
    isIncome: Boolean,
): String =
    writer.batch {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > 100) fail("Category group names must be between 1 and 100 characters")
        if (groups().any { (it["name"] as String).trim().lowercase() == clean.lowercase() }) {
            fail(
                "A category group with this name already exists",
            )
        }
        val last =
            db
                .first(
                    "SELECT sort_order FROM category_groups WHERE tombstone = 0 ORDER BY sort_order DESC, id DESC LIMIT 1",
                )?.get("sort_order") as Number?
        writer.insert(
            "category_groups",
            linkedMapOf(
                "name" to clean,
                "is_income" to (if (isIncome) 1L else 0L),
                "hidden" to 0L,
                "sort_order" to (last?.toDouble() ?: 0.0) + SORT_INCREMENT,
            ),
        )
    }

fun ActualBudgetEdits.manageCategoryGroup(
    groupId: String,
    action: String,
    name: String?,
    targetId: String?,
) = writer.batch {
    val all = groups()
    val group = all.firstOrNull { it["id"] == groupId } ?: fail("Category group does not exist in this budget")
    when (action) {
        "rename" -> {
            val clean = name?.trim().orEmpty()
            if (clean.isEmpty() || clean.length > 100) fail("Category group names must be between 1 and 100 characters")
            if (all.any {
                    it["id"] != groupId && (it["name"] as String).trim().lowercase() == clean.lowercase()
                }
            ) {
                fail("A category group with this name already exists")
            }
            writer.update("category_groups", groupId, mapOf("name" to clean))
        }

        "hide", "show" -> {
            writer.update("category_groups", groupId, mapOf("hidden" to (if (action == "hide") 1L else 0L)))
        }

        "delete" -> {
            if (truthy(group["is_income"]) && all.none { it["id"] != groupId && truthy(it["is_income"]) }) {
                fail("A budget needs at least one income category group")
            }
            val cats = categories()
            val inGroup = cats.filter { it["cat_group"] == groupId }
            if (targetId != null) {
                val destination = cats.firstOrNull { it["id"] == targetId && !truthy(it["hidden"]) }
                val destinationGroup = all.firstOrNull { it["id"] == destination?.get("cat_group") && !truthy(it["hidden"]) }
                if (destination == null ||
                    destinationGroup == null ||
                    inGroup.any { it["id"] == destination["id"] || truthy(it["is_income"]) != truthy(destination["is_income"]) }
                ) {
                    fail("Choose a visible category outside this group with the same income type")
                }
                doTransfer(inGroup.map { it["id"] as String }, targetId)
            }
            db
                .query(
                    "SELECT * FROM categories WHERE cat_group = ?",
                    groupId,
                ).forEach { deleteCategoryRow(it["id"] as String, targetId) }
            writer.delete("category_groups", groupId)
        }

        else -> {
            if (targetId == groupId) fail("Choose a different category group position")
            if (targetId != null && all.none { it["id"] == targetId }) fail("Choose a category group in this budget")
            val peers = db.query("SELECT id, sort_order FROM category_groups WHERE tombstone = 0 ORDER BY sort_order, id")
            val (updates, order) = shove(orders(peers), targetId)
            updates.forEach { writer.update("category_groups", it.id, mapOf("sort_order" to it.sortOrder)) }
            writer.update("category_groups", groupId, mapOf("sort_order" to order))
        }
    }
}

fun ActualBudgetEdits.manageCategory(
    categoryId: String,
    action: String,
    targetCategoryId: String?,
    targetGroupId: String?,
) = writer.batch {
    val cats = categories()
    val category = cats.firstOrNull { it["id"] == categoryId } ?: fail("Category does not exist in this budget")
    if (action == "hide" || action == "show") {
        writer.update("categories", categoryId, mapOf("hidden" to (if (action == "hide") 1L else 0L)))
        return@batch
    }
    val currentGroup = category["cat_group"] as String
    val destinationGroup = targetGroupId ?: currentGroup
    if (targetGroupId != null && destinationGroup != currentGroup) {
        val group =
            groups().firstOrNull { it["id"] == destinationGroup && !truthy(it["hidden"]) }
                ?: fail("Choose a visible category group in this budget")
        if (truthy(group["is_income"]) != truthy(category["is_income"])) fail("Income categories must stay in an income group")
        if (targetCategoryId != null) fail("A category moved to another group starts at the end of that group")
        moveCategory(categoryId, destinationGroup, null)
        return@batch
    }
    if (targetCategoryId == categoryId) fail("Choose a different category position")
    if (targetCategoryId != null &&
        cats.firstOrNull { it["id"] == targetCategoryId }?.get("cat_group") != currentGroup
    ) {
        fail("Choose a category in the same group")
    }
    moveCategory(categoryId, currentGroup, targetCategoryId)
}

internal fun ActualBudgetEdits.moveCategory(
    id: String,
    groupId: String,
    targetId: String?,
) {
    val peers = db.query("SELECT id, sort_order FROM categories WHERE cat_group = ? AND tombstone = 0 ORDER BY sort_order, id", groupId)
    val (updates, order) = shove(orders(peers), targetId)
    updates.forEach { writer.update("categories", it.id, mapOf("sort_order" to it.sortOrder)) }
    writer.update("categories", id, linkedMapOf("sort_order" to order, "cat_group" to groupId))
}

fun ActualBudgetEdits.deleteCategory(
    id: String,
    transferId: String?,
) = writer.batch {
    val cats = categories()
    val category = cats.firstOrNull { it["id"] == id } ?: return@batch
    if (transferId != null) {
        val destination = cats.firstOrNull { it["id"] == transferId }
        if (destination == null || transferId == id) fail("Choose a different category in this budget")
        if (truthy(destination["is_income"]) !=
            truthy(category["is_income"])
        ) {
            fail("Transactions can only move to a category of the same type")
        }
        if (!truthy(category["is_income"])) doTransfer(listOf(id), transferId)
    }
    deleteCategoryRow(id, transferId)
}

private fun ActualBudgetEdits.deleteCategoryRow(
    id: String,
    transferId: String?,
) {
    if (transferId != null) {
        db.query("SELECT * FROM category_mapping WHERE transferId = ?", id).forEach {
            writer.update("category_mapping", it["id"] as String, mapOf("transferId" to transferId))
        }
        writer.update("category_mapping", id, mapOf("transferId" to transferId))
    }
    writer.delete("categories", id)
}

/** Actual's `doTransfer`: adds the deleted categories' budgeted amounts to the destination in every created month. */
private fun ActualBudgetEdits.doTransfer(
    categoryIds: List<String>,
    transferId: String,
) {
    for (month in createdMonths()) {
        val total = categoryIds.sumOf { getBudget(month, it) }
        setBudget(month, transferId, total + getBudget(month, transferId))
    }
}
