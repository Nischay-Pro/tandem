@testable import TandemForActual
import XCTest

final class WatchAdditionsTests: XCTestCase {
    private func addition(_ id: String, amount: String = "-4.50") -> WatchAddition {
        WatchAddition(id: id, amount: amount, categoryId: "food", payee: "Corner Café", accountId: "checking")
    }

    func testTheSameEntryIsQueuedOnce() {
        var queue = WatchAdditionQueue()
        XCTAssertTrue(queue.enqueue(addition("a")))
        XCTAssertFalse(queue.enqueue(addition("a", amount: "-9")))
        XCTAssertTrue(queue.enqueue(addition("b")))
        XCTAssertEqual(queue.items.map(\.id), ["a", "b"])
        XCTAssertEqual(queue.items.first?.amount, "-4.50")
    }

    func testAnAddedEntryLeavesAndARefusedOneKeepsItsReason() {
        var queue = WatchAdditionQueue()
        queue.enqueue(addition("a"))
        queue.enqueue(addition("b"))

        XCTAssertEqual(queue.apply(WatchAdditionReply(id: "a", added: true, message: "Added")).map(\.id), "a")
        let refused = queue.apply(WatchAdditionReply(id: "b", added: false, message: "Open Tandem to unlock this budget, then try again."))
        XCTAssertNil(queue.apply(WatchAdditionReply(id: "unknown", added: true, message: "")))

        XCTAssertEqual(refused?.status, .failed)
        XCTAssertEqual(queue.items.map(\.id), ["b"])
        XCTAssertTrue(queue.waiting.isEmpty)
        XCTAssertEqual(queue.failed.first?.message, "Open Tandem to unlock this budget, then try again.")
        queue.dismiss(id: "b")
        XCTAssertTrue(queue.items.isEmpty)
    }

    func testOnlyWaitingEntriesWatchConnectivityDroppedAreSentAgain() {
        var queue = WatchAdditionQueue()
        queue.enqueue(addition("a"))
        queue.enqueue(addition("b"))
        queue.enqueue(addition("c"))
        queue.apply(WatchAdditionReply(id: "c", added: false, message: "No budget is open."))

        XCTAssertEqual(queue.needingResend(outstanding: ["a"]).map(\.id), ["b"])
    }

    func testQueueSurvivesARelaunch() throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: "watch-additions-\(UUID().uuidString)"))
        var queue = WatchAdditionQueue()
        queue.enqueue(addition("a"))
        queue.save(to: defaults)

        XCTAssertEqual(WatchAdditionQueue.load(from: defaults).items.map(\.id), ["a"])
        XCTAssertEqual(WatchAdditionQueue.load(from: defaults).items.first?.payee, "Corner Café")
        XCTAssertEqual(WatchAdditionQueue.load(from: UserDefaults(suiteName: "empty-\(UUID().uuidString)")!).items, [])
    }

    func testPayloadsRoundTripAndRejectOtherMessages() throws {
        let sent = addition("a")
        let received = try XCTUnwrap(WatchAddition(payload: sent.payload))
        XCTAssertEqual(received.id, "a")
        XCTAssertEqual(received.amount, "-4.50")
        XCTAssertEqual(received.categoryId, "food")
        XCTAssertEqual(received.payee, "Corner Café")
        XCTAssertEqual(received.accountId, "checking")
        XCTAssertNil(WatchAddition(payload: ["kind": "other", "id": "a", "amount": "-1"]))
        XCTAssertNil(WatchAddition(payload: ["kind": WatchAddition.kind, "id": "", "amount": "-1"]))

        let reply = WatchAdditionReply(id: "a", added: false, message: "Quick add is off.")
        XCTAssertEqual(WatchAdditionReply(payload: reply.payload), reply)
        XCTAssertNil(WatchAdditionReply(payload: sent.payload))
    }

    func testKeypadBuildsAPlainAmountOut() {
        var input = WatchAmountInput(fractionDigits: 2)
        XCTAssertNil(input.plainAmount)
        ["1", "2", ".", "4", "0", "9"].forEach { input.press($0) }
        XCTAssertEqual(input.display, "12.40")
        XCTAssertEqual(input.plainAmount, "-12.40")
        input.press("⌫")
        input.press("⌫")
        XCTAssertEqual(input.display, "12.")
        XCTAssertEqual(input.plainAmount, "-12")

        var whole = WatchAmountInput(fractionDigits: 0)
        ["0", "0", "5", "."].forEach { whole.press($0) }
        XCTAssertEqual(whole.plainAmount, "-5")

        var zero = WatchAmountInput()
        [".", "0"].forEach { zero.press($0) }
        XCTAssertEqual(zero.display, "0.0")
        XCTAssertNil(zero.plainAmount)
    }

    func testSpokenAmountsAreReadInEitherDecimalMark() {
        var input = WatchAmountInput()
        XCTAssertTrue(input.set(spoken: "$12,40"))
        XCTAssertEqual(input.plainAmount, "-12.40")
        XCTAssertFalse(input.set(spoken: "lunch"))
        XCTAssertEqual(input.plainAmount, "-12.40")
    }
}
