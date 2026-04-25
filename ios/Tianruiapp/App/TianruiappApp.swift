import SwiftUI
import CryptoKit
import UIKit
import UserNotifications

@main
struct TianruiappApp: App {
    @UIApplicationDelegateAdaptor(PushNotificationDelegate.self) private var pushDelegate

    var body: some Scene {
        WindowGroup {
            RootScreen()
        }
    }
}

enum PushDeviceReporter {
    private static let tokenStoreKey = "tpns_token"
    private static let tokenSourceStoreKey = "tpns_token_source"
    private static let lastUploadedTokenKey = "tpns_last_uploaded_token"
    private static let lastUploadedUUIDKey = "tpns_last_uploaded_uuid"
    private static let uploadGate = PushUploadGate()

    private actor PushUploadGate {
        private var inFlightKeys: Set<String> = []

        func begin(_ key: String) -> Bool {
            if inFlightKeys.contains(key) {
                return false
            }
            inFlightKeys.insert(key)
            return true
        }

        func end(_ key: String) {
            inFlightKeys.remove(key)
        }
    }

    static func normalizeToken(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "<", with: "")
            .replacingOccurrences(of: ">", with: "")
            .replacingOccurrences(of: " ", with: "")
            .uppercased()
    }

    static func canReport(token: String, uuid: String) -> Bool {
        !normalizeToken(token).isEmpty && !uuid.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    static func shouldUploadToken(source: String, tpnsReady: Bool) -> Bool {
        !tpnsReady || source == "tpns"
    }

    static func debugTokenPreview(_ value: String) -> String {
        let token = normalizeToken(value)
        guard !token.isEmpty else { return "<empty>" }
        if token.count <= 10 {
            return token
        }
        let prefix = String(token.prefix(6))
        let suffix = String(token.suffix(4))
        return "\(prefix)...\(suffix)"
    }

    static func saveTPNSToken(_ rawToken: String, source: String) {
        let normalized = normalizeToken(rawToken)
        guard !normalized.isEmpty else { return }
#if DEBUG
        print("[TPNS][TOKEN][RECEIVED] source=\(source) token=\(debugTokenPreview(normalized)) len=\(normalized.count)")
#endif
        UserDefaults.standard.set(normalized, forKey: tokenStoreKey)
        UserDefaults.standard.set(source, forKey: tokenSourceStoreKey)
#if DEBUG
        print("[TPNS][TOKEN][CACHED] source=\(source) token=\(debugTokenPreview(normalized))")
#endif
        Task {
            await reportCachedTokenIfPossible()
        }
    }

    static func reportCachedTokenIfPossible() async {
        let token = normalizeToken(UserDefaults.standard.string(forKey: tokenStoreKey) ?? "")
        let source = (UserDefaults.standard.string(forKey: tokenSourceStoreKey) ?? "").lowercased()
        let uuid = UserDefaults.standard.string(forKey: "auth_uuid") ?? ""
#if DEBUG
        print("[TPNS][UPLOAD][CHECK] source=\(source.isEmpty ? "unknown" : source) token=\(debugTokenPreview(token)) uuidEmpty=\(uuid.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)")
#endif
        guard canReport(token: token, uuid: uuid) else {
#if DEBUG
            print("[TPNS][UPLOAD][SKIP] missing token or uuid")
#endif
            return
        }

        guard shouldUploadToken(source: source, tpnsReady: TPNSCredentials.runtimeReady) else {
#if DEBUG
            print("[TPNS][UPLOAD][SKIP] waiting for tpns token, current source=\(source)")
#endif
            return
        }

        let uploadKey = "\(token)|\(uuid)"
        let started = await uploadGate.begin(uploadKey)
        guard started else {
#if DEBUG
            print("[TPNS][UPLOAD][SKIP] same token upload in-flight")
#endif
            return
        }
        defer {
            Task {
                await uploadGate.end(uploadKey)
            }
        }

        let lastToken = UserDefaults.standard.string(forKey: lastUploadedTokenKey) ?? ""
        let lastUUID = UserDefaults.standard.string(forKey: lastUploadedUUIDKey) ?? ""
        if token == lastToken && uuid == lastUUID {
#if DEBUG
            print("[TPNS][UPLOAD][SKIP] already uploaded token=\(debugTokenPreview(token))")
#endif
            return
        }

        do {
#if DEBUG
            print("[TPNS][UPLOAD][START] source=\(source.isEmpty ? "unknown" : source) token=\(debugTokenPreview(token)) uuid=\(uuid)")
#endif
            try await AuthAPIClient.shared.upsertPushDevice(token: token,
                                                            platform: "ios",
                                                            appInstanceId: appInstanceID(),
                                                            deviceModel: UIDevice.current.model,
                                                            appVersion: appVersion())
            UserDefaults.standard.set(token, forKey: lastUploadedTokenKey)
            UserDefaults.standard.set(uuid, forKey: lastUploadedUUIDKey)
#if DEBUG
            print("[TPNS][UPLOAD][SUCCESS] token=\(debugTokenPreview(token))")
#endif
        } catch {
#if DEBUG
            print("[TPNS][UPLOAD][FAIL] token=\(debugTokenPreview(token)) error=\(error.localizedDescription)")
#endif
        }
    }

    private static func appVersion() -> String {
        let short = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? ""
        if short.isEmpty { return build }
        if build.isEmpty { return short }
        return "\(short)-\(build)"
    }

    private static func appInstanceID() -> String {
        let vendor = UIDevice.current.identifierForVendor?.uuidString ?? ""
        let model = UIDevice.current.model
        let system = UIDevice.current.systemVersion
        let raw = [vendor, model, system].joined(separator: "|")
        return Insecure.MD5.hash(data: Data(raw.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

enum TPNSCredentials {
    static let enabled: Bool = false
    static let accessID: UInt32 = 0
    static let secretKey: String = ""
    static let accessKey: String = ""
    private(set) static var runtimeReady: Bool = false

    static var isConfigured: Bool {
        enabled && accessID != 0 && !secretKey.isEmpty && !accessKey.isEmpty
    }

    static func setRuntimeReady(_ value: Bool) {
        runtimeReady = value
    }

    static func accessKeyTail4() -> String {
        guard accessKey.count >= 4 else { return accessKey }
        return String(accessKey.suffix(4))
    }
}   

final class PushNotificationDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    private var tpnsManager: NSObject?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        startTPNSIfConfigured(launchOptions: launchOptions)
        guard TPNSCredentials.isConfigured else {
#if DEBUG
            print("[TPNS][DISABLED] TPNS disabled by config")
#endif
            return true
        }

        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
#if DEBUG
            print("[TPNS][REGISTER][AUTH] granted=\(granted)")
#endif
            guard granted else { return }
            DispatchQueue.main.async {
                application.registerForRemoteNotifications()
#if DEBUG
                print("[TPNS][REGISTER][REQUEST] requested APNs registration")
#endif
            }
        }
        Task {
            await PushDeviceReporter.reportCachedTokenIfPossible()
        }
        return true
    }

    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02X", $0) }.joined()
#if DEBUG
        print("[TPNS][REGISTER][SUCCESS] token=\(PushDeviceReporter.debugTokenPreview(token)) len=\(token.count)")
#endif
        if TPNSCredentials.runtimeReady {
#if DEBUG
            print("[TPNS][REGISTER][APNS] token cached, waiting TPNS xgToken")
#endif
            PushDeviceReporter.saveTPNSToken(token, source: "apns")
            return
        }
        PushDeviceReporter.saveTPNSToken(token, source: "apns")
    }

    private func startTPNSIfConfigured(launchOptions: [UIApplication.LaunchOptionsKey: Any]?) {
        TPNSCredentials.setRuntimeReady(false)
        guard TPNSCredentials.isConfigured else {
#if DEBUG
            print("[TPNS][CONFIG][SKIP] accessID/secretKey/accessKey not configured")
#endif
            return
        }

        guard let xgPushClass = NSClassFromString("XGPush") as? NSObject.Type,
              let managerObj = xgPushClass.perform(NSSelectorFromString("defaultManager"))?.takeUnretainedValue() as? NSObject else {
#if DEBUG
            print("[TPNS][CONFIG][FAIL] XGPush runtime class not found")
#endif
            return
        }

        tpnsManager = managerObj
        managerObj.setValue(true, forKey: "enableDebug")
        managerObj.setValue(NSMutableDictionary(dictionary: launchOptions ?? [:]), forKey: "launchOptions")

        let startSelector = NSSelectorFromString("startXGWithAccessID:accessKey:delegate:")
        guard managerObj.responds(to: startSelector) else {
#if DEBUG
            print("[TPNS][CONFIG][FAIL] XGPush missing startXGWithAccessID API")
#endif
            return
        }

        typealias StartXGFunc = @convention(c) (AnyObject, Selector, UInt32, NSString, AnyObject?) -> Void
        let startImp = managerObj.method(for: startSelector)
        let startFunc = unsafeBitCast(startImp, to: StartXGFunc.self)
        startFunc(managerObj,
                  startSelector,
                  TPNSCredentials.accessID,
                  TPNSCredentials.accessKey as NSString,
                  self)
        TPNSCredentials.setRuntimeReady(true)
#if DEBUG
        print("[TPNS][CONFIG][START] accessID=\(TPNSCredentials.accessID) accessKeyTail4=\(TPNSCredentials.accessKeyTail4()) accessKeyLen=\(TPNSCredentials.accessKey.count) accessKeyConfigured=\(!TPNSCredentials.accessKey.isEmpty) secretKeyConfigured=\(!TPNSCredentials.secretKey.isEmpty)")
#endif
    }

    @objc(xgPushDidRegisteredDeviceToken:xgToken:error:)
    func xgPushDidRegisteredDeviceToken(_ deviceToken: String?, xgToken: String?, error: NSError?) {
        if let error {
#if DEBUG
            print("[TPNS][XG][REGISTER][FAIL] \(error.localizedDescription)")
#endif
            return
        }

        let token = PushDeviceReporter.normalizeToken(xgToken ?? deviceToken ?? "")
        guard !token.isEmpty else { return }
#if DEBUG
        print("[TPNS][XG][REGISTER][SUCCESS] token=\(PushDeviceReporter.debugTokenPreview(token))")
#endif
        PushDeviceReporter.saveTPNSToken(token, source: "tpns")
    }

    @objc(xgPushDidRegisterForRemoteNotificationsWithDeviceToken:)
    func xgPushDidRegisterForRemoteNotificationsWithDeviceToken(_ deviceToken: Data?) {
        let hex = (deviceToken ?? Data()).map { String(format: "%02X", $0) }.joined()
#if DEBUG
        if hex.isEmpty {
            print("[TPNS][XG][APNS][TOKEN] empty")
        } else {
            print("[TPNS][XG][APNS][TOKEN] token=\(PushDeviceReporter.debugTokenPreview(hex)) len=\(hex.count)")
        }
#endif
    }

    func application(_ application: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
#if DEBUG
        print("[TPNS][REGISTER][FAIL] \(error.localizedDescription)")
        if error.localizedDescription.contains("aps-environment") {
            print("[TPNS][REGISTER][HINT] Missing APNs entitlement. Check Tianruiapp.entitlements and Signing & Capabilities -> Push Notifications.")
        }
#endif
    }
}
