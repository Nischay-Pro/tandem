import SwiftUI

/// Left to spend in large type, then the pinned categories with their bars. Overspending says "over" in words too.
struct GlanceView: View {
    @EnvironmentObject private var model: WatchModel

    var body: some View {
        List {
            if let summary = model.summary, summary.state == .ready || summary.state == .hidden {
                Section {
                    LeftToSpendHeader(summary: summary)
                }
                .listRowBackground(Color.clear)
                PendingAdditions()
                Section {
                    ForEach(summary.categories ?? []) { category in
                        NavigationLink(value: category) {
                            CategoryRow(category: category, summary: summary)
                        }
                    }
                }
            } else {
                Section {
                    StatusView(summary: model.summary)
                }
                .listRowBackground(Color.clear)
                PendingAdditions()
            }
        }
    }
}

private struct LeftToSpendHeader: View {
    let summary: WatchSummary

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(summary.headlineLabel)
                .font(.footnote)
                .foregroundStyle(.secondary)
            Text(summary.headlineAmount)
                .font(.system(size: 34, weight: .semibold, design: .rounded))
                .monospacedDigit()
                .minimumScaleFactor(0.5)
                .lineLimit(1)
                .foregroundStyle(summary.showsAmounts && summary.leftToSpendNegative == true ? Color.tandemOver : .primary)
                .privacySensitive()
            if let secondary = summary.headlineSecondary {
                Text(secondary)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .privacySensitive()
            }
            Text(summary.syncedText())
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

private struct CategoryRow: View {
    let category: WatchSummary.Category
    let summary: WatchSummary

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline) {
                Text(category.name)
                    .font(.headline)
                    .lineLimit(1)
                Spacer(minLength: 4)
            }
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(summary.amount(category.left))
                    .font(.system(.body, design: .rounded).weight(.medium))
                    .monospacedDigit()
                    .foregroundStyle(category.isOverspent ? Color.tandemOver : .primary)
                    .privacySensitive()
                if summary.showsAmounts {
                    Text(category.isOverspent ? "over" : "left")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            if summary.showsAmounts {
                ProgressView(value: category.progress)
                    .tint(category.isOverspent ? Color.tandemOver : Color.tandemAccent)
            }
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }
}

/// Shown instead of numbers: no budget yet, or a budget that stays locked until it's opened on the iPhone.
private struct StatusView: View {
    let summary: WatchSummary?

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: summary?.state == .locked ? "lock.fill" : "iphone")
                .font(.title2)
                .foregroundStyle(Color.tandemAccent)
            if let name = summary?.budgetName {
                Text(name)
                    .font(.headline)
            }
            Text(summary?.statusMessage ?? "Open Tandem on your phone to send your budget here.")
                .font(.footnote)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
    }
}

/// Additions still on their way to the iPhone, and any it couldn't take, with the reason.
private struct PendingAdditions: View {
    @EnvironmentObject private var model: WatchModel

    var body: some View {
        if !model.queue.items.isEmpty {
            Section {
                ForEach(model.queue.items) { item in
                    PendingRow(item: item)
                        .swipeActions {
                            Button(role: .destructive) {
                                model.dismiss(item)
                            } label: {
                                Image(systemName: "xmark")
                            }
                            .accessibilityLabel("Dismiss")
                        }
                }
            }
        }
    }
}

private struct PendingRow: View {
    @EnvironmentObject private var model: WatchModel
    let item: WatchAddition

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(item.amount.replacingOccurrences(of: "-", with: "−"))
                .font(.system(.body, design: .rounded).weight(.medium))
                .monospacedDigit()
            if item.status == .failed {
                Text(item.message ?? "Tandem couldn’t add it.")
                    .font(.caption2)
                    .foregroundStyle(Color.tandemOver)
                Button("Try again") { model.tryAgain(item) }
                    .font(.footnote)
            } else {
                Label("Waiting for iPhone", systemImage: "arrow.triangle.2.circlepath")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

/// Budgeted, spent and left this month, then the last few transactions.
struct CategoryDetailView: View {
    @EnvironmentObject private var model: WatchModel
    let category: WatchSummary.Category

    var body: some View {
        let summary = model.summary
        let current = summary?.categories?.first { $0.id == category.id } ?? category
        List {
            Section {
                AmountRow(title: "Budgeted", value: summary?.amount(current.budgeted) ?? "–")
                AmountRow(title: "Spent", value: summary?.amount(current.spent) ?? "–")
                AmountRow(
                    title: current.isOverspent ? "Over" : "Left",
                    value: summary?.amount(current.left) ?? "–",
                    highlight: current.isOverspent
                )
            }
            if let transactions = current.transactions, !transactions.isEmpty {
                Section("Recent") {
                    ForEach(transactions, id: \.self) { transaction in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(transaction.payee)
                                .lineLimit(1)
                            HStack {
                                Text(transaction.amount)
                                    .monospacedDigit()
                                    .privacySensitive()
                                Spacer()
                                Text(WatchDates.short(transaction.date))
                                    .foregroundStyle(.secondary)
                            }
                            .font(.footnote)
                        }
                    }
                }
            }
        }
        .navigationTitle(current.name)
    }
}

private struct AmountRow: View {
    let title: String
    let value: String
    var highlight = false

    var body: some View {
        HStack {
            Text(title)
                .foregroundStyle(.secondary)
            Spacer()
            Text(value)
                .monospacedDigit()
                .fontWeight(.medium)
                .foregroundStyle(highlight ? Color.tandemOver : .primary)
                .privacySensitive()
        }
    }
}

/// The next bills, soonest first.
struct BillsView: View {
    @EnvironmentObject private var model: WatchModel

    var body: some View {
        List {
            let bills = model.summary?.bills ?? []
            if bills.isEmpty {
                Text(model.summary?.statusMessage ?? "No bills due in the next 60 days.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .listRowBackground(Color.clear)
            }
            ForEach(bills, id: \.self) { bill in
                VStack(alignment: .leading, spacing: 2) {
                    Text(bill.name)
                        .font(.headline)
                        .lineLimit(2)
                    HStack {
                        Text(WatchDates.relative(bill.date))
                            .foregroundStyle(WatchDates.isToday(bill.date) ? Color.tandemAccent : .secondary)
                        Spacer()
                        Text(model.summary?.amount(bill.amount) ?? "–")
                            .monospacedDigit()
                            .privacySensitive()
                    }
                    .font(.footnote)
                }
                .accessibilityElement(children: .combine)
            }
        }
    }
}
