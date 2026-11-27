import Foundation

/// The small summary the iPhone sends the watch, already formatted. It never holds more than its state allows:
/// nothing without a budget, only the budget's name while it's locked, and names without amounts while they're hidden.
struct WatchSummary: Codable, Equatable {
    enum State: String, Codable {
        case none
        case locked
        case hidden
        case ready
    }

    struct Transaction: Codable, Equatable, Hashable {
        let payee: String
        let amount: String
        let date: String

        enum CodingKeys: String, CodingKey {
            case payee = "n"
            case amount = "a"
            case date = "d"
        }
    }

    struct Category: Codable, Equatable, Identifiable, Hashable {
        let id: String
        let name: String
        let left: String?
        let overspent: Bool?
        let spentPercent: Int?
        let budgeted: String?
        let spent: String?
        let transactions: [Transaction]?

        enum CodingKeys: String, CodingKey {
            case id = "i"
            case name = "n"
            case left = "l"
            case overspent = "o"
            case spentPercent = "p"
            case budgeted = "bu"
            case spent = "sp"
            case transactions = "x"
        }

        var isOverspent: Bool { overspent == true }

        /// How full the category's bar is, from 0 to 1.
        var progress: Double { min(max(Double(spentPercent ?? 0) / 100, 0), 1) }
    }

    struct Bill: Codable, Equatable, Hashable {
        let name: String
        let date: String
        let amount: String?

        enum CodingKeys: String, CodingKey {
            case name = "n"
            case date = "d"
            case amount = "a"
        }
    }

    struct Choice: Codable, Equatable, Identifiable, Hashable {
        let id: String
        let name: String

        enum CodingKeys: String, CodingKey {
            case id = "i"
            case name = "n"
        }
    }

    struct QuickAdd: Codable, Equatable {
        let open: Bool
        let fractionDigits: Int?
        let accounts: [Choice]?
        let payees: [String]?
        let recentCategories: [Choice]?

        enum CodingKeys: String, CodingKey {
            case open = "on"
            case fractionDigits = "f"
            case accounts = "a"
            case payees = "p"
            case recentCategories = "k"
        }
    }

    let version: Int
    let state: State
    var generatedAt: Int64
    let budgetId: String?
    let budgetName: String?
    let syncedAt: Int64?
    let leftToSpend: String?
    let leftToSpendNegative: Bool?
    let toBudget: String?
    let toBudgetNegative: Bool?
    let categories: [Category]?
    let bills: [Bill]?
    let quickAdd: QuickAdd?

    enum CodingKeys: String, CodingKey {
        case version = "v"
        case state = "s"
        case generatedAt = "g"
        case budgetId = "k"
        case budgetName = "b"
        case syncedAt = "y"
        case leftToSpend = "l"
        case leftToSpendNegative = "lo"
        case toBudget = "r"
        case toBudgetNegative = "ro"
        case categories = "c"
        case bills = "u"
        case quickAdd = "q"
    }

    /// WatchConnectivity's application context is meant to stay small; the phone never sends more than this.
    static let maxBytes = 2048
    static let hiddenAmount = "•••"
    static let contextKey = "summary"

    static func decode(_ data: Data?) -> WatchSummary? {
        guard let data, data.count <= maxBytes,
              let summary = try? JSONDecoder().decode(WatchSummary.self, from: data), summary.version == 1
        else { return nil }
        return summary
    }

    var showsAmounts: Bool { state == .ready }
    var canQuickAdd: Bool { quickAdd?.open == true && state != .none }

    /// An amount as the watch shows it: dots while amounts are hidden, a dash when it isn't known yet.
    func amount(_ value: String?) -> String {
        switch state {
        case .ready: return value ?? "–"
        case .hidden: return Self.hiddenAmount
        case .locked, .none: return "–"
        }
    }

    /// The line shown instead of numbers, or nil when there are numbers to show.
    var statusMessage: String? {
        switch state {
        case .none: return "Open Tandem on your phone to choose a budget."
        case .locked: return "Locked · open Tandem on your phone"
        case .hidden, .ready: return nil
        }
    }

    /// "Synced 10:42", from the last sync the summary came from, or when it was made.
    func syncedText(locale: Locale = .current, timeZone: TimeZone = .current) -> String {
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.timeZone = timeZone
        formatter.dateStyle = .none
        formatter.timeStyle = .short
        let millis = syncedAt ?? generatedAt
        return "Synced \(formatter.string(from: Date(timeIntervalSince1970: TimeInterval(millis) / 1000)))"
    }

    /// Bills due on [date], for the Smart Stack to raise the bills complication that day.
    func billsDue(on date: Date, calendar: Calendar = .current) -> [Bill] {
        let day = Self.dayId(for: date, calendar: calendar)
        return (bills ?? []).filter { $0.date == day }
    }

    static func dayId(for date: Date, calendar: Calendar = .current) -> String {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }

    /// The same summary apart from when it was made, so an unchanged one isn't sent again.
    func sameContent(as other: WatchSummary) -> Bool {
        var mine = self
        mine.generatedAt = other.generatedAt
        return mine == other
    }
}

/// The iPhone's rule for what reaches the watch. A summary made while Tandem is closed never replaces numbers the watch
/// got after an unlock of the same budget; a new budget, sign-out, or anything else always goes through.
enum WatchSummaryOutbox {
    static func shouldSend(previous: Data?, next: Data) -> Bool {
        guard let upcoming = WatchSummary.decode(next) else { return false }
        guard let earlier = WatchSummary.decode(previous) else { return true }
        if upcoming.sameContent(as: earlier) { return false }
        let keepsUnlockedNumbers = upcoming.state == .locked &&
            (earlier.state == .ready || earlier.state == .hidden) &&
            upcoming.budgetId == earlier.budgetId
        return !keepsUnlockedNumbers
    }
}

/// Where the watch keeps the last summary, in its App Group so the complications can read it too.
enum WatchSummaryStore {
    static let appGroup = "group.app.tandemforactual.ios"
    static let fileName = "watch-summary.json"

    static func fileURL(fileManager: FileManager = .default) -> URL? {
        fileManager.containerURL(forSecurityApplicationGroupIdentifier: appGroup)?.appendingPathComponent(fileName)
    }

    @discardableResult
    static func save(_ data: Data, to url: URL?) -> Bool {
        guard let url, WatchSummary.decode(data) != nil else { return false }
        do {
            try data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            return true
        } catch {
            return false
        }
    }

    static func load(from url: URL?) -> WatchSummary? {
        guard let url else { return nil }
        return WatchSummary.decode(try? Data(contentsOf: url))
    }
}

/// Dates in the summary are year-month-day; these turn them into "Today", "Tomorrow" or "Dec 2".
enum WatchDates {
    static func parse(_ day: String, calendar: Calendar = .current) -> Date? {
        let parts = day.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
    }

    static func isToday(_ day: String, now: Date = Date(), calendar: Calendar = .current) -> Bool {
        day == WatchSummary.dayId(for: now, calendar: calendar)
    }

    static func short(_ day: String) -> String {
        guard let date = parse(day) else { return day }
        return date.formatted(.dateTime.month(.abbreviated).day())
    }

    static func relative(_ day: String, now: Date = Date(), calendar: Calendar = .current) -> String {
        guard let date = parse(day, calendar: calendar) else { return day }
        if calendar.isDate(date, inSameDayAs: now) { return "Today" }
        if let tomorrow = calendar.date(byAdding: .day, value: 1, to: now), calendar.isDate(date, inSameDayAs: tomorrow) {
            return "Tomorrow"
        }
        return short(day)
    }
}
