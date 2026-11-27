import AppIntents
import SwiftUI
import WidgetKit

struct LeftToSpendWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "LeftToSpend", intent: LeftToSpendIntent.self, provider: LeftToSpendProvider()) { entry in
            LeftToSpendView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Left to spend")
        .description("See what’s left this month in the categories you pick.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .accessoryRectangular, .accessoryInline])
    }
}

struct CategoryEntity: AppEntity {
    static var typeDisplayRepresentation = TypeDisplayRepresentation(name: "Category")
    static var defaultQuery = CategoryQuery()

    let id: String
    let name: String
    let group: String

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(name)", subtitle: "\(group)")
    }
}

/// Lists the categories from the summary the app saved. A chosen category stays chosen while amounts are hidden.
struct CategoryQuery: EntityQuery {
    func entities(for identifiers: [CategoryEntity.ID]) async throws -> [CategoryEntity] {
        let known = Self.all()
        return identifiers.map { id in known.first { $0.id == id } ?? CategoryEntity(id: id, name: "Category", group: "") }
    }

    func suggestedEntities() async throws -> [CategoryEntity] {
        Self.all()
    }

    static func all() -> [CategoryEntity] {
        let categories = WidgetSnapshotStore.load(from: WidgetSnapshotStore.fileURL())?.categories ?? []
        return categories.map { CategoryEntity(id: $0.id, name: $0.name, group: $0.group) }
    }
}

struct LeftToSpendIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Left to spend"
    static var description = IntentDescription("Pick the categories this widget shows.")

    @Parameter(title: "Categories")
    var categories: [CategoryEntity]?

    init() {}
}

struct LeftToSpendEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
    let categoryIds: [String]
}

struct LeftToSpendProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> LeftToSpendEntry {
        LeftToSpendEntry(date: Date(), snapshot: .preview, categoryIds: WidgetSnapshot.preview.categories?.map(\.id) ?? [])
    }

    func snapshot(for configuration: LeftToSpendIntent, in context: Context) async -> LeftToSpendEntry {
        context.isPreview ? placeholder(in: context) : entry(for: configuration)
    }

    func timeline(for configuration: LeftToSpendIntent, in context: Context) async -> Timeline<LeftToSpendEntry> {
        let current = entry(for: configuration)
        return Timeline(entries: [current], policy: .after(WidgetSnapshotStore.nextMonthStart(after: current.date)))
    }

    private func entry(for configuration: LeftToSpendIntent) -> LeftToSpendEntry {
        LeftToSpendEntry(
            date: Date(),
            snapshot: WidgetSnapshotStore.load(from: WidgetSnapshotStore.fileURL()),
            categoryIds: configuration.categories?.map(\.id) ?? []
        )
    }
}

private struct LeftToSpendView: View {
    @Environment(\.widgetFamily) private var family
    let entry: LeftToSpendEntry

    private var status: WidgetSnapshot.Status { entry.snapshot?.status(on: entry.date) ?? .noBudget }
    private var categories: [WidgetSnapshot.Category] { entry.snapshot?.categories(withIds: entry.categoryIds) ?? [] }

    private var rows: Int {
        switch family {
        case .systemLarge: return 8
        case .systemMedium: return 3
        case .accessoryRectangular: return 2
        default: return 3
        }
    }

    var body: some View {
        if family == .accessoryInline {
            Text(inlineText)
        } else if status != .ready {
            content { WidgetStatusMessage(status: status) }
        } else if categories.isEmpty {
            content {
                Text("Touch and hold to edit the widget and pick categories.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        } else if categories.count == 1 && family != .accessoryRectangular {
            content { single(categories[0]) }
        } else {
            content {
                VStack(spacing: family == .accessoryRectangular ? 0 : 6) {
                    ForEach(categories.prefix(rows)) { row($0) }
                }
            }
        }
    }

    private var inlineText: String {
        guard status == .ready, let first = categories.first else { return WidgetStatusMessage.text(for: status) }
        return "\(first.name) \(first.text)"
    }

    private func content<Inner: View>(@ViewBuilder _ inner: () -> Inner) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            if family != .accessoryRectangular {
                WidgetHeader(title: "Left to spend", showsAdd: family != .systemSmall)
            }
            Spacer(minLength: 0)
            inner()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func single(_ category: WidgetSnapshot.Category) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(category.amount)
                .font(.title2.weight(.bold))
                .foregroundStyle(category.overspent ? Color.red : Color.primary)
                .minimumScaleFactor(0.6)
                .lineLimit(1)
            Text(category.overspent ? "\(category.name) · overspent" : category.name)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
    }

    private func row(_ category: WidgetSnapshot.Category) -> some View {
        HStack {
            Text(category.name)
                .lineLimit(1)
            Spacer(minLength: 8)
            Text(category.text)
                .fontWeight(.semibold)
                .foregroundStyle(category.overspent ? Color.red : Color.primary)
                .lineLimit(1)
        }
        .font(family == .accessoryRectangular ? .caption : .subheadline)
    }
}
