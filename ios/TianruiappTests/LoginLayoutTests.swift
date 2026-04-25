import XCTest
@testable import Tianruiapp

final class LoginLayoutTests: XCTestCase {
    func test_loginPrimaryBlueHex() {
        XCTAssertEqual(AppColor.primaryBlueHex, "#1383F6")
    }

    func test_loginStaticMetrics() {
        XCTAssertEqual(LoginLayout.primaryButtonHeight, 50)
        XCTAssertEqual(LoginLayout.horizontalPadding, 30)
        XCTAssertEqual(LoginLayout.buttonCornerRadius, 8)
    }

    func test_loginResponseDataDecodesUppercaseUUID() throws {
        let data = Data("{\"UUID\":\"u-1\",\"token\":\"t-1\"}".utf8)
        let decoded = try JSONDecoder().decode(LoginResponseData.self, from: data)
        XCTAssertEqual(decoded.uuid, "u-1")
        XCTAssertEqual(decoded.token, "t-1")
    }

    func test_loginResponseDataDecodesWhenTokenMissing() throws {
        let data = Data("{\"UUID\":\"u-2\"}".utf8)
        let decoded = try JSONDecoder().decode(LoginResponseData.self, from: data)
        XCTAssertEqual(decoded.uuid, "u-2")
        XCTAssertEqual(decoded.token, "")
    }

    func test_sharedUserItemDecodesSnakeCaseFields() throws {
        let data = Data("{\"member_id\":\"m-1\",\"phone\":\"13812345678\",\"is_owner\":1}".utf8)
        let decoded = try JSONDecoder().decode(SharedUserItem.self, from: data)
        XCTAssertEqual(decoded.memberId, "m-1")
        XCTAssertEqual(decoded.phone, "13812345678")
        XCTAssertTrue(decoded.isOwner)
    }

    func test_sharedUserItemDecodesNumericMemberId() throws {
        let data = Data("{\"member_id\":12345,\"phone\":\"13812345678\",\"is_owner\":0}".utf8)
        let decoded = try JSONDecoder().decode(SharedUserItem.self, from: data)
        XCTAssertEqual(decoded.memberId, "12345")
        XCTAssertEqual(decoded.phone, "13812345678")
        XCTAssertFalse(decoded.isOwner)
    }

    func test_decodeSharedUsers_ignoresArrayDataShape() throws {
        let json = """
        {
          "code": 0,
          "message": "ok",
          "data": [
            { "member_id": 1001, "phone": "13812345678", "is_owner": 1 },
            { "member_id": "1002", "phone": "13900001111", "is_owner": 0 }
          ]
        }
        """
        let raw = try JSONSerialization.jsonObject(with: Data(json.utf8), options: [])
        let users = AuthAPIClient.decodeSharedUsers(from: raw)
        XCTAssertTrue(users.isEmpty)
    }

    func test_decodeSharedUsers_readsDataUsersShape() throws {
        let json = """
        {
          "code": 0,
          "message": "ok",
          "data": {
            "users": [
              { "member_id": "1001", "phone": "13812345678", "is_owner": 1 }
            ]
          }
        }
        """
        let raw = try JSONSerialization.jsonObject(with: Data(json.utf8), options: [])
        let users = AuthAPIClient.decodeSharedUsers(from: raw)
        XCTAssertEqual(users.count, 1)
        XCTAssertEqual(users[0].memberId, "1001")
    }

    func test_decodeSharedUsers_ignoresUserWithoutMemberId() throws {
        let json = """
        {
          "code": 0,
          "message": "ok",
          "data": {
            "users": [
              { "share_uuid": "u-1", "phone": "13812345678", "is_owner": 0 }
            ]
          }
        }
        """
        let raw = try JSONSerialization.jsonObject(with: Data(json.utf8), options: [])
        let users = AuthAPIClient.decodeSharedUsers(from: raw)
        XCTAssertTrue(users.isEmpty)
    }

    func test_makeRemoveSharedUserBody_usesNumericMemberIdWhenPossible() throws {
        let data = try AuthAPIClient.makeRemoveSharedUserBody(deviceKey: "d-1", memberId: "1001")
        let raw = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any]
        XCTAssertEqual(raw?["device_key"] as? String, "d-1")
        XCTAssertEqual(raw?["member_id"] as? Int, 1001)
    }

    func test_makeRemoveSharedUserBody_keepsStringMemberIdWhenNotNumeric() throws {
        let data = try AuthAPIClient.makeRemoveSharedUserBody(deviceKey: "d-1", memberId: "m-1001")
        let raw = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any]
        XCTAssertEqual(raw?["device_key"] as? String, "d-1")
        XCTAssertEqual(raw?["member_id"] as? String, "m-1001")
    }

    func test_shareUserPhoneMask_masksMiddleDigits() {
        XCTAssertEqual(F1SharePhoneFormatter.mask("13812345678"), "138****5678")
    }

    func testDeviceInfoResponseDecodesOnlineStatusAndSignalFromStrings() throws {
        let json = """
        {
          "device_key": "d-1",
          "short_name": "F2",
          "frame_no": "f-1",
          "bluetooth_mac_address": "AA:BB:CC:DD:EE:FF",
          "signal_strength": "4",
          "online_status": "0"
        }
        """
        let data = Data(json.utf8)
        let decoded = try JSONDecoder().decode(DeviceInfoResponse.self, from: data)
        XCTAssertEqual(decoded.signalStrength, 4)
        XCTAssertEqual(decoded.onlineStatus, 0)
    }

    func testDeviceInfoResponseDecodesLastTripStartTime() throws {
        let json = """
        {
          "device_key": "d-1",
          "last_trip": {
            "start_time": "2026-04-21 10:22:33"
          }
        }
        """
        let data = Data(json.utf8)
        let decoded = try JSONDecoder().decode(DeviceInfoResponse.self, from: data)
        XCTAssertEqual(decoded.lastTripStartTime, "2026-04-21 10:22:33")
    }

    func testTrackPointsDecodeFromPolylinePayload() throws {
        let json = """
        {
          "code": 0,
          "message": "ok",
          "data": {
            "polyline": [
              {
                "points": [
                  { "latitude": 31.2304, "longitude": 121.4737 },
                  { "latitude": "31.2310", "longitude": "121.4740" }
                ]
              }
            ]
          }
        }
        """
        let raw = try JSONSerialization.jsonObject(with: Data(json.utf8), options: [])
        let points = AuthAPIClient.decodeTrackPoints(from: raw)
        XCTAssertEqual(points.count, 2)
        XCTAssertEqual(points[0].latitude, 31.2304, accuracy: 0.0001)
        XCTAssertEqual(points[0].longitude, 121.4737, accuracy: 0.0001)
        XCTAssertEqual(points[1].latitude, 31.2310, accuracy: 0.0001)
        XCTAssertEqual(points[1].longitude, 121.4740, accuracy: 0.0001)
    }

    func testDeviceInfoResponseDecodesExtendedDeviceModelFields() throws {
        let json = """
        {
          "device_key": "d-2",
          "iot_version": "V1.2.3",
          "imei": "123456789012345",
          "device_model": {
            "control_model": "F2",
            "model_name": "F2 Pro",
            "length": 180,
            "width": "72",
            "height": 110,
            "weight": "95",
            "motor_power": "1500",
            "range": 80,
            "battery_capacity": "24"
          }
        }
        """
        let data = Data(json.utf8)
        let decoded = try JSONDecoder().decode(DeviceInfoResponse.self, from: data)
        XCTAssertEqual(decoded.iotVersion, "V1.2.3")
        XCTAssertEqual(decoded.imei, "123456789012345")
        XCTAssertEqual(decoded.deviceModel?.modelName, "F2 Pro")
        XCTAssertEqual(decoded.deviceModel?.length, "180")
        XCTAssertEqual(decoded.deviceModel?.width, "72")
        XCTAssertEqual(decoded.deviceModel?.height, "110")
        XCTAssertEqual(decoded.deviceModel?.weight, "95")
        XCTAssertEqual(decoded.deviceModel?.motorPower, "1500")
        XCTAssertEqual(decoded.deviceModel?.range, "80")
        XCTAssertEqual(decoded.deviceModel?.batteryCapacity, "24")
    }

    func test_pushTokenNormalizer_removesAnglesSpacesAndUppercases() {
        let normalized = PushDeviceReporter.normalizeToken("<ab 12 cd 34>")
        XCTAssertEqual(normalized, "AB12CD34")
    }

    func test_pushReporter_canReport_requiresTokenAndUUID() {
        XCTAssertFalse(PushDeviceReporter.canReport(token: "", uuid: "u-1"))
        XCTAssertFalse(PushDeviceReporter.canReport(token: "abc", uuid: ""))
        XCTAssertTrue(PushDeviceReporter.canReport(token: "abc", uuid: "u-1"))
    }

    func test_pushTokenPreview_masksLongToken() {
        let preview = PushDeviceReporter.debugTokenPreview("AB12CD34EF56AB78")
        XCTAssertEqual(preview, "AB12CD...AB78")
    }

    func test_pushTokenPreview_keepsShortToken() {
        let preview = PushDeviceReporter.debugTokenPreview("AB12CD")
        XCTAssertEqual(preview, "AB12CD")
    }

    func test_tpnsCredentials_notConfiguredByDefault() {
        XCTAssertFalse(TPNSCredentials.isConfigured)
    }

    func test_shouldUploadToken_requiresTpnsSourceWhenConfigured() {
        XCTAssertFalse(PushDeviceReporter.shouldUploadToken(source: "apns", tpnsReady: true))
        XCTAssertTrue(PushDeviceReporter.shouldUploadToken(source: "tpns", tpnsReady: true))
        XCTAssertTrue(PushDeviceReporter.shouldUploadToken(source: "apns", tpnsReady: false))
    }

    func test_numberAuthErrorMessage_matchesAndroidForNotConfigured() {
        XCTAssertEqual(NumberAuthError.notConfigured.errorDescription, "号码认证未配置")
    }

    func test_numberAuthErrorMessage_matchesAndroidForNoViewController() {
        XCTAssertEqual(NumberAuthError.noViewController.errorDescription, "当前环境不支持号码认证")
    }

    func test_loginAgreementLink_serviceMetadata() {
        XCTAssertEqual(LoginAgreementLink.service.title, "用户协议")
        XCTAssertEqual(LoginAgreementLink.service.urlString, "https://cdn.tr.sheyutech.com/service.html")
    }

    func test_loginAgreementLink_privacyMetadata() {
        XCTAssertEqual(LoginAgreementLink.privacy.title, "隐私政策")
        XCTAssertEqual(LoginAgreementLink.privacy.urlString, "https://cdn.tr.sheyutech.com/privacy.html")
    }
}
