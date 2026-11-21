@testable import TandemForActual
import XCTest

final class SharedTextInboxTests: XCTestCase {
    private var defaults: UserDefaults!

    override func setUp() {
        super.setUp()
        defaults = UserDefaults(suiteName: "SharedTextInboxTests")
        defaults.removePersistentDomain(forName: "SharedTextInboxTests")
    }

    func testSharedTextIsHandedOverOnce() {
        XCTAssertTrue(SharedTextInbox.save("  Paid $12.50 at Corner Cafe \n", to: defaults))
        XCTAssertEqual(SharedTextInbox.take(from: defaults), "Paid $12.50 at Corner Cafe")
        XCTAssertNil(SharedTextInbox.take(from: defaults))
    }

    func testBlankTextAndMissingStorageAreIgnored() {
        XCTAssertFalse(SharedTextInbox.save("   ", to: defaults))
        XCTAssertFalse(SharedTextInbox.save("Paid", to: nil))
        XCTAssertNil(SharedTextInbox.take(from: nil))
    }

    func testOldSharesAreDroppedAndLongTextIsCapped() {
        let saved = Date(timeIntervalSince1970: 1_000)
        XCTAssertTrue(SharedTextInbox.save("Paid", to: defaults, now: saved))
        XCTAssertNil(SharedTextInbox.take(from: defaults, now: saved.addingTimeInterval(SharedTextInbox.maxAge + 1)))
        XCTAssertTrue(SharedTextInbox.save(String(repeating: "a", count: 5_000), to: defaults))
        XCTAssertEqual(SharedTextInbox.take(from: defaults)?.count, SharedTextInbox.maxLength)
    }
}
