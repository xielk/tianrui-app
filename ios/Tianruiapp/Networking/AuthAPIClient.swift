import Foundation
import CryptoKit
import UIKit

struct LoginResponseData: Decodable {
    let uuid: String
    let token: String

    private enum CodingKeys: String, CodingKey {
        case uuid
        case uuidUpper = "UUID"
        case token
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        if let lower = try container.decodeIfPresent(String.self, forKey: .uuid), !lower.isEmpty {
            uuid = lower
        } else {
            uuid = try container.decode(String.self, forKey: .uuidUpper)
        }
        token = try container.decodeIfPresent(String.self, forKey: .token) ?? ""
    }
}

struct DeviceSummary: Decodable {
    let shortName: String
    let deviceKey: String

    private enum CodingKeys: String, CodingKey {
        case shortName = "short_name"
        case deviceKey = "device_key"
    }
}

struct DeviceInfoResponse: Decodable {
    struct LastTripInfo: Decodable {
        let startTime: String

        private enum CodingKeys: String, CodingKey {
            case startTime = "start_time"
        }
    }

    struct DeviceModelInfo: Decodable {
        let controlModel: String
        let modelName: String
        let length: String
        let width: String
        let height: String
        let weight: String
        let motorPower: String
        let range: String
        let batteryCapacity: String

        private enum CodingKeys: String, CodingKey {
            case controlModel = "control_model"
            case modelName = "model_name"
            case length
            case width
            case height
            case weight
            case motorPower = "motor_power"
            case range
            case batteryCapacity = "battery_capacity"
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            controlModel = try container.decodeIfPresent(String.self, forKey: .controlModel) ?? ""
            modelName = try container.decodeIfPresent(String.self, forKey: .modelName) ?? ""
            length = Self.decodeText(container: container, key: .length)
            width = Self.decodeText(container: container, key: .width)
            height = Self.decodeText(container: container, key: .height)
            weight = Self.decodeText(container: container, key: .weight)
            motorPower = Self.decodeText(container: container, key: .motorPower)
            range = Self.decodeText(container: container, key: .range)
            batteryCapacity = Self.decodeText(container: container, key: .batteryCapacity)
        }

        private static func decodeText(container: KeyedDecodingContainer<CodingKeys>, key: CodingKeys) -> String {
            if let text = try? container.decode(String.self, forKey: key) {
                let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
                if !value.isEmpty {
                    return value
                }
            }
            if let intValue = try? container.decode(Int.self, forKey: key) {
                return String(intValue)
            }
            if let doubleValue = try? container.decode(Double.self, forKey: key) {
                if doubleValue.truncatingRemainder(dividingBy: 1) == 0 {
                    return String(Int(doubleValue))
                }
                return String(doubleValue)
            }
            if let boolValue = try? container.decode(Bool.self, forKey: key) {
                return boolValue ? "1" : "0"
            }
            return ""
        }
    }

    let deviceKey: String
    let shortName: String
    let frameNo: String
    let iotVersion: String
    let imei: String
    let bluetoothMacAddress: String
    let signalStrength: Int
    let onlineStatus: Int
    let lastTripStartTime: String
    let deviceModel: DeviceModelInfo?
    let latitude: Double?
    let longitude: Double?
    let address: String?

    private enum CodingKeys: String, CodingKey {
        case deviceKey = "device_key"
        case shortName = "short_name"
        case frameNo = "frame_no"
        case iotVersion = "iot_version"
        case imei
        case bluetoothMacAddress = "bluetooth_mac_address"
        case signalStrength = "signal_strength"
        case onlineStatus = "online_status"
        case lastTrip = "last_trip"
        case deviceModel = "device_model"
        case latitude
        case longitude
        case lat
        case lng
        case address
        case currentLocationAddress = "current_location_address"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        deviceKey = try container.decodeIfPresent(String.self, forKey: .deviceKey) ?? ""
        shortName = try container.decodeIfPresent(String.self, forKey: .shortName) ?? ""
        frameNo = try container.decodeIfPresent(String.self, forKey: .frameNo) ?? ""
        iotVersion = try container.decodeIfPresent(String.self, forKey: .iotVersion) ?? ""
        imei = try container.decodeIfPresent(String.self, forKey: .imei) ?? ""
        bluetoothMacAddress = try container.decodeIfPresent(String.self, forKey: .bluetoothMacAddress) ?? ""
        if let signalInt = try? container.decode(Int.self, forKey: .signalStrength) {
            signalStrength = signalInt
        } else if let signalString = try? container.decode(String.self, forKey: .signalStrength),
                  let parsed = Int(signalString) {
            signalStrength = parsed
        } else if let signalBool = try? container.decode(Bool.self, forKey: .signalStrength) {
            signalStrength = signalBool ? 1 : 0
        } else {
            signalStrength = 0
        }
        if let onlineInt = try? container.decode(Int.self, forKey: .onlineStatus) {
            onlineStatus = onlineInt
        } else if let onlineString = try? container.decode(String.self, forKey: .onlineStatus),
                  let parsed = Int(onlineString) {
            onlineStatus = parsed
        } else if let onlineBool = try? container.decode(Bool.self, forKey: .onlineStatus) {
            onlineStatus = onlineBool ? 1 : 0
        } else {
            onlineStatus = 0
        }

        if let lastTrip = try? container.decodeIfPresent(LastTripInfo.self, forKey: .lastTrip) {
            lastTripStartTime = lastTrip.startTime.trimmingCharacters(in: .whitespacesAndNewlines)
        } else {
            lastTripStartTime = ""
        }

        deviceModel = try container.decodeIfPresent(DeviceModelInfo.self, forKey: .deviceModel)

        let latPrimary = try container.decodeIfPresent(Double.self, forKey: .latitude)
        let latFallback = try container.decodeIfPresent(Double.self, forKey: .lat)
        latitude = latPrimary ?? latFallback

        let lngPrimary = try container.decodeIfPresent(Double.self, forKey: .longitude)
        let lngFallback = try container.decodeIfPresent(Double.self, forKey: .lng)
        longitude = lngPrimary ?? lngFallback

        let addrPrimary = try container.decodeIfPresent(String.self, forKey: .address)
        let addrFallback = try container.decodeIfPresent(String.self, forKey: .currentLocationAddress)
        address = addrPrimary ?? addrFallback
    }
}

struct DeviceTrackPoint: Equatable {
    let latitude: Double
    let longitude: Double
}

struct SharedUserItem: Decodable, Equatable, Identifiable {
    let memberId: String
    let phone: String
    let isOwner: Bool

    var id: String { memberId }

    private enum CodingKeys: String, CodingKey {
        case memberId = "member_id"
        case phone
        case isOwner = "is_owner"
    }

    init(memberId: String, phone: String, isOwner: Bool) {
        self.memberId = memberId
        self.phone = phone
        self.isOwner = isOwner
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        memberId = Self.decodeText(container: container, key: .memberId)
        phone = Self.decodeText(container: container, key: .phone)
        if let intFlag = try? container.decode(Int.self, forKey: .isOwner) {
            isOwner = intFlag == 1
        } else if let boolFlag = try? container.decode(Bool.self, forKey: .isOwner) {
            isOwner = boolFlag
        } else {
            isOwner = false
        }
    }

    private static func decodeText(container: KeyedDecodingContainer<CodingKeys>, key: CodingKeys) -> String {
        if let text = try? container.decode(String.self, forKey: key) {
            let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
            if !value.isEmpty {
                return value
            }
        }
        if let intValue = try? container.decode(Int.self, forKey: key) {
            return String(intValue)
        }
        if let doubleValue = try? container.decode(Double.self, forKey: key) {
            if doubleValue.truncatingRemainder(dividingBy: 1) == 0 {
                return String(Int(doubleValue))
            }
            return String(doubleValue)
        }
        return ""
    }
}

private struct SharedUsersPayload: Decodable {
    let users: [SharedUserItem]
}

private struct APIEnvelope<T: Decodable>: Decodable {
    let code: Int
    let message: String
    let data: T?
}

private struct LoginOrRegisterRequest: Encodable {
    let phone: String?
    let code: String

    enum CodingKeys: String, CodingKey {
        case phone
        case code
    }
}

private struct DeviceKeyRequest: Encodable {
    let deviceKey: String

    private enum CodingKeys: String, CodingKey {
        case deviceKey = "device_key"
    }
}

private struct InductionLockRequest: Encodable {
    let deviceKey: String
    let inductionLock: Int

    private enum CodingKeys: String, CodingKey {
        case deviceKey = "device_key"
        case inductionLock = "induction_lock"
    }
}

private struct SilenceLockRequest: Encodable {
    let deviceKey: String
    let silenceLock: Int

    private enum CodingKeys: String, CodingKey {
        case deviceKey = "device_key"
        case silenceLock = "silence_lock"
    }
}

private struct ShareDeviceRequest: Encodable {
    let deviceKey: String
    let phone: String

    private enum CodingKeys: String, CodingKey {
        case deviceKey = "device_key"
        case phone
    }
}

private struct RemoveSharedUserRequest: Encodable {
    let deviceKey: String
    let memberId: String

    private enum CodingKeys: String, CodingKey {
        case deviceKey = "device_key"
        case memberId = "member_id"
    }
}

private struct ChangeOwnerRequest: Encodable {
    let deviceKey: String
    let newOwnerPhone: String

    private enum CodingKeys: String, CodingKey {
        case deviceKey = "device_key"
        case newOwnerPhone = "new_owner_phone"
    }
}

private struct UpsertPushDeviceRequest: Encodable {
    let token: String
    let platform: String
    let appInstanceId: String
    let deviceModel: String
    let appVersion: String

    private enum CodingKeys: String, CodingKey {
        case token
        case platform
        case appInstanceId = "app_instance_id"
        case deviceModel = "device_model"
        case appVersion = "app_version"
    }
}

enum AuthAPIError: LocalizedError {
    case invalidURL
    case invalidResponse
    case server(String)
    case emptyData

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "请求地址无效"
        case .invalidResponse:
            return "服务器响应异常"
        case .server(let message):
            return message
        case .emptyData:
            return "返回数据为空"
        }
    }
}

final class AuthAPIClient {
    static let shared = AuthAPIClient()

    private let baseURL = URL(string: "https://api.tr.sheyutech.com/app-api/")!
    private let apiKey = "4297f44b13955235245b2497399d7a93"
    private let jsonDecoder = JSONDecoder()
    private let jsonEncoder = JSONEncoder()

    private init() {}

    func sendVerificationCode(phone: String) async throws {
        guard var components = URLComponents(url: baseURL.appending(path: "api/sms/send"), resolvingAgainstBaseURL: false) else {
            throw AuthAPIError.invalidURL
        }
        components.queryItems = [URLQueryItem(name: "phone", value: phone)]
        guard let url = components.url else {
            throw AuthAPIError.invalidURL
        }

        let request = try signedRequest(url: url, method: "POST", body: nil)
        let _: APIEnvelope<EmptyResponse> = try await perform(request)
    }

    func loginWithSms(phone: String, code: String) async throws -> LoginResponseData {
        let url = baseURL.appending(path: "api/login-or-register")
        let payload = LoginOrRegisterRequest(phone: phone, code: code)
        let body = try jsonEncoder.encode(payload)
        let request = try signedRequest(url: url, method: "POST", body: body)

        let response: APIEnvelope<LoginResponseData> = try await perform(request)
        guard let data = response.data else {
            throw AuthAPIError.emptyData
        }
        return data
    }

    func fetchUserDevices() async throws -> [DeviceSummary] {
        let url = baseURL.appending(path: "mine/user-devices")
        let request = try signedRequest(url: url, method: "GET", body: nil)
        let response: APIEnvelope<[DeviceSummary]> = try await perform(request)
        return response.data ?? []
    }

    func fetchDeviceInfo(deviceKey: String) async throws -> DeviceInfoResponse {
        guard var components = URLComponents(url: baseURL.appending(path: "mine/device-info"), resolvingAgainstBaseURL: false) else {
            throw AuthAPIError.invalidURL
        }
        components.queryItems = [URLQueryItem(name: "device_key", value: deviceKey)]
        guard let url = components.url else {
            throw AuthAPIError.invalidURL
        }

        let request = try signedRequest(url: url, method: "GET", body: nil)
        let response: APIEnvelope<DeviceInfoResponse> = try await perform(request)
        guard let data = response.data else {
            throw AuthAPIError.emptyData
        }
        return data
    }

    func setDefaultDevice(deviceKey: String) async throws {
        let url = baseURL.appending(path: "mine/set-default-device")
        let payload = DeviceKeyRequest(deviceKey: deviceKey)
        let body = try jsonEncoder.encode(payload)
        let request = try signedRequest(url: url, method: "POST", body: body)
        let _: APIEnvelope<EmptyResponse> = try await perform(request)
    }

    func bindDevice(deviceKey: String) async throws {
        let url = baseURL.appending(path: "mine/bind_device")
        let payload = DeviceKeyRequest(deviceKey: deviceKey)
        let body = try jsonEncoder.encode(payload)
        let request = try signedRequest(url: url, method: "POST", body: body)
        let _: APIEnvelope<EmptyResponse> = try await perform(request)
    }

    func findBike(deviceKey: String) async throws {
        guard var components = URLComponents(url: baseURL.appending(path: "api/control"), resolvingAgainstBaseURL: false) else {
            throw AuthAPIError.invalidURL
        }
        components.queryItems = [
            URLQueryItem(name: "device_key", value: deviceKey),
            URLQueryItem(name: "action", value: "OPEN_QUERY"),
        ]
        guard let url = components.url else {
            throw AuthAPIError.invalidURL
        }
        let request = try signedRequest(url: url, method: "GET", body: nil)
        let _: APIEnvelope<EmptyResponse> = try await perform(request)
    }

    func toggleLock(deviceKey: String, locked: Bool) async throws {
        guard var components = URLComponents(url: baseURL.appending(path: "api/control"), resolvingAgainstBaseURL: false) else {
            throw AuthAPIError.invalidURL
        }
        components.queryItems = [
            URLQueryItem(name: "device_key", value: deviceKey),
            URLQueryItem(name: "action", value: locked ? "CLOSE_LOCK" : "OPEN_LOCK"),
        ]
        guard let url = components.url else {
            throw AuthAPIError.invalidURL
        }
        let request = try signedRequest(url: url, method: "GET", body: nil)
        let _: APIEnvelope<EmptyResponse> = try await perform(request)
    }

    func toggleMute(deviceKey: String, mute: Bool) async throws {
        let url = baseURL.appending(path: "mine/silence-lock")
        let payload = SilenceLockRequest(deviceKey: deviceKey, silenceLock: mute ? 1 : 0)
        let body = try jsonEncoder.encode(payload)
        let request = try signedRequest(url: url, method: "POST", body: body)
        let _: APIEnvelope<EmptyResponse> = try await perform(request)
    }

    func toggleAutoSense(deviceKey: String, enabled: Bool) async throws {
        let url = baseURL.appending(path: "mine/induction-lock")
        let payload = InductionLockRequest(deviceKey: deviceKey, inductionLock: enabled ? 1 : 0)
        let body = try jsonEncoder.encode(payload)
        let request = try signedRequest(url: url, method: "POST", body: body)
        let _: APIEnvelope<EmptyResponse> = try await perform(request)
    }

    func fetchSharedUsers(deviceKey: String) async throws -> [SharedUserItem] {
        guard var components = URLComponents(url: baseURL.appending(path: "mine/shared-users"), resolvingAgainstBaseURL: false) else {
            throw AuthAPIError.invalidURL
        }
        components.queryItems = [URLQueryItem(name: "device_key", value: deviceKey)]
        guard let url = components.url else {
            throw AuthAPIError.invalidURL
        }

        let request = try signedRequest(url: url, method: "GET", body: nil)
        let (data, response) = try await send(request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw AuthAPIError.invalidResponse
        }
        guard httpResponse.statusCode < 400 else {
            throw AuthAPIError.server("获取用车人失败")
        }

        let raw = try JSONSerialization.jsonObject(with: data, options: [])
        if let root = raw as? [String: Any],
           let code = Self.parseInt(root["code"]),
           code != 0 {
            let message = (root["message"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
            throw AuthAPIError.server((message?.isEmpty == false) ? message! : "获取用车人失败")
        }

        return Self.decodeSharedUsers(from: raw)
    }

    static func decodeSharedUsers(from raw: Any) -> [SharedUserItem] {
        guard let root = raw as? [String: Any] else {
            return []
        }

        let userObjects: [[String: Any]]
        if let data = root["data"] as? [String: Any],
           let users = data["users"] as? [[String: Any]] {
            userObjects = users
        } else {
            return []
        }

        return userObjects.compactMap { item in
            let memberId = parseString(item["member_id"]) ?? ""
            let phone = parseString(item["phone"]) ?? ""
            guard !memberId.isEmpty, !phone.isEmpty else {
                return nil
            }
            let isOwner = (parseInt(item["is_owner"]) ?? 0) == 1
            return SharedUserItem(memberId: memberId, phone: phone, isOwner: isOwner)
        }
    }

    func shareDevice(deviceKey: String, phone: String) async throws {
        let url = baseURL.appending(path: "mine/share-device")
        let payload = ShareDeviceRequest(deviceKey: deviceKey, phone: phone)
        let body = try jsonEncoder.encode(payload)
        let request = try signedRequest(url: url, method: "POST", body: body)
        let _: APIEnvelope<EmptyResponse> = try await perform(request)
    }

    func removeSharedUser(deviceKey: String, memberId: String) async throws {
        let url = baseURL.appending(path: "mine/remove-shared-user")
        let body = try Self.makeRemoveSharedUserBody(deviceKey: deviceKey, memberId: memberId)
        let request = try signedRequest(url: url, method: "POST", body: body)
        let _: APIEnvelope<EmptyResponse> = try await perform(request)
    }

    static func makeRemoveSharedUserBody(deviceKey: String, memberId: String) throws -> Data {
        var payload: [String: Any] = ["device_key": deviceKey]
        if let numericMemberId = Int(memberId) {
            payload["member_id"] = numericMemberId
        } else {
            payload["member_id"] = memberId
        }
        return try JSONSerialization.data(withJSONObject: payload, options: [])
    }

    func changeOwner(deviceKey: String, newOwnerPhone: String) async throws {
        let url = baseURL.appending(path: "mine/change-owner")
        let payload = ChangeOwnerRequest(deviceKey: deviceKey, newOwnerPhone: newOwnerPhone)
        let body = try jsonEncoder.encode(payload)
        let request = try signedRequest(url: url, method: "POST", body: body)
        let _: APIEnvelope<EmptyResponse> = try await perform(request)
    }

    func upsertPushDevice(token: String,
                          platform: String,
                          appInstanceId: String,
                          deviceModel: String,
                          appVersion: String) async throws {
#if DEBUG
        print("[TPNS][API][REQUEST] endpoint=mine/upsert-push-device token=\(PushDeviceReporter.debugTokenPreview(token)) platform=\(platform)")
#endif
        let url = baseURL.appending(path: "mine/upsert-push-device")
        let payload = UpsertPushDeviceRequest(token: token,
                                              platform: platform,
                                              appInstanceId: appInstanceId,
                                              deviceModel: deviceModel,
                                              appVersion: appVersion)
        let body = try jsonEncoder.encode(payload)
        let request = try signedRequest(url: url, method: "POST", body: body)
        let _: APIEnvelope<EmptyResponse> = try await perform(request)
#if DEBUG
        print("[TPNS][API][SUCCESS] endpoint=mine/upsert-push-device token=\(PushDeviceReporter.debugTokenPreview(token))")
#endif
    }

    func fetchLocationAddress(lat: Double, lng: Double) async throws -> String {
        guard var components = URLComponents(url: baseURL.appending(path: "api/location-address"), resolvingAgainstBaseURL: false) else {
            throw AuthAPIError.invalidURL
        }
        components.queryItems = [
            URLQueryItem(name: "lat", value: String(lat)),
            URLQueryItem(name: "lng", value: String(lng)),
        ]
        guard let url = components.url else {
            throw AuthAPIError.invalidURL
        }
        let request = try signedRequest(url: url, method: "GET", body: nil)
        let (data, response) = try await send(request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw AuthAPIError.invalidResponse
        }
        guard httpResponse.statusCode < 400 else {
            throw AuthAPIError.server("地址解析失败")
        }

        let raw = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any]
        guard let code = raw?["code"] as? Int, code == 0 else {
            let msg = raw?["message"] as? String ?? "地址解析失败"
            throw AuthAPIError.server(msg)
        }
        if let dataMap = raw?["data"] as? [String: Any] {
            if let address = dataMap["address"] as? String, !address.isEmpty {
                return address
            }
            if let formatted = dataMap["formatted_address"] as? String, !formatted.isEmpty {
                return formatted
            }
        }
        return ""
    }

    func fetchDeviceTrackPoints(deviceKey: String, interval: Int = 20) async throws -> [DeviceTrackPoint] {
        guard var components = URLComponents(url: baseURL.appending(path: "mine/device-track"), resolvingAgainstBaseURL: false) else {
            throw AuthAPIError.invalidURL
        }
        components.queryItems = [
            URLQueryItem(name: "device_key", value: deviceKey),
            URLQueryItem(name: "interval", value: String(interval)),
        ]
        guard let url = components.url else {
            throw AuthAPIError.invalidURL
        }

        let request = try signedRequest(url: url, method: "GET", body: nil)
        let (data, response) = try await send(request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw AuthAPIError.invalidResponse
        }
        guard httpResponse.statusCode < 400 else {
            throw AuthAPIError.server("获取轨迹失败")
        }

        let raw = try JSONSerialization.jsonObject(with: data, options: [])
        if let root = raw as? [String: Any],
           let code = root["code"] as? Int,
           code != 0 {
            let message = (root["message"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
            throw AuthAPIError.server((message?.isEmpty == false) ? message! : "获取轨迹失败")
        }
        return Self.decodeTrackPoints(from: raw)
    }

    static func decodeTrackPoints(from raw: Any) -> [DeviceTrackPoint] {
        guard let root = raw as? [String: Any],
              let data = root["data"] as? [String: Any],
              let polyline = data["polyline"] as? [[String: Any]],
              let first = polyline.first,
              let points = first["points"] as? [[String: Any]] else {
            return []
        }

        return points.compactMap { point in
            guard let lat = parseDouble(point["latitude"]),
                  let lng = parseDouble(point["longitude"]) else {
                return nil
            }
            return DeviceTrackPoint(latitude: lat, longitude: lng)
        }
    }

    private static func parseDouble(_ value: Any?) -> Double? {
        if let double = value as? Double {
            return double
        }
        if let int = value as? Int {
            return Double(int)
        }
        if let text = value as? String {
            return Double(text)
        }
        return nil
    }

    private static func parseInt(_ value: Any?) -> Int? {
        if let int = value as? Int {
            return int
        }
        if let bool = value as? Bool {
            return bool ? 1 : 0
        }
        if let text = value as? String {
            return Int(text)
        }
        return nil
    }

    private static func parseString(_ value: Any?) -> String? {
        if let text = value as? String {
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
        if let int = value as? Int {
            return String(int)
        }
        if let double = value as? Double {
            if double.truncatingRemainder(dividingBy: 1) == 0 {
                return String(Int(double))
            }
            return String(double)
        }
        return nil
    }

    private func perform<T: Decodable>(_ request: URLRequest) async throws -> APIEnvelope<T> {
        let (data, response) = try await send(request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw AuthAPIError.invalidResponse
        }

        let envelope: APIEnvelope<T>
        do {
            envelope = try jsonDecoder.decode(APIEnvelope<T>.self, from: data)
        } catch {
            debugLogDecodeFailure(request: request, responseData: data, error: error)
            throw error
        }
        if httpResponse.statusCode >= 400 || envelope.code != 0 {
            throw AuthAPIError.server(envelope.message.isEmpty ? "请求失败" : envelope.message)
        }
        return envelope
    }

    private func send(_ request: URLRequest) async throws -> (Data, URLResponse) {
        debugLogRequest(request)
        do {
            let result = try await URLSession.shared.data(for: request)
            debugLogResponse(request: request, data: result.0, response: result.1, error: nil)
            return result
        } catch {
            debugLogResponse(request: request, data: nil, response: nil, error: error)
            throw error
        }
    }

    private func debugLogRequest(_ request: URLRequest) {
#if DEBUG
        let method = request.httpMethod ?? "GET"
        let urlText = request.url?.absoluteString ?? ""
        let headers = request.allHTTPHeaderFields ?? [:]
        let bodyText = debugBodyString(from: request.httpBody)
        print("[API][REQ] \(method) \(urlText)")
        print("[API][REQ][HEADERS] \(headers)")
        print("[API][REQ][BODY] \(bodyText)")
#endif
    }

    private func debugLogResponse(request: URLRequest, data: Data?, response: URLResponse?, error: Error?) {
#if DEBUG
        let method = request.httpMethod ?? "GET"
        let urlText = request.url?.absoluteString ?? ""
        if let error {
            print("[API][RES] \(method) \(urlText) ERROR: \(error.localizedDescription)")
            return
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            print("[API][RES] \(method) \(urlText) INVALID_RESPONSE")
            return
        }

        let responseBody = debugBodyString(from: data)
        print("[API][RES] \(method) \(urlText) STATUS: \(httpResponse.statusCode)")
        print("[API][RES][HEADERS] \(httpResponse.allHeaderFields)")
        print("[API][RES][BODY] \(responseBody)")
#endif
    }

    private func debugLogDecodeFailure(request: URLRequest, responseData: Data, error: Error) {
#if DEBUG
        let method = request.httpMethod ?? "GET"
        let urlText = request.url?.absoluteString ?? ""
        print("[API][DECODE][FAIL] \(method) \(urlText) ERROR: \(error.localizedDescription)")
        print("[API][DECODE][RAW] \(debugBodyString(from: responseData))")
#endif
    }

    private func debugBodyString(from data: Data?) -> String {
        guard let data else { return "<empty>" }
        if data.isEmpty { return "<empty>" }
        if let text = String(data: data, encoding: .utf8) {
            return text
        }
        return "<\(data.count) bytes binary>"
    }

    private func signedRequest(url: URL, method: String, body: Data?) throws -> URLRequest {
        let timestamp = String(Int(Date().timeIntervalSince1970))
        let pathWithQuery = url.path + (url.query.map { "?\($0)" } ?? "")
        let bodyString = body.flatMap { String(data: $0, encoding: .utf8) } ?? ""
        let sign = generateSignature(timestamp: timestamp, method: method, path: pathWithQuery, body: bodyString)
        let uuid = (UserDefaults.standard.string(forKey: "auth_uuid") ?? "")

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(uuid, forHTTPHeaderField: "UUID")
        request.setValue(timestamp, forHTTPHeaderField: "X-Timestamp")
        request.setValue(sign, forHTTPHeaderField: "X-Sign")
        return request
    }

    private func generateSignature(timestamp: String, method: String, path: String, body: String) -> String {
        var params: [String: String] = ["timestamp": timestamp]

        if let queryIndex = path.firstIndex(of: "?") {
            let query = String(path[path.index(after: queryIndex)...])
            for item in query.split(separator: "&") {
                let pair = item.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
                guard let key = pair.first.map(String.init), !key.isEmpty else { continue }
                let value = pair.count > 1 ? String(pair[1]) : ""
                params[key] = value
            }
        }

        let upperMethod = method.uppercased()
        if (upperMethod == "POST" || upperMethod == "PUT") && !body.isEmpty {
            params["body"] = body
        }

        let sorted = params.keys.sorted()
        let base = sorted.map { "\($0)=\(params[$0] ?? "")" }.joined(separator: "&") + "&key=\(apiKey)"
        return Insecure.MD5.hash(data: Data(base.utf8)).map { String(format: "%02x", $0) }.joined().uppercased()
    }
}

private struct EmptyResponse: Decodable {}
