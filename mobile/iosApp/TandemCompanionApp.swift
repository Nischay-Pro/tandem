import ComposeApp
import SwiftUI
import UIKit

@main
struct TandemCompanionApp: App {
    @UIApplicationDelegateAdaptor(TandemAppDelegate.self) private var appDelegate
    @State private var preferredScheme: ColorScheme? = .dark

    var body: some Scene {
        WindowGroup {
            ComposeRootView(preferredScheme: $preferredScheme)
                .preferredColorScheme(preferredScheme)
                .ignoresSafeArea()
                .background(Color(red: 16 / 255, green: 42 / 255, blue: 67 / 255))
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
            onBackgroundSyncSettingsChanged: { enabled, wifiOnly, budgetId in
                IosBackgroundSyncScheduler.shared.update(
                    enabled: enabled.boolValue,
                    wifiOnly: wifiOnly.boolValue,
                    budgetId: budgetId
                )
                return KotlinUnit()
            },
            onSystemAppearanceChanged: { isDark, followsSystem in
                context.coordinator.updateAppearance(isDark: isDark, followsSystem: followsSystem)
                return KotlinUnit()
            },
            onRequestNotificationPermission: {
                IosBudgetNotifications.shared.requestPermission()
                return KotlinUnit()
            },
            onOpenNotificationSettings: {
                IosBudgetNotifications.shared.openSettings()
                return KotlinUnit()
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
