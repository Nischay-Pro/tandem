import AppIntents
import ComposeApp
import Foundation

/// Opens a new transaction from Shortcuts, Siri or Spotlight, filled with the amount and payee given.
struct AddTransactionIntent: AppIntent {
    static let title: LocalizedStringResource = "Add transaction"
    static let openAppWhenRun = true

    @Parameter(title: "Amount")
    var amount: Double?

    @Parameter(title: "Payee")
    var payee: String?

    static var parameterSummary: some ParameterSummary {
        Summary("Add \(\.$amount) at \(\.$payee)")
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        _ = QuickActionEvents.shared.openLink(link: QuickAddLink.make(amount: amount, payee: payee))
        return .result()
    }
}

struct TandemAppShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: AddTransactionIntent(),
            phrases: [
                "Add a transaction in \(.applicationName)",
                "Log a purchase in \(.applicationName)",
            ]
        )
        AppShortcut(
            intent: LogTransactionIntent(),
            phrases: [
                "Add expense in \(.applicationName)",
                "Log an expense in \(.applicationName)",
                "Log a transaction in \(.applicationName)",
            ]
        )
        AppShortcut(
            intent: TransferMoneyIntent(),
            phrases: [
                "Transfer money in \(.applicationName)",
                "Move money between accounts in \(.applicationName)",
            ]
        )
        AppShortcut(
            intent: LeftToSpendIntent(),
            phrases: [
                "How much is left for \(\.$category) in \(.applicationName)",
                "How much is left to spend in \(.applicationName)",
            ]
        )
        AppShortcut(
            intent: AccountBalanceIntent(),
            phrases: [
                "What’s the balance of \(\.$account) in \(.applicationName)",
                "Account balance in \(.applicationName)",
            ]
        )
        AppShortcut(
            intent: OpenFeatureIntent(),
            phrases: [
                "Open \(\.$feature) in \(.applicationName)",
                "Show \(\.$feature) in \(.applicationName)",
            ]
        )
    }
}

/// The link Tandem opens for a new transaction; the amount is written with a dot and no grouping.
enum QuickAddLink {
    static func make(amount: Double?, payee: String?) -> String {
        var query: [String] = []
        if let amount, amount.isFinite, let text = format(abs(amount)) {
            query.append("amount=\(encode(text))")
        }
        if let payee = payee?.trimmingCharacters(in: .whitespacesAndNewlines), !payee.isEmpty {
            query.append("payee=\(encode(payee))")
        }
        let base = "app.tandemforactual://open/add-transaction"
        return query.isEmpty ? base : "\(base)?\(query.joined(separator: "&"))"
    }

    private static func format(_ amount: Double) -> String? {
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.numberStyle = .decimal
        formatter.usesGroupingSeparator = false
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 6
        return formatter.string(from: NSNumber(value: amount))
    }

    private static let unreserved = CharacterSet(
        charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
    )

    private static func encode(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: unreserved) ?? ""
    }
}
