import SwiftUI
import WidgetKit

@main
struct TandemComplications: WidgetBundle {
    var body: some Widget {
        LeftToSpendComplication()
        ToBudgetComplication()
        NextBillComplication()
    }
}

private let accent = Color(red: 0xB9 / 255, green: 0xA8 / 255, blue: 0xFF / 255)

struct SummaryEntry: TimelineEntry {
    let date: Date
    let summary: WatchSummary?
    let relevance: TimelineEntryRelevance?
}

/// Reads the summary the watch app saved; complications never reach the iPhone or the network themselves.
struct SummaryProvider: TimelineProvider {
    func placeholder(in context: Context) -> SummaryEntry {
        SummaryEntry(date: Date(), summary: nil, relevance: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (SummaryEntry) -> Void) {
        completion(entry(at: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SummaryEntry>) -> Void) {
        let now = Date()
        let tomorrow = Calendar.current.startOfDay(for: now.addingTimeInterval(24 * 60 * 60))
        completion(Timeline(entries: [entry(at: now)], policy: .after(tomorrow)))
    }

    /// Bills due today raise the next-bill complication in the Smart Stack for the rest of the day.
    private func entry(at date: Date) -> SummaryEntry {
        let summary = WatchSummaryStore.load(from: WatchSummaryStore.fileURL())
        let dueToday = !(summary?.billsDue(on: date) ?? []).isEmpty
        let endOfDay = Calendar.current.startOfDay(for: date.addingTimeInterval(24 * 60 * 60)).timeIntervalSince(date)
        return SummaryEntry(
            date: date,
            summary: summary,
            relevance: TimelineEntryRelevance(score: dueToday ? 100 : 1, duration: dueToday ? endOfDay : 0)
        )
    }
}

struct LeftToSpendComplication: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "WatchLeftToSpend", provider: SummaryProvider()) { entry in
            LeftToSpendComplicationView(summary: entry.summary)
                .containerBackground(.clear, for: .widget)
        }
        .configurationDisplayName("Left to spend")
        .description("What’s left this month across your budget.")
        .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryInline, .accessoryCorner])
    }
}

private struct LeftToSpendComplicationView: View {
    @Environment(\.widgetFamily) private var family
    let summary: WatchSummary?

    private var amount: String { summary?.headlineAmount ?? "–" }
    private var locked: Bool { summary?.state != .ready && summary?.state != .hidden }
    private var title: String { summary?.headlineShort ?? "Left to spend" }

    var body: some View {
        switch family {
        case .accessoryCircular:
            ZStack {
                AccessoryWidgetBackground()
                VStack(spacing: 0) {
                    Image(systemName: locked ? "lock.fill" : "cart")
                        .font(.caption2)
                        .foregroundStyle(accent)
                    Text(locked ? "–" : amount)
                        .font(.system(.caption, design: .rounded).weight(.semibold))
                        .minimumScaleFactor(0.4)
                        .lineLimit(1)
                        .privacySensitive()
                }
                .padding(2)
            }
            .accessibilityLabel("\(summary?.headlineLabel ?? title) \(amount)")
        case .accessoryCorner:
            Image(systemName: locked ? "lock.fill" : "cart")
                .font(.title3)
                .foregroundStyle(accent)
                .widgetLabel {
                    Text(locked ? "Tandem" : amount)
                        .privacySensitive()
                }
        case .accessoryInline:
            Text(locked ? "Tandem · Locked" : "\(amount) \(summary?.leadsWithPerDay == true ? "a day" : "left")")
                .privacySensitive()
        default:
            RectangularSummary(title: title, amount: amount, negative: summary?.leftToSpendNegative == true, summary: summary)
        }
    }
}

struct ToBudgetComplication: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "WatchToBudget", provider: SummaryProvider()) { entry in
            RectangularSummary(
                title: "To budget",
                amount: entry.summary?.amount(entry.summary?.toBudget) ?? "–",
                negative: entry.summary?.toBudgetNegative == true,
                summary: entry.summary
            )
            .containerBackground(.clear, for: .widget)
        }
        .configurationDisplayName("To budget")
        .description("Money still waiting for a category.")
        .supportedFamilies([.accessoryRectangular])
    }
}

private struct RectangularSummary: View {
    let title: String
    let amount: String
    let negative: Bool
    let summary: WatchSummary?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title)
                .font(.caption2)
                .foregroundStyle(accent)
            if let message = summary?.statusMessage {
                Text(message)
                    .font(.footnote)
                    .lineLimit(2)
            } else {
                Text(amount)
                    .font(.system(.title3, design: .rounded).weight(.semibold))
                    .monospacedDigit()
                    .minimumScaleFactor(0.5)
                    .lineLimit(1)
                    .foregroundStyle(negative && summary?.showsAmounts == true ? Color.red : .primary)
                    .privacySensitive()
                Text(summary?.syncedText() ?? "")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

struct NextBillComplication: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "WatchNextBill", provider: SummaryProvider()) { entry in
            NextBillView(summary: entry.summary, date: entry.date)
                .containerBackground(.clear, for: .widget)
        }
        .configurationDisplayName("Next bill")
        .description("Your next bill, raised in the Smart Stack on the day it’s due.")
        .supportedFamilies([.accessoryRectangular])
    }
}

private struct NextBillView: View {
    let summary: WatchSummary?
    let date: Date

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Next bill")
                .font(.caption2)
                .foregroundStyle(accent)
            if let bill = summary?.bills?.first {
                Text(bill.name)
                    .font(.headline)
                    .lineLimit(1)
                Text(WatchDates.isToday(bill.date, now: date) ? "Due today" : WatchDates.relative(bill.date, now: date))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                if let amount = summary?.amount(bill.amount), summary?.showsAmounts == true {
                    Text(amount)
                        .font(.caption)
                        .monospacedDigit()
                        .privacySensitive()
                }
            } else {
                Text(summary?.statusMessage ?? "No bills due soon.")
                    .font(.footnote)
                    .lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}
