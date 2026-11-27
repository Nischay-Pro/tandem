import Foundation

/// The summary the app leaves in its App Group for Tandem's widgets: this month's totals and expense category balances,
/// already formatted, or only the budget's name while it is protected. Widgets never read the budget itself.
struct WidgetSnapshot: Codable, Equatable {
    struct Category: Codable, Equatable, Identifiable {
        let id: String
        let name: String
        let group: String
        let amount: String
        let text: String
        let overspent: Bool
    }

    enum Status: Equatable {
        case noBudget
        case hidden
        case outdated
        case ready
    }

    let version: Int
    let month: String
    let state: String
    let budgetName: String?
    let readyToAssign: String?
    let readyToAssignNegative: Bool?
    let spent: String?
    let categories: [Category]?

    /// What a widget can show on [date]: amounts from an earlier month wait until the app writes this month's.
    func status(on date: Date, calendar: Calendar = .current) -> Status {
        switch state {
        case "ready":
            return month == WidgetSnapshotStore.monthId(for: date, calendar: calendar) ? .ready : .outdated
        case "hidden":
            return .hidden
        default:
            return .noBudget
        }
    }

    /// The chosen categories in the order chosen; ones no longer in the budget are left out.
    func categories(withIds ids: [String]) -> [Category] {
        let all = categories ?? []
        var seen = Set<String>()
        return ids.compactMap { id in
            guard seen.insert(id).inserted else { return nil }
            return all.first { $0.id == id }
        }
    }
}

enum WidgetSnapshotStore {
    static let appGroup = "group.app.tandemforactual.ios"
    static let fileName = "widget-summary.json"
    static let addTransactionLink = URL(string: "app.tandemforactual://open/add-transaction")!

    static func fileURL(fileManager: FileManager = .default) -> URL? {
        fileManager.containerURL(forSecurityApplicationGroupIdentifier: appGroup)?.appendingPathComponent(fileName)
    }

    /// Saves the summary only when it reads back as one, so a widget never sees a half-written or unexpected file.
    static func save(_ json: String, to url: URL?) -> Bool {
        guard let url, let data = json.data(using: .utf8), decode(data) != nil else { return false }
        do {
            try data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            return true
        } catch {
            return false
        }
    }

    static func load(from url: URL?) -> WidgetSnapshot? {
        guard let url, let data = try? Data(contentsOf: url) else { return nil }
        return decode(data)
    }

    static func monthId(for date: Date, calendar: Calendar = .current) -> String {
        let parts = calendar.dateComponents([.year, .month], from: date)
        return String(format: "%04d-%02d", parts.year ?? 0, parts.month ?? 0)
    }

    static func nextMonthStart(after date: Date, calendar: Calendar = .current) -> Date {
        let start = calendar.dateInterval(of: .month, for: date)?.start ?? date
        return calendar.date(byAdding: .month, value: 1, to: start) ?? date.addingTimeInterval(24 * 60 * 60)
    }

    private static func decode(_ data: Data) -> WidgetSnapshot? {
        guard let snapshot = try? JSONDecoder().decode(WidgetSnapshot.self, from: data), snapshot.version == 1 else { return nil }
        return snapshot
    }
}
