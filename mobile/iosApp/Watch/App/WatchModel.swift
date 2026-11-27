import Foundation
import WatchConnectivity
import WatchKit
import WidgetKit

/// The watch's view of Tandem: the last summary from the iPhone and the additions still on their way to it.
final class WatchModel: NSObject, ObservableObject, WCSessionDelegate {
    @Published private(set) var summary: WatchSummary?
    @Published private(set) var queue: WatchAdditionQueue

    private let session: WCSession? = WCSession.isSupported() ? WCSession.default : nil
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        queue = WatchAdditionQueue.load(from: defaults)
        summary = WatchSummaryStore.load(from: WatchSummaryStore.fileURL())
        super.init()
        #if DEBUG
        if let demo = WatchScreenshot.summary { summary = demo }
        #endif
    }

    func activate() {
        #if DEBUG
        if WatchScreenshot.summary != nil { return }
        #endif
        guard let session else { return }
        session.delegate = self
        session.activate()
    }

    /// Queues [addition] and sends it; it stays queued, and is sent again, until the iPhone answers.
    func add(_ addition: WatchAddition) {
        guard queue.enqueue(addition) else { return }
        queue.save(to: defaults)
        send(addition)
        WKInterfaceDevice.current().play(.success)
    }

    func dismiss(_ addition: WatchAddition) {
        queue.dismiss(id: addition.id)
        queue.save(to: defaults)
    }

    func tryAgain(_ addition: WatchAddition) {
        queue.dismiss(id: addition.id)
        add(WatchAddition(
            id: addition.id,
            amount: addition.amount,
            categoryId: addition.categoryId,
            categoryName: addition.categoryName,
            payee: addition.payee,
            accountId: addition.accountId,
            createdAt: addition.createdAt
        ))
    }

    private func send(_ addition: WatchAddition) {
        guard let session, session.activationState == .activated else { return }
        let payload = addition.payload
        guard session.isReachable else {
            session.transferUserInfo(payload)
            return
        }
        session.sendMessage(payload, replyHandler: { [weak self] reply in
            self?.receive(reply)
        }, errorHandler: { _ in
            session.transferUserInfo(payload)
        })
    }

    private func resendWaiting(_ session: WCSession) {
        let outstanding = Set(session.outstandingUserInfoTransfers.compactMap { $0.userInfo["id"] as? String })
        DispatchQueue.main.async { [self] in
            queue.needingResend(outstanding: outstanding).forEach(send)
        }
    }

    private func receive(_ payload: [String: Any]) {
        guard let reply = WatchAdditionReply(payload: payload) else { return }
        DispatchQueue.main.async { [self] in
            if let item = queue.apply(reply), item.status == .failed {
                WKInterfaceDevice.current().play(.failure)
            }
            queue.save(to: defaults)
        }
    }

    private func receive(context: [String: Any]) {
        guard let data = context[WatchSummary.contextKey] as? Data, let next = WatchSummary.decode(data) else { return }
        WatchSummaryStore.save(data, to: WatchSummaryStore.fileURL())
        DispatchQueue.main.async { [self] in
            summary = next
            WidgetCenter.shared.reloadAllTimelines()
        }
    }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        guard activationState == .activated else { return }
        receive(context: session.receivedApplicationContext)
        resendWaiting(session)
    }

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        receive(context: applicationContext)
    }

    func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any]) {
        receive(userInfo)
    }

    func sessionReachabilityDidChange(_ session: WCSession) {
        if session.isReachable { resendWaiting(session) }
    }
}
