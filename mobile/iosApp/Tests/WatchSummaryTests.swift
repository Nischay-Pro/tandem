@testable import TandemForActual
import XCTest

final class WatchSummaryTests: XCTestCase {
    private let ready = """
    {"v":1,"s":"ready","g":1795000000000,"k":"budget","b":"Home","y":1794999000000,"l":"$320.00","lo":false,"r":"-$5.00","ro":true,
    "c":[{"i":"food","n":"Food","l":"-$12.40","o":true,"p":112,"bu":"$100.00","sp":"$112.40",
    "x":[{"n":"Corner Café","a":"-$4.50","d":"2026-11-26"}]},{"i":"rent","n":"Rent","l":"$0.00","o":false,"p":100}],
    "u":[{"n":"Water","d":"2026-11-27","a":"$40.00"},{"n":"Phone","d":"2026-12-02","a":"$20.00"}],
    "q":{"on":true,"f":2,"a":[{"i":"checking","n":"Checking"}],"p":["Corner Café"],"k":[{"i":"fun","n":"Fun"}]}}
    """

    private func summary(_ json: String) throws -> WatchSummary {
        try XCTUnwrap(WatchSummary.decode(Data(json.utf8)))
    }

    private func json(state: String, budget: String = "budget", generated: Int = 1) -> Data {
        Data(#"{"v":1,"s":"\#(state)","g":\#(generated),"k":"\#(budget)","b":"Home","q":{"on":false}}"#.utf8)
    }

    func testReadySummaryDecodesEveryPart() throws {
        let summary = try summary(ready)
        XCTAssertEqual(summary.state, .ready)
        XCTAssertEqual(summary.amount(summary.leftToSpend), "$320.00")
        XCTAssertEqual(summary.categories?.map(\.id), ["food", "rent"])
        XCTAssertTrue(try XCTUnwrap(summary.categories?.first).isOverspent)
        XCTAssertEqual(try XCTUnwrap(summary.categories?.first).progress, 1)
        XCTAssertEqual(summary.categories?.first?.transactions?.first?.payee, "Corner Café")
        XCTAssertEqual(summary.bills?.count, 2)
        XCTAssertTrue(summary.canQuickAdd)
        XCTAssertEqual(summary.quickAdd?.accounts?.first?.id, "checking")
        XCTAssertNil(summary.statusMessage)
    }

    func testSummariesOverTheLimitOrFromAnotherVersionAreIgnored() {
        XCTAssertNil(WatchSummary.decode(nil))
        XCTAssertNil(WatchSummary.decode(Data(#"{"v":2,"s":"ready","g":1}"#.utf8)))
        XCTAssertNil(WatchSummary.decode(Data("not json".utf8)))
        let padded = #"{"v":1,"s":"none","g":1,"b":""# + String(repeating: "x", count: WatchSummary.maxBytes) + #""}"#
        XCTAssertNil(WatchSummary.decode(Data(padded.utf8)))
        XCTAssertLessThanOrEqual(Data(ready.utf8).count, WatchSummary.maxBytes)
    }

    func testHiddenAndLockedSummariesNeverShowAmounts() throws {
        let hidden = try summary(#"{"v":1,"s":"hidden","g":1,"b":"Home","c":[{"i":"food","n":"Food"}],"q":{"on":true}}"#)
        XCTAssertEqual(hidden.amount("$5.00"), "•••")
        XCTAssertFalse(hidden.showsAmounts)
        XCTAssertNil(hidden.statusMessage)

        let locked = try summary(#"{"v":1,"s":"locked","g":1,"b":"Home","q":{"on":true}}"#)
        XCTAssertEqual(locked.amount("$5.00"), "–")
        XCTAssertEqual(locked.statusMessage, "Locked · open Tandem on your phone")
        XCTAssertTrue(locked.canQuickAdd)

        let none = try summary(#"{"v":1,"s":"none","g":1,"q":{"on":false}}"#)
        XCTAssertFalse(none.canQuickAdd)
        XCTAssertEqual(none.statusMessage, "Open Tandem on your phone to choose a budget.")
    }

    func testIPhoneKeepsUnlockedNumbersUntilTheBudgetChanges() {
        XCTAssertTrue(WatchSummaryOutbox.shouldSend(previous: nil, next: json(state: "locked")))
        XCTAssertFalse(WatchSummaryOutbox.shouldSend(previous: json(state: "ready"), next: json(state: "locked")))
        XCTAssertFalse(WatchSummaryOutbox.shouldSend(previous: json(state: "hidden"), next: json(state: "locked")))
        XCTAssertTrue(WatchSummaryOutbox.shouldSend(previous: json(state: "ready"), next: json(state: "locked", budget: "other")))
        XCTAssertTrue(WatchSummaryOutbox.shouldSend(previous: json(state: "ready"), next: json(state: "none", budget: "")))
        XCTAssertTrue(WatchSummaryOutbox.shouldSend(previous: json(state: "locked"), next: json(state: "ready")))
        XCTAssertFalse(WatchSummaryOutbox.shouldSend(previous: json(state: "ready"), next: json(state: "ready", generated: 2)))
        XCTAssertFalse(WatchSummaryOutbox.shouldSend(previous: nil, next: Data("{}".utf8)))
    }

    func testSyncedTimeAndBillsDueToday() throws {
        let summary = try summary(ready)
        let text = summary.syncedText(locale: Locale(identifier: "en_US"), timeZone: TimeZone(identifier: "UTC")!)
        XCTAssertTrue(text.hasPrefix("Synced "), text)
        XCTAssertTrue(text.contains(":"), text)

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let due = calendar.date(from: DateComponents(year: 2026, month: 11, day: 27, hour: 9))!
        XCTAssertEqual(summary.billsDue(on: due, calendar: calendar).map(\.name), ["Water"])
        XCTAssertTrue(WatchDates.isToday("2026-11-27", now: due, calendar: calendar))
        XCTAssertEqual(WatchDates.relative("2026-11-27", now: due, calendar: calendar), "Today")
        XCTAssertEqual(WatchDates.relative("2026-11-28", now: due, calendar: calendar), "Tomorrow")
        XCTAssertEqual(WatchDates.relative("soon", now: due, calendar: calendar), "soon")
    }

    func testStoreKeepsOnlyValidSummaries() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("watch-summary-\(name).json")
        XCTAssertTrue(WatchSummaryStore.save(Data(ready.utf8), to: url))
        XCTAssertEqual(WatchSummaryStore.load(from: url)?.budgetName, "Home")
        XCTAssertFalse(WatchSummaryStore.save(Data("{}".utf8), to: url))
        XCTAssertFalse(WatchSummaryStore.save(Data(ready.utf8), to: nil))
        try? FileManager.default.removeItem(at: url)
    }
}
