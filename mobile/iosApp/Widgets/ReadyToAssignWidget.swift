import SwiftUI
import WidgetKit

struct ReadyToAssignWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ReadyToAssign", provider: SnapshotProvider()) { entry in
            ReadyToAssignView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Ready to assign")
        .description("See what’s ready to assign and spent this month.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryInline])
    }
}

struct SnapshotEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
}

/// Reads the summary the app saved; the widget refreshes when the app saves a new one and at the start of each month.
struct SnapshotProvider: TimelineProvider {
    func placeholder(in context: Context) -> SnapshotEntry { SnapshotEntry(date: Date(), snapshot: .preview) }

    func getSnapshot(in context: Context, completion: @escaping (SnapshotEntry) -> Void) {
        completion(context.isPreview ? placeholder(in: context) : current())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SnapshotEntry>) -> Void) {
        let entry = current()
        completion(Timeline(entries: [entry], policy: .after(WidgetSnapshotStore.nextMonthStart(after: entry.date))))
    }

    private func current() -> SnapshotEntry {
        SnapshotEntry(date: Date(), snapshot: WidgetSnapshotStore.load(from: WidgetSnapshotStore.fileURL()))
    }
}

private struct ReadyToAssignView: View {
    @Environment(\.widgetFamily) private var family
    let entry: SnapshotEntry

    private var status: WidgetSnapshot.Status { entry.snapshot?.status(on: entry.date) ?? .noBudget }
    private var ready: String { entry.snapshot?.readyToAssign ?? "—" }
    private var negative: Bool { entry.snapshot?.readyToAssignNegative == true }

    var body: some View {
        switch family {
        case .accessoryInline:
            Text(status == .ready ? "\(ready) to assign" : WidgetStatusMessage.text(for: status))
        case .accessoryRectangular:
            VStack(alignment: .leading) {
                Text("Ready to assign").font(.caption)
                if status == .ready {
                    Text(ready).font(.headline)
                } else {
                    WidgetStatusMessage(status: status)
                }
            }
        default:
            VStack(alignment: .leading, spacing: 6) {
                WidgetHeader(title: entry.snapshot?.budgetName ?? "Tandem", showsAdd: family == .systemMedium)
                Spacer(minLength: 0)
                if status == .ready {
                    amounts
                } else {
                    WidgetStatusMessage(status: status)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var amounts: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 2) {
                Text(ready)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(negative ? Color.red : Color.primary)
                    .minimumScaleFactor(0.6)
                    .lineLimit(1)
                Text("Ready to assign").font(.caption).foregroundStyle(.secondary)
            }
            if family == .systemMedium {
                Spacer()
                VStack(alignment: .leading, spacing: 2) {
                    Text(entry.snapshot?.spent ?? "—")
                        .font(.headline)
                        .lineLimit(1)
                    Text("Spent this month").font(.caption).foregroundStyle(.secondary)
                }
            }
        }
    }
}

extension WidgetSnapshot {
    static let preview = WidgetSnapshot(
        version: 1,
        month: WidgetSnapshotStore.monthId(for: Date()),
        state: "ready",
        budgetName: "Household",
        readyToAssign: "$240.00",
        readyToAssignNegative: false,
        spent: "$1,182.40",
        categories: [
            Category(id: "groceries", name: "Groceries", group: "Living", amount: "$182.40", text: "$182.40", overspent: false),
            Category(id: "eating-out", name: "Eating out", group: "Fun", amount: "-$12.50", text: "$12.50 over", overspent: true),
            Category(id: "fuel", name: "Fuel", group: "Transport", amount: "$64.00", text: "$64.00", overspent: false),
        ]
    )
}
