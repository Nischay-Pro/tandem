import ComposeApp
import SwiftUI
import UIKit

@main
struct TandemCompanionApp: App {
    @UIApplicationDelegateAdaptor(TandemAppDelegate.self) private var appDelegate
    @AppStorage("tandem_notifications_permission_prompted") private var notificationPermissionPrompted = false
    @State private var preferredScheme: ColorScheme?

    var body: some Scene {
        WindowGroup {
            ComposeRootView(preferredScheme: $preferredScheme)
                .preferredColorScheme(preferredScheme)
                .ignoresSafeArea()
                .task {
                    guard !notificationPermissionPrompted else { return }
                    notificationPermissionPrompted = true
                    IosBudgetNotifications.shared.requestPermission()
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
}
