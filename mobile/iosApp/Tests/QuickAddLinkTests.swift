@testable import TandemForActual
import XCTest

final class QuickAddLinkTests: XCTestCase {
    func testAmountAndPayeeAreEncodedIntoTheAddLink() {
        XCTAssertEqual(
            QuickAddLink.make(amount: -1234.5, payee: " Café & Bar "),
            "app.tandemforactual://open/add-transaction?amount=1234.5&payee=Caf%C3%A9%20%26%20Bar"
        )
    }

    func testMissingOrUnusableValuesAreLeftOut() {
        XCTAssertEqual(QuickAddLink.make(amount: nil, payee: "  "), "app.tandemforactual://open/add-transaction")
        XCTAssertEqual(QuickAddLink.make(amount: .infinity, payee: nil), "app.tandemforactual://open/add-transaction")
        XCTAssertEqual(QuickAddLink.make(amount: 12, payee: nil), "app.tandemforactual://open/add-transaction?amount=12")
    }
}
