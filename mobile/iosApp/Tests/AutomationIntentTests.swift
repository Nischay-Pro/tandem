@testable import TandemForActual
import XCTest

final class AutomationIntentTests: XCTestCase {
    func testAmountsAreSignedByTypeWithADotAndNoGrouping() {
        XCTAssertEqual(AutomationAmountText.make(1234.5, moneyIn: false), "-1234.5")
        XCTAssertEqual(AutomationAmountText.make(-12.4, moneyIn: true), "+12.4")
        XCTAssertEqual(AutomationAmountText.make(12, moneyIn: false), "-12")
        XCTAssertNil(AutomationAmountText.make(0, moneyIn: false))
        XCTAssertNil(AutomationAmountText.make(.nan, moneyIn: true))
        XCTAssertNil(AutomationAmountText.make(.infinity, moneyIn: false))
    }

    func testDatesAreSentAsLocalYearMonthDay() throws {
        let date = try XCTUnwrap(Calendar.current.date(from: DateComponents(year: 2026, month: 3, day: 7, hour: 23, minute: 30)))
        XCTAssertEqual(AutomationDateText.make(date), "2026-03-07")
    }

    func testEveryScreenOpensItsFeatureLink() {
        XCTAssertEqual(
            TandemFeature.allCases.map(\.link),
            [
                "app.tandemforactual://open/scan-receipt",
                "app.tandemforactual://open/budget-checkup",
                "app.tandemforactual://open/accounts",
                "app.tandemforactual://open/review-transactions",
            ]
        )
        XCTAssertEqual(Set(TandemFeature.caseDisplayRepresentations.keys), Set(TandemFeature.allCases))
    }

    func testUndoNeedsBothTheBudgetAndTheTransaction() {
        let info = AutomationUndoInfo.make(budgetId: "budget-1", transactionId: "tandem:1")
        XCTAssertEqual(AutomationUndoInfo.read(info), AutomationUndoInfo(budgetId: "budget-1", transactionId: "tandem:1"))
        XCTAssertNil(AutomationUndoInfo.read(["budget": "budget-1"]))
        XCTAssertNil(AutomationUndoInfo.read(["budget": "", "transaction": "tandem:1"]))
    }

    func testAnUnusableAmountFailsWithAClearMessage() {
        let error = TandemIntentError(message: "Enter an amount other than zero.")
        XCTAssertEqual(String(localized: error.localizedStringResource), "Enter an amount other than zero.")
    }
}
