@testable import TandemForActual
import XCTest

final class WidgetSnapshotStoreTests: XCTestCase {
    private let ready = """
    {"version":1,"month":"2026-11","state":"ready","budgetName":"Home","readyToAssign":"$5.00","readyToAssignNegative":false,
    "spent":"$25.00","categories":[{"id":"food","name":"Food","group":"Living","amount":"-$12.40","text":"$12.40 over","overspent":true},
    {"id":"rent","name":"Rent","group":"Living","amount":"$300.00","text":"$300.00","overspent":false}]}
    """
    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/New_York")!
        return calendar
    }

    private func date(_ year: Int, _ month: Int, _ day: Int) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day, hour: 12))!
    }

    private var fileURL: URL {
        FileManager.default.temporaryDirectory.appendingPathComponent("widget-summary-\(name).json")
    }

    func testSavedSummaryReadsBack() throws {
        XCTAssertTrue(WidgetSnapshotStore.save(ready, to: fileURL))
        let snapshot = try XCTUnwrap(WidgetSnapshotStore.load(from: fileURL))
        XCTAssertEqual(snapshot.budgetName, "Home")
        XCTAssertEqual(snapshot.categories?.count, 2)
        XCTAssertEqual(snapshot.status(on: date(2026, 11, 30), calendar: calendar), .ready)
        XCTAssertEqual(snapshot.status(on: date(2026, 12, 1), calendar: calendar), .outdated)
        XCTAssertNil(snapshot.readyLabel)
    }

    func testTrackingSummaryCarriesItsLabel() throws {
        let tracking = #"{"version":1,"month":"2026-11","state":"ready","readyToAssign":"$15.00","readyLabel":"Saved this month"}"#
        XCTAssertTrue(WidgetSnapshotStore.save(tracking, to: fileURL))
        XCTAssertEqual(try XCTUnwrap(WidgetSnapshotStore.load(from: fileURL)).readyLabel, "Saved this month")
    }

    func testOnlyValidSummariesAreSaved() {
        XCTAssertFalse(WidgetSnapshotStore.save("not json", to: fileURL))
        XCTAssertFalse(WidgetSnapshotStore.save(#"{"version":2,"month":"2026-11","state":"none"}"#, to: fileURL))
        XCTAssertFalse(WidgetSnapshotStore.save(ready, to: nil))
        XCTAssertNil(WidgetSnapshotStore.load(from: nil))
    }

    func testProtectedAndMissingBudgetsShowNoAmounts() throws {
        XCTAssertTrue(WidgetSnapshotStore.save(#"{"version":1,"month":"2026-11","state":"hidden","budgetName":"Home"}"#, to: fileURL))
        let hidden = try XCTUnwrap(WidgetSnapshotStore.load(from: fileURL))
        XCTAssertEqual(hidden.status(on: date(2026, 11, 2), calendar: calendar), .hidden)
        XCTAssertNil(hidden.categories)
        XCTAssertTrue(WidgetSnapshotStore.save(#"{"version":1,"month":"2026-11","state":"none"}"#, to: fileURL))
        XCTAssertEqual(WidgetSnapshotStore.load(from: fileURL)?.status(on: date(2026, 11, 2), calendar: calendar), .noBudget)
    }

    func testChosenCategoriesKeepTheirOrder() throws {
        XCTAssertTrue(WidgetSnapshotStore.save(ready, to: fileURL))
        let snapshot = try XCTUnwrap(WidgetSnapshotStore.load(from: fileURL))
        XCTAssertEqual(snapshot.categories(withIds: ["rent", "gone", "food", "rent"]).map(\.id), ["rent", "food"])
        XCTAssertEqual(snapshot.categories(withIds: []), [])
    }

    func testMonthsTurnOverAtLocalMidnight() {
        XCTAssertEqual(WidgetSnapshotStore.monthId(for: date(2026, 1, 9), calendar: calendar), "2026-01")
        XCTAssertEqual(
            WidgetSnapshotStore.nextMonthStart(after: date(2026, 12, 31), calendar: calendar),
            calendar.date(from: DateComponents(year: 2027, month: 1, day: 1))
        )
    }
}
