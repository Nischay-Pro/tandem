import BackgroundTasks
import ComposeApp
import Foundation
import Network

protocol IosBackgroundTaskScheduling {
    func register(
        identifier: String,
        launchHandler: @escaping (BGTask) -> Void
    ) -> Bool
    func submit(_ request: BGTaskRequest) throws
    func cancel(identifier: String)
}

extension BGTaskScheduler: IosBackgroundTaskScheduling {
    func register(
        identifier: String,
        launchHandler: @escaping (BGTask) -> Void
    ) -> Bool {
        register(
            forTaskWithIdentifier: identifier,
            using: nil,
            launchHandler: launchHandler
        )
    }

    func cancel(identifier: String) {
        cancel(taskRequestWithIdentifier: identifier)
    }
}

final class IosBackgroundSyncScheduler {
    static let shared = IosBackgroundSyncScheduler(
        taskScheduler: BGTaskScheduler.shared,
        defaults: .standard
    )

    private let taskIdentifier = "app.tandemforactual.ios.background-sync"
    private let taskScheduler: IosBackgroundTaskScheduling
    private let defaults: UserDefaults
    private let enabledKey = "tandem.backgroundSyncEnabled"
    private let unmeteredOnlyKey = "tandem.backgroundSyncWifiOnly"

    init(taskScheduler: IosBackgroundTaskScheduling, defaults: UserDefaults) {
        self.taskScheduler = taskScheduler
        self.defaults = defaults
    }

    func register() {
        let registered = taskScheduler.register(identifier: taskIdentifier) {
            [weak self] task in
            guard let self, let refreshTask = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            self.run(refreshTask)
        }

        if !registered {
            NSLog("Tandem background refresh registration failed. Check the iOS task identifier and background mode.")
        }
        scheduleIfEnabled()
    }

    func update(enabled: Bool, unmeteredOnly: Bool, budgetId: String) {
        let shouldSchedule = enabled && !budgetId.isEmpty
        defaults.set(shouldSchedule, forKey: enabledKey)
        defaults.set(unmeteredOnly, forKey: unmeteredOnlyKey)
        taskScheduler.cancel(identifier: taskIdentifier)
        if shouldSchedule {
            schedule()
        }
    }

    private func scheduleIfEnabled() {
        guard defaults.bool(forKey: enabledKey) else {
            taskScheduler.cancel(identifier: taskIdentifier)
            return
        }
        schedule()
    }

    private func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 6 * 60 * 60)
        do {
            try taskScheduler.submit(request)
        } catch {
            NSLog("Tandem background refresh could not be scheduled: %@", String(describing: error))
        }
    }

    private func run(_ task: BGAppRefreshTask) {
        scheduleIfEnabled()
        guard defaults.bool(forKey: enabledKey) else {
            task.setTaskCompleted(success: true)
            return
        }

        let monitor = NWPathMonitor()
        let completion = BackgroundTaskCompletion(task: task)
        let runGate = BackgroundSyncRunGate()
        let handleSlot = BackgroundSyncHandleSlot()
        let unmeteredOnly = defaults.bool(forKey: unmeteredOnlyKey)

        task.expirationHandler = {
            handleSlot.cancel()
            monitor.cancel()
            completion.finish(success: false)
        }

        monitor.pathUpdateHandler = { path in
            guard !completion.isFinished else { return }
            guard path.status == .satisfied else {
                monitor.cancel()
                completion.finish(success: false)
                return
            }
            if unmeteredOnly && (path.isExpensive || path.isConstrained) {
                monitor.cancel()
                completion.finish(success: false)
                return
            }

            guard runGate.claim() else { return }
            monitor.cancel()
            let handle = IosBackgroundSyncHandleKt.startIosBackgroundBudgetSync { taskSucceeded, syncFailed, conflictCount, rejectedCount in
                if syncFailed.boolValue {
                    IosBudgetNotifications.shared.syncFailed()
                }
                IosBudgetNotifications.shared.conflicts(Int32(conflictCount.intValue))
                IosBudgetNotifications.shared.rejected(Int32(rejectedCount.intValue))
                completion.finish(success: taskSucceeded.boolValue)
            }
            handleSlot.install(handle)
        }
        monitor.start(queue: DispatchQueue(label: "app.tandemforactual.background-network", qos: .utility))
    }
}

private final class BackgroundSyncRunGate {
    private let lock = NSLock()
    private var didStart = false

    func claim() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        guard !didStart else { return false }
        didStart = true
        return true
    }
}

private final class BackgroundSyncHandleSlot {
    private let lock = NSLock()
    private var handle: IosBackgroundSyncHandle?
    private var wasCancelled = false

    func install(_ handle: IosBackgroundSyncHandle) {
        lock.lock()
        guard !wasCancelled else {
            lock.unlock()
            handle.cancel()
            return
        }
        self.handle = handle
        lock.unlock()
    }

    func cancel() {
        lock.lock()
        wasCancelled = true
        let handle = self.handle
        self.handle = nil
        lock.unlock()
        handle?.cancel()
    }
}

private final class BackgroundTaskCompletion {
    private let lock = NSLock()
    private let task: BGTask
    private var completed = false

    init(task: BGTask) {
        self.task = task
    }

    var isFinished: Bool {
        lock.lock()
        defer { lock.unlock() }
        return completed
    }

    func finish(success: Bool) {
        lock.lock()
        guard !completed else {
            lock.unlock()
            return
        }
        completed = true
        lock.unlock()
        task.setTaskCompleted(success: success)
    }
}
