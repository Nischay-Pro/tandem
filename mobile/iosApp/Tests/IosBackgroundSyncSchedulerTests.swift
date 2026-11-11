import BackgroundTasks
import Foundation
import XCTest
@testable import TandemForActual

final class IosBackgroundSyncSchedulerTests: XCTestCase {
    private let userDefaultsSuite = "IosBackgroundSyncSchedulerTests.\(UUID().uuidString)"
    private var defaults: UserDefaults!
    private var taskScheduler: FakeBackgroundTaskScheduler!
    private var scheduler: IosBackgroundSyncScheduler!

    override func setUp() {
        super.setUp()
        defaults = UserDefaults(suiteName: userDefaultsSuite)
        defaults.removePersistentDomain(forName: userDefaultsSuite)
        taskScheduler = FakeBackgroundTaskScheduler()
        scheduler = IosBackgroundSyncScheduler(
            taskScheduler: taskScheduler,
            defaults: defaults
        )
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: userDefaultsSuite)
        super.tearDown()
    }

    func testRegisterUsesThePermittedBackgroundTaskIdentifier() {
        scheduler.register()

        XCTAssertEqual(
            taskScheduler.registeredIdentifiers,
            ["app.tandemforactual.ios.background-sync"]
        )
        XCTAssertEqual(taskScheduler.registeredHandlers.count, 1)
        XCTAssertEqual(
            taskScheduler.cancelledIdentifiers,
            ["app.tandemforactual.ios.background-sync"]
        )
        let permittedIdentifiers = Bundle.main.object(
            forInfoDictionaryKey: "BGTaskSchedulerPermittedIdentifiers"
        ) as? [String]
        XCTAssertTrue(
            permittedIdentifiers?.contains("app.tandemforactual.ios.background-sync") == true
        )
    }

    func testUpdateSchedulesRefreshForAnEnabledBudget() throws {
        scheduler.update(enabled: true, unmeteredOnly: true, budgetId: "budget-1")

        XCTAssertEqual(
            taskScheduler.cancelledIdentifiers,
            ["app.tandemforactual.ios.background-sync"]
        )
        let request = try XCTUnwrap(taskScheduler.submittedRequests.first as? BGAppRefreshTaskRequest)
        XCTAssertEqual(request.identifier, "app.tandemforactual.ios.background-sync")
        XCTAssertEqual(
            taskScheduler.pendingIdentifiers,
            ["app.tandemforactual.ios.background-sync"]
        )
        let delay = try XCTUnwrap(request.earliestBeginDate).timeIntervalSinceNow
        XCTAssertGreaterThan(delay, (6 * 60 * 60) - 5)
        XCTAssertLessThan(delay, (6 * 60 * 60) + 5)
        XCTAssertTrue(defaults.bool(forKey: "tandem.backgroundSyncEnabled"))
        XCTAssertTrue(defaults.bool(forKey: "tandem.backgroundSyncWifiOnly"))
    }

    func testDisablingSyncCancelsTheRequestAndClearsTheEnabledPreference() {
        scheduler.update(enabled: true, unmeteredOnly: false, budgetId: "budget-1")
        scheduler.update(enabled: false, unmeteredOnly: false, budgetId: "budget-1")

        XCTAssertEqual(
            taskScheduler.cancelledIdentifiers,
            [
                "app.tandemforactual.ios.background-sync",
                "app.tandemforactual.ios.background-sync",
            ]
        )
        XCTAssertEqual(taskScheduler.submittedRequests.count, 1)
        XCTAssertTrue(taskScheduler.pendingIdentifiers.isEmpty)
        XCTAssertFalse(defaults.bool(forKey: "tandem.backgroundSyncEnabled"))
    }

    func testEnabledSyncWithoutABudgetDoesNotScheduleARequest() {
        scheduler.update(enabled: true, unmeteredOnly: false, budgetId: "")

        XCTAssertEqual(
            taskScheduler.cancelledIdentifiers,
            ["app.tandemforactual.ios.background-sync"]
        )
        XCTAssertTrue(taskScheduler.submittedRequests.isEmpty)
        XCTAssertFalse(defaults.bool(forKey: "tandem.backgroundSyncEnabled"))
    }

    func testTandemAppHostLaunchesInTheSimulator() {
        XCTAssertEqual(Bundle.main.bundleIdentifier, "app.tandemforactual.ios")
        XCTAssertNotNil(Bundle.main.executableURL)
    }
}

private final class FakeBackgroundTaskScheduler: IosBackgroundTaskScheduling {
    private(set) var registeredIdentifiers: [String] = []
    private(set) var registeredHandlers: [(BGTask) -> Void] = []
    private(set) var cancelledIdentifiers: [String] = []
    private(set) var submittedRequests: [BGTaskRequest] = []
    private(set) var pendingIdentifiers: Set<String> = []

    func register(identifier: String, launchHandler: @escaping (BGTask) -> Void) -> Bool {
        registeredIdentifiers.append(identifier)
        registeredHandlers.append(launchHandler)
        return true
    }

    func submit(_ request: BGTaskRequest) throws {
        submittedRequests.append(request)
        pendingIdentifiers.insert(request.identifier)
    }

    func cancel(identifier: String) {
        cancelledIdentifiers.append(identifier)
        pendingIdentifiers.remove(identifier)
    }
}
