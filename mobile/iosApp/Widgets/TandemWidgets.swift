import SwiftUI
import WidgetKit

@main
struct TandemWidgets: WidgetBundle {
    var body: some Widget {
        LeftToSpendWidget()
        ReadyToAssignWidget()
        QuickAddWidget()
    }
}

/// Shown instead of amounts when there's no budget, it's protected, or this month's summary hasn't been written yet.
struct WidgetStatusMessage: View {
    let status: WidgetSnapshot.Status

    var body: some View {
        Text(Self.text(for: status))
            .font(.footnote)
            .foregroundStyle(.secondary)
    }

    static func text(for status: WidgetSnapshot.Status) -> String {
        switch status {
        case .noBudget: return "Open Tandem to choose a budget."
        case .hidden: return "Open Tandem to see your budget."
        case .outdated: return "Open Tandem to update this month."
        case .ready: return ""
        }
    }
}

struct WidgetHeader: View {
    let title: String
    let showsAdd: Bool

    var body: some View {
        HStack {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
                .lineLimit(1)
            Spacer(minLength: 4)
            if showsAdd {
                Link(destination: WidgetSnapshotStore.addTransactionLink) {
                    Image(systemName: "plus")
                        .font(.caption.weight(.bold))
                        .frame(width: 28, height: 28)
                        .background(Circle().fill(.tint.opacity(0.16)))
                }
                .accessibilityLabel("Add transaction")
            }
        }
    }
}

struct QuickAddWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "QuickAdd", provider: QuickAddProvider()) { _ in
            QuickAddView()
                .containerBackground(.fill.tertiary, for: .widget)
                .widgetURL(WidgetSnapshotStore.addTransactionLink)
        }
        .configurationDisplayName("Quick add")
        .description("Start a new transaction in one tap.")
        .supportedFamilies([.systemSmall, .accessoryCircular])
    }
}

struct QuickAddEntry: TimelineEntry {
    let date: Date
}

struct QuickAddProvider: TimelineProvider {
    func placeholder(in context: Context) -> QuickAddEntry { QuickAddEntry(date: Date()) }

    func getSnapshot(in context: Context, completion: @escaping (QuickAddEntry) -> Void) {
        completion(QuickAddEntry(date: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<QuickAddEntry>) -> Void) {
        completion(Timeline(entries: [QuickAddEntry(date: Date())], policy: .never))
    }
}

private struct QuickAddView: View {
    @Environment(\.widgetFamily) private var family

    var body: some View {
        if family == .accessoryCircular {
            Image(systemName: "plus")
                .font(.title2.weight(.bold))
                .accessibilityLabel("Add transaction")
        } else {
            VStack(spacing: 8) {
                Image(systemName: "plus")
                    .font(.title.weight(.bold))
                    .frame(width: 56, height: 56)
                    .background(Circle().fill(.tint.opacity(0.16)))
                Text("Add transaction")
                    .font(.footnote.weight(.semibold))
            }
            .accessibilityElement(children: .combine)
        }
    }
}
