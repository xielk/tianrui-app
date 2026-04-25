import XCTest
@testable import Tianruiapp

final class RequiredAssetTests: XCTestCase {
    func test_requiredAssetNamesAreDeclared() {
        let names = RequiredAssets.all
        XCTAssertTrue(names.contains("app_logo"))
        XCTAssertTrue(names.contains("f2_bike"))
        XCTAssertTrue(names.contains("nav_home"))
    }
}
