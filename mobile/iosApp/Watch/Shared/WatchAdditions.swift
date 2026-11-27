import Foundation

/// A transaction typed on the watch. Its id is made once on the watch, so sending it again never adds it twice.
struct WatchAddition: Codable, Equatable, Identifiable {
    enum Status: String, Codable {
        case waiting
        case failed
    }

    let id: String
    let amount: String
    let categoryId: String?
    let categoryName: String?
    let payee: String?
    let accountId: String?
    let createdAt: Date
    var status: Status = .waiting
    var message: String?

    static let kind = "watch-addition"

    /// What travels to the iPhone; only the fields the iPhone needs to add it.
    var payload: [String: Any] {
        var payload: [String: Any] = ["kind": Self.kind, "id": id, "amount": amount]
        payload["category"] = categoryId
        payload["payee"] = payee
        payload["account"] = accountId
        return payload
    }

    init(
        id: String = UUID().uuidString,
        amount: String,
        categoryId: String? = nil,
        categoryName: String? = nil,
        payee: String? = nil,
        accountId: String? = nil,
        createdAt: Date = Date()
    ) {
        self.id = id
        self.amount = amount
        self.categoryId = categoryId
        self.categoryName = categoryName
        self.payee = payee
        self.accountId = accountId
        self.createdAt = createdAt
    }

    init?(payload: [String: Any]) {
        guard payload["kind"] as? String == Self.kind,
              let id = payload["id"] as? String, !id.isEmpty,
              let amount = payload["amount"] as? String, !amount.isEmpty
        else { return nil }
        self.init(
            id: id,
            amount: amount,
            categoryId: payload["category"] as? String,
            payee: payload["payee"] as? String,
            accountId: payload["account"] as? String
        )
    }
}

/// The iPhone's answer for one addition: added, or the reason it wasn't.
struct WatchAdditionReply: Equatable {
    static let kind = "watch-addition-reply"

    let id: String
    let added: Bool
    let message: String

    var payload: [String: Any] { ["kind": Self.kind, "id": id, "added": added, "message": message] }

    init(id: String, added: Bool, message: String) {
        self.id = id
        self.added = added
        self.message = message
    }

    init?(payload: [String: Any]) {
        guard payload["kind"] as? String == Self.kind,
              let id = payload["id"] as? String,
              let added = payload["added"] as? Bool
        else { return nil }
        self.init(id: id, added: added, message: payload["message"] as? String ?? "")
    }
}

/// Additions the watch is still waiting on. Each is kept until the iPhone answers, so nothing typed is lost while the
/// iPhone is out of reach, and the same id is never queued twice.
struct WatchAdditionQueue: Codable, Equatable {
    private(set) var items: [WatchAddition] = []

    var waiting: [WatchAddition] { items.filter { $0.status == .waiting } }
    var failed: [WatchAddition] { items.filter { $0.status == .failed } }

    /// Adds [item] unless one with its id is already queued; true when it was added.
    @discardableResult
    mutating func enqueue(_ item: WatchAddition) -> Bool {
        guard !items.contains(where: { $0.id == item.id }) else { return false }
        items.append(item)
        return true
    }

    /// Takes the iPhone's answer: an added item leaves the queue, a refused one stays with the reason until dismissed.
    @discardableResult
    mutating func apply(_ reply: WatchAdditionReply) -> WatchAddition? {
        guard let index = items.firstIndex(where: { $0.id == reply.id }) else { return nil }
        if reply.added {
            return items.remove(at: index)
        }
        items[index].status = .failed
        items[index].message = reply.message
        return items[index]
    }

    mutating func dismiss(id: String) {
        items.removeAll { $0.id == id }
    }

    /// Waiting items WatchConnectivity no longer holds, which need sending again.
    func needingResend(outstanding: Set<String>) -> [WatchAddition] {
        waiting.filter { !outstanding.contains($0.id) }
    }

    static func load(from defaults: UserDefaults, key: String = "watch-additions") -> WatchAdditionQueue {
        guard let data = defaults.data(forKey: key),
              let queue = try? JSONDecoder().decode(WatchAdditionQueue.self, from: data)
        else { return WatchAdditionQueue() }
        return queue
    }

    func save(to defaults: UserDefaults, key: String = "watch-additions") {
        if let data = try? JSONEncoder().encode(self) {
            defaults.set(data, forKey: key)
        }
    }
}

/// What's typed on the keypad, kept as plain digits with a dot so the iPhone reads it the same in every locale.
struct WatchAmountInput: Equatable {
    private(set) var text = ""
    let fractionDigits: Int

    init(fractionDigits: Int = 2) {
        self.fractionDigits = min(max(fractionDigits, 0), 2)
    }

    static let maxWholeDigits = 9

    mutating func press(_ key: String) {
        switch key {
        case "⌫":
            if !text.isEmpty { text.removeLast() }
        case ".":
            guard fractionDigits > 0, !text.contains(".") else { return }
            text = text.isEmpty ? "0." : text + "."
        default:
            guard key.count == 1, let digit = key.first, digit.isASCII, digit.isNumber else { return }
            let parts = text.split(separator: ".", omittingEmptySubsequences: false)
            if parts.count == 2 {
                guard parts[1].count < fractionDigits else { return }
            } else {
                guard parts.first.map({ $0.count < Self.maxWholeDigits }) ?? true else { return }
                if text == "0" { text = "" }
            }
            text.append(digit)
        }
    }

    /// Reads a spoken or typed amount such as "12.40" or "12,40"; false when it isn't one.
    mutating func set(spoken: String) -> Bool {
        let cleaned = spoken.filter { $0.isNumber || $0 == "." || $0 == "," }.replacingOccurrences(of: ",", with: ".")
        var next = WatchAmountInput(fractionDigits: fractionDigits)
        cleaned.forEach { next.press(String($0)) }
        guard next.isValid else { return false }
        self = next
        return true
    }

    var isValid: Bool {
        guard let value = Decimal(string: text, locale: Locale(identifier: "en_US_POSIX")) else { return false }
        return value > 0
    }

    /// Money out, signed and with a dot, such as "-12.40"; nil until an amount above zero is typed.
    var plainAmount: String? {
        guard isValid else { return nil }
        let trimmed = text.hasSuffix(".") ? String(text.dropLast()) : text
        return "-" + trimmed
    }

    /// The amount as typed, for the keypad's display.
    var display: String { text.isEmpty ? "0" : text }
}
