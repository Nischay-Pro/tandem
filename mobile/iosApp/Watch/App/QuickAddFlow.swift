import SwiftUI

/// Amount, then category, then an optional recent payee, then a check of everything with the account it goes to.
struct QuickAddFlow: View {
    let summary: WatchSummary
    let onAdd: (WatchAddition) -> Void

    @State private var path: [Step] = []
    @State private var amount: WatchAmountInput
    @State private var category: WatchSummary.Choice? = nil
    @State private var payee: String? = nil
    @State private var accountId: String?

    enum Step: Hashable {
        case category
        case payee
        case confirm
    }

    init(summary: WatchSummary, onAdd: @escaping (WatchAddition) -> Void) {
        self.summary = summary
        self.onAdd = onAdd
        _amount = State(initialValue: WatchAmountInput(fractionDigits: summary.quickAdd?.fractionDigits ?? 2))
        _accountId = State(initialValue: summary.quickAdd?.accounts?.first?.id)
    }

    private var categories: [WatchSummary.Choice] {
        (summary.categories ?? []).map { WatchSummary.Choice(id: $0.id, name: $0.name) }
    }

    private var recentCategories: [WatchSummary.Choice] { summary.quickAdd?.recentCategories ?? [] }
    private var payees: [String] { summary.quickAdd?.payees ?? [] }

    var body: some View {
        NavigationStack(path: $path) {
            AmountKeypad(amount: $amount) {
                path.append(nextStep(after: nil))
            }
            .navigationTitle("Amount")
            .navigationDestination(for: Step.self) { step in
                switch step {
                case .category: categoryStep
                case .payee: payeeStep
                case .confirm: confirmStep
                }
            }
        }
    }

    private func nextStep(after step: Step?) -> Step {
        if step == nil, !categories.isEmpty || !recentCategories.isEmpty { return .category }
        if step == nil || step == .category, !payees.isEmpty { return .payee }
        return .confirm
    }

    private var categoryStep: some View {
        List {
            Section {
                ForEach(categories) { choice in
                    choiceButton(choice.name, selected: category == choice) {
                        category = choice
                        path.append(nextStep(after: .category))
                    }
                }
            }
            if !recentCategories.isEmpty {
                Section("Recent") {
                    ForEach(recentCategories) { choice in
                        choiceButton(choice.name, selected: category == choice) {
                            category = choice
                            path.append(nextStep(after: .category))
                        }
                    }
                }
            }
            Section {
                choiceButton("No category", selected: false) {
                    category = nil
                    path.append(nextStep(after: .category))
                }
                .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Category")
    }

    private var payeeStep: some View {
        List {
            ForEach(payees, id: \.self) { name in
                choiceButton(name, selected: payee == name) {
                    payee = name
                    path.append(.confirm)
                }
            }
            choiceButton("Skip", selected: false) {
                payee = nil
                path.append(.confirm)
            }
            .foregroundStyle(.secondary)
        }
        .navigationTitle("Payee")
    }

    private var confirmStep: some View {
        let accounts = summary.quickAdd?.accounts ?? []
        return List {
            Section {
                Text("−" + amount.display)
                    .font(.system(size: 30, weight: .semibold, design: .rounded))
                    .monospacedDigit()
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let category {
                    Label(category.name, systemImage: "tag")
                }
                if let payee {
                    Label(payee, systemImage: "person")
                }
            }
            if accounts.count > 1 {
                Picker("Account", selection: $accountId) {
                    ForEach(accounts) { account in
                        Text(account.name).tag(Optional(account.id))
                    }
                }
            } else if let account = accounts.first {
                Label(account.name, systemImage: "creditcard")
            }
            Button {
                guard let plain = amount.plainAmount else { return }
                onAdd(WatchAddition(
                    amount: plain,
                    categoryId: category?.id,
                    categoryName: category?.name,
                    payee: payee,
                    accountId: accountId
                ))
            } label: {
                Image(systemName: "checkmark")
                    .font(.title3.weight(.bold))
                    .frame(maxWidth: .infinity)
            }
            .tint(.tandemAccent)
            .buttonStyle(.borderedProminent)
            .listRowBackground(Color.clear)
            .accessibilityLabel("Add transaction")
        }
        .navigationTitle("Add")
    }

    private func choiceButton(_ title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(title).lineLimit(2)
                Spacer(minLength: 4)
                if selected {
                    Image(systemName: "checkmark").foregroundStyle(Color.tandemAccent)
                }
            }
        }
    }
}

/// A calculator-style keypad sized for the watch, with dictation or scribble one tap away.
private struct AmountKeypad: View {
    @Binding var amount: WatchAmountInput
    let onNext: () -> Void

    private let keys = ["1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫"]
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: 3)

    var body: some View {
        VStack(spacing: 4) {
            HStack {
                Text(amount.display)
                    .font(.system(size: 26, weight: .semibold, design: .rounded))
                    .monospacedDigit()
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .contentTransition(.numericText())
                    .animation(.snappy, value: amount.display)
                Spacer(minLength: 2)
                Button(action: onNext) {
                    Image(systemName: "arrow.right")
                        .font(.body.weight(.bold))
                }
                .buttonStyle(.borderedProminent)
                .tint(.tandemAccent)
                .disabled(!amount.isValid)
                .frame(width: 44)
                .accessibilityLabel("Next")
            }
            LazyVGrid(columns: columns, spacing: 4) {
                ForEach(keys, id: \.self) { key in
                    Button {
                        amount.press(key)
                    } label: {
                        Text(key)
                            .font(.system(.title3, design: .rounded).weight(.medium))
                            .frame(maxWidth: .infinity, minHeight: 30)
                    }
                    .buttonStyle(.bordered)
                    .disabled(key == "." && amount.fractionDigits == 0)
                    .accessibilityLabel(key == "⌫" ? "Delete" : key)
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                TextFieldLink(prompt: Text("Say an amount")) {
                    Image(systemName: "mic")
                } onSubmit: { spoken in
                    _ = amount.set(spoken: spoken)
                }
                .accessibilityLabel("Dictate amount")
            }
        }
    }
}
