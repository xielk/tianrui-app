import XCTest
@testable import Tianruiapp

final class F1LayoutTests: XCTestCase {
    func test_f1MainCardCornerRadius() {
        XCTAssertEqual(F1Layout.mainCardRadius, 34)
    }

    func test_channelAvailability_hasAnyChannelWhenBleAvailable() {
        let availability = ChannelAvailability(bleAvailable: true, networkAvailable: false)
        XCTAssertTrue(availability.hasAnyChannel)
    }

    func test_channelAvailability_hasAnyChannelWhenNetworkAvailable() {
        let availability = ChannelAvailability(bleAvailable: false, networkAvailable: true)
        XCTAssertTrue(availability.hasAnyChannel)
    }

    func test_channelAvailability_hasNoChannelWhenBothUnavailable() {
        let availability = ChannelAvailability(bleAvailable: false, networkAvailable: false)
        XCTAssertFalse(availability.hasAnyChannel)
    }

    func test_mapURLBuilder_returnsNilWhenLocationInvalid() {
        XCTAssertNil(F1MapURLBuilder.staticMapURL(latitude: 0, longitude: 0))
    }

    func test_mapURLBuilder_buildsAmapStaticURLWhenLocationValid() {
        let url = F1MapURLBuilder.staticMapURL(latitude: 31.2304, longitude: 121.4737)
        XCTAssertNotNil(url)
        XCTAssertEqual(url?.host, "restapi.amap.com")
        let raw = url?.absoluteString ?? ""
        XCTAssertTrue(raw.contains("/v3/staticmap"))
        XCTAssertTrue(raw.contains("location=121.4737,31.2304"))
        XCTAssertTrue(raw.contains("markers=mid,0xFF0000,A:121.4737,31.2304"))
    }

    func test_mapAddressResolver_usesFallbackWhenAddressMissing() {
        XCTAssertEqual(
            F1MapAddressResolver.displayAddress(address: "", latitude: 31.2304, longitude: 121.4737),
            "获取地址中"
        )
        XCTAssertEqual(
            F1MapAddressResolver.displayAddress(address: "", latitude: 0, longitude: 0),
            "位置信息不可用"
        )
    }

    func test_mapSectionMode_splitForF2AndSingleForOthers() {
        XCTAssertEqual(F1MapSectionMode.forLayout(.f1), .single)
        XCTAssertEqual(F1MapSectionMode.forLayout(.m1b), .single)
        XCTAssertEqual(F1MapSectionMode.forLayout(.f2), .split)
    }

    func test_mapAddressVisibilityPolicy_hidesAddressForF2SplitMode() {
        XCTAssertTrue(F1MapAddressVisibilityPolicy.showsAddress(for: .f1))
        XCTAssertTrue(F1MapAddressVisibilityPolicy.showsAddress(for: .m1b))
        XCTAssertFalse(F1MapAddressVisibilityPolicy.showsAddress(for: .f2))
    }

    func test_controlChannelPolicy_forFOneUsesBleOnly() {
        XCTAssertEqual(F1ControlChannelPolicy.select(isF2: false, bleReady: true, networkOnline: true), .ble)
        XCTAssertEqual(F1ControlChannelPolicy.select(isF2: false, bleReady: false, networkOnline: true), .none)
    }

    func test_controlChannelPolicy_forFTwoFallsBackToCellular() {
        XCTAssertEqual(F1ControlChannelPolicy.select(isF2: true, bleReady: true, networkOnline: true), .ble)
        XCTAssertEqual(F1ControlChannelPolicy.select(isF2: true, bleReady: false, networkOnline: true), .cellular4G)
        XCTAssertEqual(F1ControlChannelPolicy.select(isF2: true, bleReady: false, networkOnline: false), .none)
    }

    func test_trackMapDisplayPolicy_showsNoDataHintWhenPointsEmpty() {
        XCTAssertTrue(F1TrackMapDisplayPolicy.shouldShowNoDataHint(points: []))
        XCTAssertFalse(
            F1TrackMapDisplayPolicy.shouldShowNoDataHint(
                points: [DeviceTrackPoint(latitude: 31.23, longitude: 121.47)]
            )
        )
    }

    func test_productModelDisplayPolicy_usesModelNameOnly() {
        let json = """
        {
          "device_key": "d-1",
          "device_model": {
            "control_model": "F2",
            "model_name": "M1B"
          }
        }
        """
        let info = try? JSONDecoder().decode(DeviceInfoResponse.self, from: Data(json.utf8))

        XCTAssertEqual(F1ProductModelDisplayPolicy.modelName(from: info), "M1B")
        XCTAssertEqual(F1ProductModelDisplayPolicy.modelName(from: nil), "")
    }
}
