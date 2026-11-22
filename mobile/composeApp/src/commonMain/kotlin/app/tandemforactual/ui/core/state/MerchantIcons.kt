@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.state
import androidx.compose.foundation.layout.size
import app.tandemforactual.ui.design.icons.MerchantAliasMatcher
import app.tandemforactual.ui.design.icons.accountIssuerSlugs
import app.tandemforactual.ui.design.icons.supplementaryAccountIssuerAliases
import app.tandemforactual.ui.design.icons.supplementaryMerchantAliases
import app.tandemforactual.ui.feature.activity.ActivitySearchFilter
import app.tandemforactual.ui.feature.activity.ActivitySearchRecord
import app.tandemforactual.ui.generated.resources.Res
import app.tandemforactual.ui.generated.resources.allDrawableResources
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.DrawableResource

internal data class MerchantIconNotice(
    val title: String,
    val license: String,
    val registryUrl: String,
    val brandUrl: String,
    val resourceName: String,
    val resource: DrawableResource,
)

internal data class MerchantIconMatcherPack(
    private val matcher: MerchantAliasMatcher<DrawableResource>,
    private val accountMatcher: MerchantAliasMatcher<DrawableResource>,
) {
    fun iconFor(payee: String): DrawableResource? = matcher.findPayeeIcon(payee)

    fun accountIconFor(accountName: String): DrawableResource? = accountMatcher.findAccountIcon(accountName)
}

internal data class ActivitySearchResultCache(
    val budgetId: String,
    val query: String,
    val filters: List<ActivitySearchFilter>,
    val sourceRecords: List<ActivitySearchRecord>,
    val indices: List<Int>,
    val suggestions: List<ActivitySearchFilter>,
)

internal data class MerchantIconCatalog(
    val notices: List<MerchantIconNotice>,
)

internal suspend fun loadMerchantIconMatcherPack(): MerchantIconMatcherPack {
    val index = Res.readBytes("files/merchant-icon-matcher.tsv").decodeToString()
    val resources = Res.allDrawableResources
    val matcher = MerchantAliasMatcher<DrawableResource>()
    val resourcesBySlug = mutableMapOf<String, DrawableResource>()
    val prioritiesBySlug = mutableMapOf<String, Int>()
    val accountMatcher = MerchantAliasMatcher<DrawableResource>()

    for (line in index.lineSequence()) {
        if (line.isBlank()) continue
        val fields = line.split('\t', limit = 5)
        if (fields.size != 5) continue
        val slug = fields[0]
        val resourceName = fields[1]
        val licensePriority = fields[2].toIntOrNull() ?: continue
        val isTitle = fields[3] == "1"
        val label = fields[4]
        val resource = resources[resourceName] ?: continue
        resourcesBySlug[slug] = resource
        prioritiesBySlug[slug] = licensePriority
        matcher.add(label, resource, licensePriority, isTitle)
        if (slug in accountIssuerSlugs) accountMatcher.add(label, resource, licensePriority, isTitle)
    }

    for ((slug, aliases) in supplementaryMerchantAliases) {
        val resource = resourcesBySlug[slug] ?: continue
        val licensePriority = prioritiesBySlug[slug] ?: continue
        aliases.forEach { alias -> matcher.add(alias, resource, licensePriority, isTitle = false) }
    }

    for ((slug, aliases) in supplementaryAccountIssuerAliases) {
        val resource = resourcesBySlug[slug] ?: continue
        val licensePriority = prioritiesBySlug[slug] ?: continue
        aliases.forEach { alias -> accountMatcher.add(alias, resource, licensePriority, isTitle = false) }
    }

    return MerchantIconMatcherPack(matcher, accountMatcher)
}

internal suspend fun loadMerchantIconCatalog(): MerchantIconCatalog {
    val catalog = Json.parseToJsonElement(Res.readBytes("files/merchant-icon-catalog.json").decodeToString()).jsonObject
    val resources = Res.allDrawableResources
    val notices =
        catalog["icons"]
            ?.jsonArray
            .orEmpty()
            .mapNotNull { element ->
                val icon = element.jsonObject
                val resourceName = icon["resourceName"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val resource = resources[resourceName] ?: return@mapNotNull null
                val title = icon["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val license = icon["license"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val slug = icon["slug"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val registryUrl =
                    icon["registryUrl"]?.jsonPrimitive?.contentOrNull
                        ?: "https://thesvg.org/icon/$slug"
                val brandUrl = icon["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                MerchantIconNotice(title, license, registryUrl, brandUrl, resourceName, resource)
            }.sortedBy { it.title.lowercase() }

    return MerchantIconCatalog(notices)
}
