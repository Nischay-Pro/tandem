import AppIntents
import ComposeApp
import Foundation
import SwiftUI
import UserNotifications

/// Adds a transaction straight to the open budget, under the same Settings › Automation rules as other automation apps.
struct LogTransactionIntent: AppIntent {
    static let title: LocalizedStringResource = "Log transaction"
    static let description: IntentDescription? = IntentDescription(
        "Adds a transaction to the open budget without opening Tandem. Turn on Allow automation apps to add transactions in Tandem’s settings first."
    )

    @Parameter(title: "Amount")
    var amount: Double

    @Parameter(title: "Type", default: .moneyOut)
    var direction: TransactionDirection

    @Parameter(title: "Account")
    var account: AccountEntity

    @Parameter(title: "Payee")
    var payee: String?

    @Parameter(title: "Category")
    var category: CategoryEntity?

    @Parameter(title: "Date")
    var date: Date?

    @Parameter(title: "Notes")
    var notes: String?

    @Parameter(title: "Cleared", default: false)
    var cleared: Bool

    @Parameter(title: "External ID", description: "Repeats with the same ID within 30 days add nothing.")
    var externalId: String?

    static var parameterSummary: some ParameterSummary {
        Summary("Log \(\.$direction) of \(\.$amount) in \(\.$account)") {
            \.$payee
            \.$category
            \.$date
            \.$notes
            \.$cleared
            \.$externalId
        }
    }

    func perform() async throws -> some IntentResult & ReturnsValue<String> & ProvidesDialog {
        guard let text = AutomationAmountText.make(amount, moneyIn: direction == .moneyIn) else {
            throw TandemIntentError(message: "Enter an amount other than zero.")
        }
        let added = try await TandemAutomation.add(
            amount: text,
            payee: payee,
            category: category?.id,
            account: account.id,
            date: date.map(AutomationDateText.make),
            notes: notes,
            cleared: cleared,
            externalId: externalId
        )
        if added.repeated {
            return .result(value: added.transactionId, dialog: "Already added, so nothing new was added.")
        }
        TandemAutomationNotifications.shared.added(added)
        if !added.sealed {
            IosBackgroundSyncScheduler.shared.requestAfterAutomation()
        }
        return .result(value: added.transactionId, dialog: "\(added.summary)")
    }
}

/// Moves money between two accounts in the open budget; Actual adds the matching transaction in the other account.
struct TransferMoneyIntent: AppIntent {
    static let title: LocalizedStringResource = "Transfer money"
    static let description: IntentDescription? = IntentDescription(
        "Moves money from one account to another in the open budget without opening Tandem. Turn on Allow automation apps to add transactions in Tandem’s settings first."
    )

    @Parameter(title: "Amount")
    var amount: Double

    @Parameter(title: "From account")
    var fromAccount: AccountEntity

    @Parameter(title: "To account")
    var toAccount: AccountEntity

    @Parameter(title: "Date")
    var date: Date?

    @Parameter(title: "Notes")
    var notes: String?

    @Parameter(title: "External ID", description: "Repeats with the same ID within 30 days add nothing.")
    var externalId: String?

    static var parameterSummary: some ParameterSummary {
        Summary("Transfer \(\.$amount) from \(\.$fromAccount) to \(\.$toAccount)") {
            \.$date
            \.$notes
            \.$externalId
        }
    }

    func perform() async throws -> some IntentResult & ReturnsValue<String> & ProvidesDialog {
        guard let text = AutomationAmountText.make(amount, moneyIn: false) else {
            throw TandemIntentError(message: "Enter an amount other than zero.")
        }
        let added = try await TandemAutomation.transfer(
            amount: text,
            fromAccount: fromAccount.id,
            toAccount: toAccount.id,
            date: date.map(AutomationDateText.make),
            notes: notes,
            externalId: externalId
        )
        if added.repeated {
            return .result(value: added.transactionId, dialog: "Already added, so nothing new was added.")
        }
        TandemAutomationNotifications.shared.added(added)
        if !added.sealed {
            IosBackgroundSyncScheduler.shared.requestAfterAutomation()
        }
        return .result(value: added.transactionId, dialog: "\(added.summary)")
    }
}

/// Reads a category's left to spend from this device's copy of the budget.
struct LeftToSpendIntent: AppIntent {
    static let title: LocalizedStringResource = "Left to spend"
    static let description: IntentDescription? = IntentDescription(
        "Shows how much is left this month in a category. Turn on Allow automation apps to read balances in Tandem’s settings first."
    )
    static let authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication

    @Parameter(title: "Category")
    var category: CategoryEntity

    static var parameterSummary: some ParameterSummary {
        Summary("Left to spend in \(\.$category)")
    }

    func perform() async throws -> some IntentResult & ReturnsValue<Double> & ProvidesDialog & ShowsSnippetView {
        let left = try await TandemAutomation.leftToSpend(categoryId: category.id, categoryName: category.name)
        return .result(
            value: Double(left.value) ?? 0,
            dialog: "\(left.sentence)",
            view: LeftToSpendSnippet(title: category.name, left: left)
        )
    }
}

/// Reads an account's balance from this device's copy of the budget.
struct AccountBalanceIntent: AppIntent {
    static let title: LocalizedStringResource = "Account balance"
    static let description: IntentDescription? = IntentDescription(
        "Shows an account’s balance, including changes not yet synced. Turn on Allow automation apps to read balances in Tandem’s settings first."
    )
    static let authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication

    @Parameter(title: "Account")
    var account: AccountEntity

    static var parameterSummary: some ParameterSummary {
        Summary("Balance of \(\.$account)")
    }

    func perform() async throws -> some IntentResult & ReturnsValue<Double> & ProvidesDialog {
        let balance = try await TandemAutomation.accountBalance(accountId: account.id, accountName: account.name)
        return .result(value: Double(balance.value) ?? 0, dialog: "\(balance.sentence)")
    }
}

/// Opens one of Tandem's main screens.
struct OpenFeatureIntent: AppIntent {
    static let title: LocalizedStringResource = "Open in Tandem"
    static let openAppWhenRun = true

    @Parameter(title: "Screen")
    var feature: TandemFeature

    static var parameterSummary: some ParameterSummary {
        Summary("Open \(\.$feature)")
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        _ = QuickActionEvents.shared.openLink(link: feature.link)
        return .result()
    }
}

enum TransactionDirection: String, AppEnum {
    case moneyOut
    case moneyIn

    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Type"
    static let caseDisplayRepresentations: [TransactionDirection: DisplayRepresentation] = [
        .moneyOut: "Money out",
        .moneyIn: "Money in",
    ]
}

enum TandemFeature: String, AppEnum, CaseIterable {
    case scanReceipt = "scan-receipt"
    case budgetCheckup = "budget-checkup"
    case accounts
    case reviewTransactions = "review-transactions"

    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Screen"
    static let caseDisplayRepresentations: [TandemFeature: DisplayRepresentation] = [
        .scanReceipt: "Scan receipt",
        .budgetCheckup: "Budget checkup",
        .accounts: "Accounts",
        .reviewTransactions: "Review transactions",
    ]

    var link: String { "app.tandemforactual://open/\(rawValue)" }
}

struct AccountEntity: AppEntity {
    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Account"
    static let defaultQuery = AccountQuery()

    let id: String
    let name: String

    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }
}

struct CategoryEntity: AppEntity {
    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Category"
    static let defaultQuery = CategoryQuery()

    let id: String
    let name: String

    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }
}

/// An encrypted budget offers no names, so a typed name stands in and is matched once the budget is unlocked.
struct AccountQuery: EntityStringQuery {
    func entities(for identifiers: [String]) async throws -> [AccountEntity] {
        let known = try await suggestedEntities()
        return identifiers.map { id in known.first { $0.id == id } ?? AccountEntity(id: id, name: id) }
    }

    func entities(matching string: String) async throws -> [AccountEntity] {
        let known = try await suggestedEntities()
        if known.isEmpty { return [AccountEntity(id: string, name: string)] }
        return known.filter { $0.name.localizedCaseInsensitiveContains(string) }
    }

    func suggestedEntities() async throws -> [AccountEntity] {
        await TandemAutomation.records().accounts.map { AccountEntity(id: $0.id, name: $0.name) }
    }
}

struct CategoryQuery: EntityStringQuery {
    func entities(for identifiers: [String]) async throws -> [CategoryEntity] {
        let known = try await suggestedEntities()
        return identifiers.map { id in known.first { $0.id == id } ?? CategoryEntity(id: id, name: id) }
    }

    func entities(matching string: String) async throws -> [CategoryEntity] {
        let known = try await suggestedEntities()
        if known.isEmpty { return [CategoryEntity(id: string, name: string)] }
        return known.filter { $0.name.localizedCaseInsensitiveContains(string) }
    }

    func suggestedEntities() async throws -> [CategoryEntity] {
        await TandemAutomation.records().categories.map { CategoryEntity(id: $0.id, name: $0.name) }
    }
}

struct LeftToSpendSnippet: View {
    let title: String
    let left: AutomationLeftToSpend

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(left.amountText)
                    .font(.system(.largeTitle, design: .rounded).weight(.bold))
                    .monospacedDigit()
                    .foregroundStyle(left.overspent ? Color.red : Color.primary)
                Text(left.overspent ? "over" : "left")
                    .font(.headline)
                    .foregroundStyle(.secondary)
            }
            Text(left.detail)
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
    }
}

struct TandemIntentError: Error, CustomLocalizedStringResourceConvertible {
    let message: String

    var localizedStringResource: LocalizedStringResource { "\(message)" }
}

/// The amount Shortcuts or Siri gave, as signed text with a dot and no grouping; the type, not the sign, says which way.
enum AutomationAmountText {
    static func make(_ amount: Double, moneyIn: Bool) -> String? {
        guard amount.isFinite, amount != 0 else { return nil }
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.numberStyle = .decimal
        formatter.usesGroupingSeparator = false
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 6
        guard let text = formatter.string(from: NSNumber(value: abs(amount))) else { return nil }
        return (moneyIn ? "+" : "-") + text
    }
}

enum AutomationDateText {
    static func make(_ date: Date) -> String {
        let parts = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }
}

/// Swift's view of the shared automation calls; each Kotlin call answers once through its callback.
enum TandemAutomation {
    static func add(
        amount: String,
        payee: String?,
        category: String?,
        account: String,
        date: String?,
        notes: String?,
        cleared: Bool,
        externalId: String?
    ) async throws -> IosAutomationAdded {
        try await withCheckedThrowingContinuation { continuation in
            IosAutomation.shared.add(
                amount: amount,
                payee: payee,
                category: category,
                account: account,
                date: date,
                notes: notes,
                cleared: cleared,
                externalId: externalId
            ) { added, failure in
                TandemAutomation.resume(continuation, with: added, failure)
            }
        }
    }

    static func transfer(
        amount: String,
        fromAccount: String,
        toAccount: String,
        date: String?,
        notes: String?,
        externalId: String?
    ) async throws -> IosAutomationAdded {
        try await withCheckedThrowingContinuation { continuation in
            IosAutomation.shared.transfer(
                amount: amount,
                fromAccount: fromAccount,
                toAccount: toAccount,
                date: date,
                notes: notes,
                externalId: externalId
            ) { added, failure in
                TandemAutomation.resume(continuation, with: added, failure)
            }
        }
    }

    static func accountBalance(accountId: String, accountName: String) async throws -> AutomationAccountBalance {
        try await withCheckedThrowingContinuation { continuation in
            IosAutomation.shared.accountBalance(accountId: accountId, accountName: accountName) { balance, failure in
                TandemAutomation.resume(continuation, with: balance, failure)
            }
        }
    }

    static func leftToSpend(categoryId: String, categoryName: String) async throws -> AutomationLeftToSpend {
        try await withCheckedThrowingContinuation { continuation in
            IosAutomation.shared.leftToSpend(categoryId: categoryId, categoryName: categoryName) { left, failure in
                TandemAutomation.resume(continuation, with: left, failure)
            }
        }
    }

    static func records() async -> AutomationRecords {
        await withCheckedContinuation { continuation in
            IosAutomation.shared.records { continuation.resume(returning: $0) }
        }
    }

    static func undo(budgetId: String, transactionId: String) async -> IosAutomationUndone {
        await withCheckedContinuation { continuation in
            IosAutomation.shared.undo(budgetId: budgetId, transactionId: transactionId) { continuation.resume(returning: $0) }
        }
    }

    private static func resume<T>(
        _ continuation: CheckedContinuation<T, Error>,
        with value: T?,
        _ failure: IosAutomationFailure?
    ) {
        if let value {
            continuation.resume(returning: value)
        } else {
            continuation.resume(throwing: TandemIntentError(message: failure?.message ?? "Tandem couldn’t open this budget."))
        }
    }
}

/// Every transaction Shortcuts or Siri adds gets a notification with Undo.
final class TandemAutomationNotifications: NSObject, UNUserNotificationCenterDelegate {
    static let shared = TandemAutomationNotifications()

    static let category = "tandem-automation-added"
    static let undoAction = "undo"

    private let center = UNUserNotificationCenter.current()

    func install() {
        let undo = UNNotificationAction(identifier: Self.undoAction, title: "Undo", options: [.authenticationRequired, .destructive])
        center.setNotificationCategories([
            UNNotificationCategory(identifier: Self.category, actions: [undo], intentIdentifiers: [], options: []),
        ])
        center.delegate = self
    }

    func added(_ added: IosAutomationAdded) {
        let content = UNMutableNotificationContent()
        content.title = added.sealed ? "Transaction saved" : added.summary
        content.body = added.sealed ? "From Shortcuts. Added when you open Tandem." : "Added by Shortcuts. Undo removes it."
        content.categoryIdentifier = Self.category
        content.userInfo = AutomationUndoInfo.make(budgetId: added.budgetId, transactionId: added.transactionId)
        center.add(UNNotificationRequest(identifier: added.transactionId, content: content, trigger: nil)) { error in
            if let error {
                NSLog("Tandem notification could not be scheduled: %@", String(describing: error))
            }
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler(notification.request.content.categoryIdentifier == Self.category ? [.banner, .list] : [])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        guard response.actionIdentifier == Self.undoAction,
              let info = AutomationUndoInfo.read(response.notification.request.content.userInfo)
        else {
            completionHandler()
            return
        }
        Task {
            let undone = await TandemAutomation.undo(budgetId: info.budgetId, transactionId: info.transactionId)
            if undone.syncNeeded {
                IosBackgroundSyncScheduler.shared.requestAfterAutomation()
            }
            let content = UNMutableNotificationContent()
            content.title = undone.message
            try? await center.add(UNNotificationRequest(identifier: info.transactionId, content: content, trigger: nil))
            completionHandler()
        }
    }
}

struct AutomationUndoInfo: Equatable {
    let budgetId: String
    let transactionId: String

    static func make(budgetId: String, transactionId: String) -> [String: String] {
        ["budget": budgetId, "transaction": transactionId]
    }

    static func read(_ userInfo: [AnyHashable: Any]) -> AutomationUndoInfo? {
        guard let budgetId = userInfo["budget"] as? String, !budgetId.isEmpty,
              let transactionId = userInfo["transaction"] as? String, !transactionId.isEmpty
        else { return nil }
        return AutomationUndoInfo(budgetId: budgetId, transactionId: transactionId)
    }
}
