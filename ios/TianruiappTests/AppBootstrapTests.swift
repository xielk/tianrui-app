import XCTest
@testable import Tianruiapp

final class AppBootstrapTests: XCTestCase {
    func test_canInstantiateRootScreenType() {
        _ = RootScreen()
        XCTAssertTrue(true)
    }

    func test_hasSessionWhenOnlyUUIDPresent() {
        XCTAssertTrue(RootScreen.hasSession(token: "", uuid: "u-1"))
    }
}
