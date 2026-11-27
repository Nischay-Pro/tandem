import SwiftUI

@main
struct TandemWatchApp: App {
    @StateObject private var model = WatchModel()

    var body: some Scene {
        WindowGroup {
            WatchRootView()
                .environmentObject(model)
                .tint(.tandemAccent)
                .onAppear { model.activate() }
        }
    }
}

extension Color {
    /// Tandem's purple, in the shade it takes on a dark background.
    static let tandemAccent = Color(red: 0xB9 / 255, green: 0xA8 / 255, blue: 0xFF / 255)
    static let tandemOver = Color(red: 1, green: 0.45, blue: 0.42)
}

/// Glance and upcoming bills as vertical pages the Digital Crown moves between, with quick add one tap away.
struct WatchRootView: View {
    @EnvironmentObject private var model: WatchModel
    @State private var addingTransaction = false

    var body: some View {
        NavigationStack {
            TabView {
                GlanceView()
                    .navigationTitle("Tandem")
                BillsView()
                    .navigationTitle("Bills")
            }
            .tabViewStyle(.verticalPage)
            .toolbar {
                if model.summary?.canQuickAdd == true {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            addingTransaction = true
                        } label: {
                            Image(systemName: "plus")
                        }
                        .accessibilityLabel("Add transaction")
                    }
                }
            }
            .navigationDestination(for: WatchSummary.Category.self) { category in
                CategoryDetailView(category: category)
            }
        }
        .sheet(isPresented: $addingTransaction) {
            if let summary = model.summary {
                QuickAddFlow(summary: summary) { addition in
                    model.add(addition)
                    addingTransaction = false
                }
            }
        }
    }
}
