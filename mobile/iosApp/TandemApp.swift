import ComposeApp
import SwiftUI
import UIKit

@main
struct TandemApp: App {
    @UIApplicationDelegateAdaptor(TandemAppDelegate.self) private var appDelegate
    @State private var preferredScheme: ColorScheme?

    var body: some Scene {
        WindowGroup {
            ComposeRootView(preferredScheme: $preferredScheme)
                .preferredColorScheme(preferredScheme)
                .ignoresSafeArea()
                .onOpenURL { url in
                    AuthReturnEvents.shared.publish(uri: url.absoluteString)
                }
        }
    }
}

private struct ComposeRootView: UIViewControllerRepresentable {
    @Binding var preferredScheme: ColorScheme?

    func makeCoordinator() -> Coordinator {
        Coordinator(preferredScheme: $preferredScheme)
    }

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            onBackgroundSyncSettingsChanged: { enabled, unmeteredOnly, budgetId in
                IosBackgroundSyncScheduler.shared.update(
                    enabled: enabled.boolValue,
                    unmeteredOnly: unmeteredOnly.boolValue,
                    budgetId: budgetId
                )
            },
            onSystemAppearanceChanged: { isDark, followsSystem in
                context.coordinator.updateAppearance(isDark: isDark, followsSystem: followsSystem)
            },
            onRequestNotificationPermission: {
                IosBudgetNotifications.shared.requestPermission()
            },
            onOpenNotificationSettings: {
                IosBudgetNotifications.shared.openSettings()
            }
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    final class Coordinator {
        private let preferredScheme: Binding<ColorScheme?>

        init(preferredScheme: Binding<ColorScheme?>) {
            self.preferredScheme = preferredScheme
        }

        func updateAppearance(isDark: KotlinBoolean, followsSystem: KotlinBoolean) {
            if followsSystem.boolValue {
                preferredScheme.wrappedValue = nil
            } else {
                preferredScheme.wrappedValue = isDark.boolValue ? .dark : .light
            }
        }
    }
}

private final class TandemAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        IosBackgroundSyncScheduler.shared.register()
        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        if let shortcutItem = options.shortcutItem {
            _ = TandemSceneDelegate.handle(shortcutItem)
        }
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = TandemSceneDelegate.self
        return configuration
    }
}

private final class TandemSceneDelegate: NSObject, UIWindowSceneDelegate {
    static let addTransaction = "app.tandemforactual.add-transaction"

    static func handle(_ shortcutItem: UIApplicationShortcutItem) -> Bool {
        guard shortcutItem.type == addTransaction else { return false }
        QuickActionEvents.shared.requestAddTransaction()
        return true
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        if let text = SharedTextInbox.take(from: UserDefaults(suiteName: SharedTextInbox.appGroup)) {
            QuickActionEvents.shared.shareText(text: text)
        }
    }

    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(Self.handle(shortcutItem))
    }
}
