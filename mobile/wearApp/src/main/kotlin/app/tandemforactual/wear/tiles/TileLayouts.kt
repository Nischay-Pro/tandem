package app.tandemforactual.wear.tiles

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.DpProp
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.types.layoutString
import app.tandemforactual.watch.WatchCategory
import app.tandemforactual.watch.WatchState
import app.tandemforactual.watch.WatchSummary
import app.tandemforactual.watch.money
import app.tandemforactual.wear.MainActivity
import app.tandemforactual.wear.data.WatchText

private const val ARC_SWEEP = 300f
private const val ARC_START = -150f
private const val RINGS = 3
private const val RING_GAP = 8f
private const val SMALL_SCREEN_DP = 210
private const val TRACK = 0xFF332E45.toInt()
private const val ACCENT = 0xFFB9A8FF.toInt()
private const val OVER = 0xFFFFB4A9.toInt()

/** Left to spend in the middle, the first few categories as rings around it, and Add at the bottom edge. */
fun leftToSpendLayout(
    context: Context,
    device: DeviceParameters,
    summary: WatchSummary?,
): LayoutElement =
    materialScope(context, device) {
        primaryLayout(
            titleSlot = { text(WatchText.LEFT_TO_SPEND.layoutString) },
            mainSlot = {
                val message = message(summary)
                if (message != null || summary == null) {
                    text(message.orEmpty().layoutString, maxLines = 3, typography = Typography.BODY_MEDIUM)
                } else {
                    rings(summary, summary.categories.take(RINGS)) {
                        text(summary.money(summary.leftToSpendMinor).layoutString, typography = amountTypography())
                    }
                }
            },
            bottomSlot = addButton(context).takeIf { summary?.quickAdd == true },
        )
    }

/** The next bill due, with its amount and date. */
fun nextBillLayout(
    context: Context,
    device: DeviceParameters,
    summary: WatchSummary?,
): LayoutElement =
    materialScope(context, device) {
        val bill = summary?.bills?.firstOrNull()
        primaryLayout(
            titleSlot = { text("Next bill".layoutString) },
            mainSlot = {
                val message = message(summary) ?: if (bill == null) "Nothing due in the next month" else null
                if (message != null || bill == null || summary == null) {
                    text(message.orEmpty().layoutString, maxLines = 3, typography = Typography.BODY_MEDIUM)
                } else {
                    column(
                        text(bill.name.layoutString, maxLines = 1, typography = Typography.TITLE_MEDIUM),
                        text(WatchText.billAmount(summary, bill.amountMinor).layoutString, typography = Typography.DISPLAY_SMALL),
                        text(WatchText.billDate(bill.date).layoutString, typography = Typography.BODY_SMALL),
                    )
                }
            },
            bottomSlot = { textEdgeButton(onClick = openApp(context, quickAdd = false), labelContent = { text("Open".layoutString) }) },
        )
    }

/** Small round screens leave less room inside the rings, so the amount steps down a size there. */
private fun MaterialScope.amountTypography(): Int =
    if (deviceConfiguration.screenWidthDp < SMALL_SCREEN_DP) Typography.TITLE_SMALL else Typography.TITLE_LARGE

private fun addButton(context: Context): MaterialScope.() -> LayoutElement =
    { textEdgeButton(onClick = openApp(context, quickAdd = true), labelContent = { text("+ Add".layoutString) }) }

private fun message(summary: WatchSummary?): String? =
    when {
        summary == null -> WatchText.NO_BUDGET
        summary.state == WatchState.LOCKED -> WatchText.LOCKED
        else -> null
    }

private fun MaterialScope.rings(
    summary: WatchSummary,
    categories: List<WatchCategory>,
    centre: MaterialScope.() -> LayoutElement,
): LayoutElement {
    val box =
        LayoutElementBuilders.Box
            .Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(clickableModifiers(openApp(context, quickAdd = false)))
    categories.forEachIndexed { index, category ->
        val inset = dp(index * RING_GAP)
        val progress = if (summary.amountsShown) category.progress else 0f
        box.addContent(arc(inset, ARC_SWEEP, TRACK))
        if (progress > 0f) box.addContent(arc(inset, ARC_SWEEP * progress, if (category.over) OVER else ACCENT))
    }
    box.addContent(centre())
    return box.build()
}

private fun arc(
    inset: DpProp,
    sweep: Float,
    color: Int,
): LayoutElement {
    val line =
        LayoutElementBuilders.ArcLine
            .Builder()
            .setLength(degrees(sweep))
            .setThickness(dp(5f))
            .setColor(argb(color))
            .setStrokeCap(
                LayoutElementBuilders.StrokeCapProp
                    .Builder()
                    .setValue(LayoutElementBuilders.STROKE_CAP_ROUND)
                    .build(),
            ).build()
    val padding =
        ModifiersBuilders.Padding
            .Builder()
            .setAll(inset)
            .build()
    return LayoutElementBuilders.Arc
        .Builder()
        .setAnchorAngle(degrees(ARC_START))
        .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
        .setModifiers(
            ModifiersBuilders.Modifiers
                .Builder()
                .setPadding(padding)
                .build(),
        ).addContent(line)
        .build()
}

private fun column(vararg elements: LayoutElement): LayoutElement {
    val column =
        LayoutElementBuilders.Column
            .Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
    elements.forEach(column::addContent)
    return column.build()
}

private fun clickableModifiers(clickable: ModifiersBuilders.Clickable): ModifiersBuilders.Modifiers =
    ModifiersBuilders.Modifiers
        .Builder()
        .setClickable(clickable)
        .build()

/** Opens the watch app, straight at Quick add when [quickAdd]. */
fun openApp(
    context: Context,
    quickAdd: Boolean,
): ModifiersBuilders.Clickable {
    val activity =
        ActionBuilders.AndroidActivity
            .Builder()
            .setPackageName(context.packageName)
            .setClassName(MainActivity::class.java.name)
    if (quickAdd) {
        activity.addKeyToExtraMapping(
            MainActivity.EXTRA_OPEN,
            ActionBuilders.AndroidStringExtra
                .Builder()
                .setValue(MainActivity.OPEN_QUICK_ADD)
                .build(),
        )
    }
    return ModifiersBuilders.Clickable
        .Builder()
        .setId(if (quickAdd) "add" else "open")
        .setOnClick(
            ActionBuilders.LaunchAction
                .Builder()
                .setAndroidActivity(activity.build())
                .build(),
        ).build()
}
