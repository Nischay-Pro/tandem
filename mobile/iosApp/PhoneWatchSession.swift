import ComposeApp
import Foundation
import WatchConnectivity

/// The iPhone's end of the Apple Watch link: sends the watch its summary and adds what's typed on the watch to the open
/// budget. The watch never reaches the budget or the server itself.
final class PhoneWatchSession: NSObject, WCSessionDelegate {
    static let shared = PhoneWatchSession()

    private let session: WCSession? = WCSession.isSupported() ? WCSession.default : nil

    func activate() {
        guard let session else { return }
        session.delegate = self
        session.activate()
        IosWatch.shared.connect { [weak self] json in
            self?.send(json)
        }
    }

    /// Sends the summary from this device's copy; while Tandem is closed a protected budget reads as locked.
    func refreshSummary() {
        guard isReady else { return }
        IosWatch.shared.readSummary { [weak self] json in
            if let json { self?.send(json) }
        }
    }

    private var isReady: Bool {
        guard let session else { return false }
        return session.activationState == .activated && session.isPaired && session.isWatchAppInstalled
    }

    private func send(_ json: String) {
        guard let session, isReady, let data = json.data(using: .utf8) else { return }
        let previous = session.applicationContext[WatchSummary.contextKey] as? Data
        guard WatchSummaryOutbox.shouldSend(previous: previous, next: data) else { return }
        do {
            try session.updateApplicationContext([WatchSummary.contextKey: data])
        } catch {
            NSLog("Tandem couldn’t update the watch: %@", String(describing: error))
        }
    }

    private func updatePaired(_ session: WCSession) {
        IosWatch.shared.updatePaired(paired: session.activationState == .activated && session.isPaired && session.isWatchAppInstalled)
    }

    private func add(_ payload: [String: Any], reply: @escaping (WatchAdditionReply) -> Void) {
        guard let addition = WatchAddition(payload: payload) else { return }
        IosWatch.shared.addQuickAdd(
            entryId: addition.id,
            amount: addition.amount,
            categoryId: addition.categoryId,
            payee: addition.payee,
            accountId: addition.accountId
        ) { result in
            if result.added {
                IosBackgroundSyncScheduler.shared.requestAfterAutomation()
                DispatchQueue.main.async { WidgetRefresh.refresh() }
            }
            reply(WatchAdditionReply(id: result.entryId, added: result.added, message: result.message))
        }
    }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        updatePaired(session)
        refreshSummary()
    }

    func sessionWatchStateDidChange(_ session: WCSession) {
        updatePaired(session)
        refreshSummary()
    }

    func sessionDidBecomeInactive(_ session: WCSession) {}

    func sessionDidDeactivate(_ session: WCSession) {
        session.activate()
    }

    func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any]) {
        add(userInfo) { reply in
            session.transferUserInfo(reply.payload)
        }
    }

    func session(_ session: WCSession, didReceiveMessage message: [String: Any], replyHandler: @escaping ([String: Any]) -> Void) {
        guard WatchAddition(payload: message) != nil else {
            replyHandler([:])
            return
        }
        add(message) { reply in
            replyHandler(reply.payload)
        }
    }
}
