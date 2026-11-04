import Foundation
import UIKit
import UserNotifications

final class IosBudgetNotifications {
    static let shared = IosBudgetNotifications()

    private let center = UNUserNotificationCenter.current()

    private init() {}

    func requestPermission() {
        center.requestAuthorization(options: [.alert, .badge, .sound]) { granted, error in
            if let error {
                NSLog("Tandem notification permission request failed: %@", String(describing: error))
            } else if !granted {
                NSLog("Tandem notifications remain disabled until enabled in Settings.")
            }
        }
    }

    func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        DispatchQueue.main.async {
            UIApplication.shared.open(url)
        }
    }

    func syncFailed() {
        post(
            identifier: "tandem-sync-failed",
            title: "Budget sync needs attention",
            body: "Tandem couldn't refresh your budget. Open the app to retry."
        )
    }

    func conflicts(_ count: Int32) {
        guard count > 0 else { return }
        let noun = count == 1 ? "change" : "changes"
        let verb = count == 1 ? "needs" : "need"
        post(
            identifier: "tandem-conflicts",
            title: "Budget changes need review",
            body: "\(count) \(noun) \(verb) your choice."
        )
    }

    func rejected(_ count: Int32) {
        guard count > 0 else { return }
        let noun = count == 1 ? "edit" : "edits"
        let verb = count == 1 ? "needs" : "need"
        post(
            identifier: "tandem-rejected-edits",
            title: "An edit wasn't applied",
            body: "\(count) \(noun) \(verb) review."
        )
    }

    private func post(identifier: String, title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        center.add(UNNotificationRequest(identifier: identifier, content: content, trigger: nil)) { error in
            if let error {
                NSLog("Tandem notification could not be scheduled: %@", String(describing: error))
            }
        }
    }
}
