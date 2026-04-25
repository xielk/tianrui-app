import Foundation
import CoreBluetooth
import CommonCrypto

enum BleAdvertisementMatcher {
    private static let defaultExpectedCompanyIdLE: [UInt8] = [0xF2, 0xD8]

    static func targetIdentifier(from connectValue: String) -> String? {
        let hex = connectValue.uppercased().filter { $0.isHexDigit }
        if hex.count == 8 {
            return hex
        }
        if hex.count == 12 {
            if hex.hasPrefix("F2D8") || hex.hasPrefix("D8F2") {
                return String(hex.suffix(8))
            }
            return hex
        }
        return nil
    }

    static func targetCompanyHex(from connectValue: String) -> String? {
        let hex = connectValue.uppercased().filter { $0.isHexDigit }
        if hex.count == 12, hex.hasPrefix("F2D8") {
            return "D8F2"
        }
        if hex.count == 12, hex.hasPrefix("D8F2") {
            return "F2D8"
        }
        return nil
    }

    static func expectedCompanyIdLE(from connectValue: String) -> [UInt8]? {
        let hex = connectValue.uppercased().filter { $0.isHexDigit }
        guard hex.count == 12 else { return nil }
        guard let b0 = UInt8(hex.prefix(2), radix: 16),
              let b1 = UInt8(hex.dropFirst(2).prefix(2), radix: 16) else {
            return nil
        }
        return [b0, b1]
    }

    static func companyMatchesMacSegment(advertisementData: [String: Any], expectedCompanyIdLE: [UInt8]?) -> Bool {
        guard let expectedCompanyIdLE, expectedCompanyIdLE.count == 2 else { return false }
        guard let manufacturer = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data else {
            return false
        }
        let bytes = [UInt8](manufacturer)
        guard bytes.count >= 2 else { return false }

        let directMatch = bytes[0] == expectedCompanyIdLE[0] && bytes[1] == expectedCompanyIdLE[1]
        let reverseMatch = bytes[0] == expectedCompanyIdLE[1] && bytes[1] == expectedCompanyIdLE[0]
        return directMatch || reverseMatch
    }

    static func extractDeviceIdentifier(advertisementData: [String: Any], expectedCompanyIdLE: [UInt8]? = nil) -> String? {
        guard let manufacturer = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data else {
            return nil
        }

        let bytes = [UInt8](manufacturer)
        guard bytes.count >= 6 else { return nil }
        let expected = expectedCompanyIdLE ?? defaultExpectedCompanyIdLE
        guard bytes[0] == expected[0], bytes[1] == expected[1] else { return nil }
        let identifierBytes = bytes[2..<6]
        return identifierBytes.map { String(format: "%02X", $0) }.joined()
    }

    static func shouldConnect(advertisementData: [String: Any], targetIdentifier: String, expectedCompanyIdLE: [UInt8]? = nil) -> Bool {
        extractDeviceIdentifier(advertisementData: advertisementData, expectedCompanyIdLE: expectedCompanyIdLE) == targetIdentifier
    }

    static func standardMacSuffix(from connectValue: String) -> String? {
        let hex = connectValue.uppercased().filter { $0.isHexDigit }
        guard hex.count == 12 else { return nil }
        return String(hex.suffix(4))
    }

    static func standardMacIdentifier(from connectValue: String) -> String? {
        let hex = connectValue.uppercased().filter { $0.isHexDigit }
        guard hex.count == 12 else { return nil }
        return String(hex.suffix(8))
    }

    static func shouldConnectUsingStandardMacSuffix(advertisementData: [String: Any], targetMacSuffix: String, targetIdentifier: String? = nil, expectedCompanyIdLE: [UInt8]? = nil) -> Bool {
        guard let manufacturer = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data else {
            return false
        }
        let bytes = [UInt8](manufacturer)
        guard bytes.count >= 6 else { return false }

        if let expectedCompanyIdLE, expectedCompanyIdLE.count == 2 {
            guard bytes[0] == expectedCompanyIdLE[0], bytes[1] == expectedCompanyIdLE[1] else {
                return false
            }
        }

        let identifier = bytes[2..<6].map { String(format: "%02X", $0) }.joined()
        if let targetIdentifier {
            return identifier == targetIdentifier.uppercased()
        }
        guard !targetMacSuffix.isEmpty else { return false }
        return identifier.hasSuffix(targetMacSuffix.uppercased())
    }
}

@MainActor
protocol BleManaging: AnyObject {
    var connectionState: BleConnectionState { get }
    var activeMac: String { get }
    var isSystemConnected: Bool { get }
    var currentDeviceToken: String { get }
    var onConnectionStateChanged: ((BleConnectionState) -> Void)? { get set }
    var onParsedMessage: ((BleParsedMessage) -> Void)? { get set }

    func startConnect(macAddress: String)
    func disconnect()
    func sendControlHex(_ hex: String)
    func ensureDeviceToken() async -> AppResult<Void>
    func removeCurrentPairingRecord() -> Bool
}

@MainActor
final class IOSBleManager: NSObject, BleManaging {
    private static let logDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss.SSS"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()

    private enum LogTone {
        case info
        case success
        case warning
    }

    private func log(_ message: String, tone: LogTone = .info) {
        let timestamp = Self.logDateFormatter.string(from: Date())
        let level: String
        switch tone {
        case .info:
            level = "SCAN"
        case .success:
            level = "OK"
        case .warning:
            level = "WARN"
        }
        print("[BLE][iOS][\(timestamp)][\(level)] \(message)")
    }

    private(set) var connectionState: BleConnectionState = .idle {
        didSet {
            onConnectionStateChanged?(connectionState)
        }
    }
    private(set) var activeMac: String = ""
    var isSystemConnected: Bool { connectedPeripheral != nil }
    var currentDeviceToken: String { deviceToken }
    private(set) var lastSentHex: String?
    private(set) var lastReceivedHex: String?
    private(set) var lastParsedMessage: BleParsedMessage?
    var onConnectionStateChanged: ((BleConnectionState) -> Void)?
    var onParsedMessage: ((BleParsedMessage) -> Void)?

    private var centralManager: CBCentralManager?
    private var connectedPeripheral: CBPeripheral?
    private var writeCharacteristic: CBCharacteristic?
    private var notifyCharacteristic: CBCharacteristic?
    private var pendingWriteData: Data?
    private var targetNameHint: String = ""
    private var targetAddressHint: String = ""
    private var targetDeviceIdentifier: String = "24188002"
    private var shouldMatchByManufacturerIdentifier: Bool = true
    private var targetMacSuffix: String?
    private var targetMacIdentifier: String?
    private var targetCompanyIdLE: [UInt8]?
    private var lastLoggedNotifyPlainHex: String?
    private var seenNotifyPlainHex: Set<String> = []
    private var calibrationObserveUntil: TimeInterval = 0
    private var calibrationObservedPayloads: Set<String> = []
    private let peripheralMapStoreKey = "ble_peripheral_uuid_by_mac"

    private let codec = BlePacketCodec()
    private let encryptKeyHex = "AA9B8F3C8A60DA6C8E583F5C6248954A"
    private let tokenRequestPlainHex = "C35AA5C300112233445566778899AABB"
    private let serviceUUID = CBUUID(string: "00001000-0000-1000-8000-00805F9B34FB")
    private let writeUUID = CBUUID(string: "00001001-0000-1000-8000-00805F9B34FB")
    private let notifyUUID = CBUUID(string: "00001002-0000-1000-8000-00805F9B34FB")
    private var deviceToken: String = "1234"
    private var tokenWaitContinuation: CheckedContinuation<String, Never>?
    private var tokenTimeoutTask: Task<Void, Never>?

    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }

    func startConnect(macAddress: String) {
        log("startConnect requested mac=\(macAddress)")
        resetDeviceTokenState()
        activeMac = macAddress
        targetNameHint = normalizedNameHint(from: macAddress)
        targetAddressHint = ""
        targetMacSuffix = nil
        targetMacIdentifier = nil
        targetCompanyIdLE = nil
        seenNotifyPlainHex.removeAll()
        let normalizedHex = macAddress.uppercased().filter { $0.isHexDigit }
        targetCompanyIdLE = BleAdvertisementMatcher.expectedCompanyIdLE(from: macAddress)
        if let parsedIdentifier = BleAdvertisementMatcher.targetIdentifier(from: macAddress) {
            targetDeviceIdentifier = parsedIdentifier
            let companyHex = BleAdvertisementMatcher.targetCompanyHex(from: macAddress)
            if companyHex != nil || normalizedHex.count == 8 {
                shouldMatchByManufacturerIdentifier = true
            } else if let macSuffix = BleAdvertisementMatcher.standardMacSuffix(from: macAddress) {
                shouldMatchByManufacturerIdentifier = false
                targetMacSuffix = macSuffix
                targetMacIdentifier = BleAdvertisementMatcher.standardMacIdentifier(from: macAddress)
            } else {
                shouldMatchByManufacturerIdentifier = false
            }
            if let companyHex {
                log("startConnect parsed companyId=\(companyHex) identifier=\(targetDeviceIdentifier)")
            } else if shouldMatchByManufacturerIdentifier {
                log("startConnect parsed identifier=\(targetDeviceIdentifier)")
            } else if let targetMacSuffix {
                log("startConnect standard MAC fallback mac=\(macAddress) suffix=\(targetMacSuffix)")
            } else {
                log("startConnect using standard MAC fallback mac=\(macAddress)")
            }
        } else {
            connectionState = .failed("INVALID_MAC")
            log("startConnect invalid MAC format, expected standard XX:XX:XX:XX:XX:XX or F2:D8:XX:XX:XX:XX or D8:F2:XX:XX:XX:XX or 8-hex identifier; input=\(macAddress)", tone: .warning)
            return
        }
        guard let manager = centralManager else {
            connectionState = .failed("CENTRAL_UNAVAILABLE")
            log("startConnect failed: central unavailable", tone: .warning)
            return
        }

        switch manager.state {
        case .poweredOn:
            startScan()
        case .poweredOff:
            connectionState = .failed("BLE_POWERED_OFF")
            log("startConnect failed: bluetooth powered off", tone: .warning)
        case .unauthorized:
            connectionState = .failed("BLE_UNAUTHORIZED")
            log("startConnect failed: bluetooth unauthorized", tone: .warning)
        case .unsupported:
            connectionState = .failed("BLE_UNSUPPORTED")
            log("startConnect failed: bluetooth unsupported", tone: .warning)
        default:
            connectionState = .connecting
            log("startConnect waiting for central state=\(manager.state.rawValue)")
        }
    }

    func disconnect() {
        log("disconnect requested", tone: .warning)
        resetDeviceTokenState()
        pendingWriteData = nil
        if let peripheral = connectedPeripheral {
            centralManager?.cancelPeripheralConnection(peripheral)
        }
        connectedPeripheral = nil
        writeCharacteristic = nil
        notifyCharacteristic = nil
        targetMacIdentifier = nil
        targetCompanyIdLE = nil
        lastLoggedNotifyPlainHex = nil
        seenNotifyPlainHex.removeAll()
        connectionState = .disconnected
    }

    func sendControlHex(_ hex: String) {
        let normalizedHex = hex.uppercased()
        lastSentHex = normalizedHex
        if normalizedHex.hasPrefix("C303C31A") {
            calibrationObserveUntil = Date().timeIntervalSince1970 + 2.5
            calibrationObservedPayloads.removeAll()
            log("calibration command dispatched plain=\(normalizedHex)", tone: .info)
        }
        log("sendControlHex plain=\(normalizedHex)")
        log("sendControlHex plainLen=\(normalizedHex.count) token=\(deviceToken)")

        guard let encryptedHex = encryptHex(normalizedHex), let payload = Data(hexEncoded: encryptedHex) else {
            connectionState = .failed("INVALID_HEX")
            log("sendControlHex failed: encrypt invalid", tone: .warning)
            return
        }
        log("sendControlHex encrypted=\(encryptedHex)")
        log("sendControlHex encryptedLen=\(encryptedHex.count)")

        guard
            let peripheral = connectedPeripheral,
            let characteristic = writeCharacteristic
        else {
            pendingWriteData = payload
            log("sendControlHex queued pending payload bytes=\(payload.count)")
            return
        }

        let writeType: CBCharacteristicWriteType = characteristic.properties.contains(.writeWithoutResponse)
            ? .withoutResponse
            : .withResponse
        peripheral.writeValue(payload, for: characteristic, type: writeType)
    }

    func ensureDeviceToken() async -> AppResult<Void> {
        if deviceToken.count == 4, deviceToken != "1234" {
            return .success(())
        }

        guard
            let encryptedHex = encryptHex(tokenRequestPlainHex),
            let payload = Data(hexEncoded: encryptedHex),
            let peripheral = connectedPeripheral,
            let characteristic = writeCharacteristic
        else {
            return .error(BleError(code: "BLE_NOT_READY", message: "蓝牙通道未就绪"))
        }

        if let pending = tokenWaitContinuation {
            pending.resume(returning: "")
            tokenWaitContinuation = nil
        }
        tokenTimeoutTask?.cancel()

        let writeType: CBCharacteristicWriteType = characteristic.properties.contains(.writeWithoutResponse)
            ? .withoutResponse
            : .withResponse
        peripheral.writeValue(payload, for: characteristic, type: writeType)
        log("token request sent", tone: .info)

        let token = await withCheckedContinuation { continuation in
            tokenWaitContinuation = continuation
            tokenTimeoutTask = Task { [weak self] in
                try? await Task.sleep(nanoseconds: 1_500_000_000)
                guard let self else { return }
                guard let pending = self.tokenWaitContinuation else { return }
                self.tokenWaitContinuation = nil
                pending.resume(returning: "")
            }
        }

        tokenTimeoutTask?.cancel()
        tokenTimeoutTask = nil

        if token.count == 4, token != "1234" {
            return .success(())
        }
        return .error(BleError(code: "BLE_TOKEN_TIMEOUT", message: "获取蓝牙Token超时"))
    }

    func removeCurrentPairingRecord() -> Bool {
        let macKey = activeMac.uppercased()
        guard !macKey.isEmpty else { return false }
        var dict = UserDefaults.standard.dictionary(forKey: peripheralMapStoreKey) as? [String: String] ?? [:]
        let removed = dict.removeValue(forKey: macKey) != nil
        UserDefaults.standard.set(dict, forKey: peripheralMapStoreKey)
        log("remove pairing record mac=\(macKey) removed=\(removed)", tone: .warning)
        return removed
    }

    private func resetDeviceTokenState() {
        deviceToken = "1234"
        tokenTimeoutTask?.cancel()
        tokenTimeoutTask = nil
        if let pending = tokenWaitContinuation {
            tokenWaitContinuation = nil
            pending.resume(returning: "")
        }
    }

    private func startScan() {
        guard let manager = centralManager else {
            connectionState = .failed("CENTRAL_UNAVAILABLE")
            return
        }

        if tryConnectKnownPeripheralIfPossible(manager: manager) {
            return
        }

        if manager.isScanning {
            log("scan already running")
            return
        }
        connectionState = .scanning
        log("scan started for service peripherals; matching manufacturer data")
        manager.scanForPeripherals(withServices: [serviceUUID], options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
    }

    private func tryConnectKnownPeripheralIfPossible(manager: CBCentralManager) -> Bool {
        if !shouldMatchByManufacturerIdentifier {
            if let targetMacSuffix {
                log("skip known peripheral fast-path for standard MAC suffix=\(targetMacSuffix)")
            } else {
                log("skip known peripheral fast-path for standard MAC")
            }
            return false
        }

        let normalizedMac = activeMac.uppercased()

        if let uuidText = knownPeripheralUUID(for: normalizedMac), let uuid = UUID(uuidString: uuidText) {
            let peripherals = manager.retrievePeripherals(withIdentifiers: [uuid])
            if let peripheral = peripherals.first {
                log("using known peripheral id=\(uuidText)", tone: .success)
                connectionState = .connecting
                connectedPeripheral = peripheral
                peripheral.delegate = self
                manager.connect(peripheral, options: nil)
                return true
            }
        }

        let connected = manager.retrieveConnectedPeripherals(withServices: [serviceUUID])
        if connected.count == 1, let peripheral = connected.first {
            log("using system-connected peripheral id=\(peripheral.identifier.uuidString)", tone: .success)
            connectionState = .connecting
            connectedPeripheral = peripheral
            peripheral.delegate = self
            manager.connect(peripheral, options: nil)
            return true
        }

        return false
    }

    private func flushPendingWriteIfPossible() {
        guard
            let payload = pendingWriteData,
            let peripheral = connectedPeripheral,
            let characteristic = writeCharacteristic
        else {
            return
        }

        let writeType: CBCharacteristicWriteType = characteristic.properties.contains(.writeWithoutResponse)
            ? .withoutResponse
            : .withResponse
        peripheral.writeValue(payload, for: characteristic, type: writeType)
        pendingWriteData = nil
    }

    private func updateReadyStateIfPossible() {
        guard writeCharacteristic != nil else { return }
        let wasReady: Bool
        if case .ready = connectionState {
            wasReady = true
        } else {
            wasReady = false
        }
        connectionState = .ready
        if !wasReady {
            log("ble channel ready", tone: .success)
        }
        requestDeviceTokenIfPossible()
        flushPendingWriteIfPossible()
    }

    private func requestDeviceTokenIfPossible() {
        guard
            let encryptedHex = encryptHex(tokenRequestPlainHex),
            let payload = Data(hexEncoded: encryptedHex),
            let peripheral = connectedPeripheral,
            let characteristic = writeCharacteristic
        else {
            log("token request skipped: write unavailable", tone: .warning)
            return
        }

        let writeType: CBCharacteristicWriteType = characteristic.properties.contains(.writeWithoutResponse)
            ? .withoutResponse
            : .withResponse
        peripheral.writeValue(payload, for: characteristic, type: writeType)
        log("token request sent", tone: .info)
    }
}

@MainActor
extension IOSBleManager: @preconcurrency CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        log("central state updated=\(central.state.rawValue)")
        if central.state == .poweredOn, !activeMac.isEmpty,
           case .connecting = connectionState {
            startScan()
        }
    }

    func centralManager(_ central: CBCentralManager,
                        didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any],
                        rssi RSSI: NSNumber) {
        let advName = advertisementData[CBAdvertisementDataLocalNameKey] as? String
        let manufacturerData = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data
        let manufacturerHex = manufacturerData?.hexString.uppercased() ?? "<nil>"
        log("didDiscover peripheral name=\(peripheral.name ?? "<nil>") advName=\(advName ?? "<nil>") manu=\(manufacturerHex) id=\(peripheral.identifier.uuidString) rssi=\(RSSI.intValue)")

        let matchedByIdentifier = shouldMatchByManufacturerIdentifier
            ? BleAdvertisementMatcher.shouldConnect(
                advertisementData: advertisementData,
                targetIdentifier: targetDeviceIdentifier,
                expectedCompanyIdLE: targetCompanyIdLE
            )
            : false

        let matchedByStandardMacSuffix: Bool
        if shouldMatchByManufacturerIdentifier {
            matchedByStandardMacSuffix = false
        } else if let targetMacSuffix {
            matchedByStandardMacSuffix = BleAdvertisementMatcher.shouldConnectUsingStandardMacSuffix(
                advertisementData: advertisementData,
                targetMacSuffix: targetMacSuffix,
                targetIdentifier: targetMacIdentifier,
                expectedCompanyIdLE: targetCompanyIdLE
            )
        } else {
            matchedByStandardMacSuffix = false
        }

        if !matchedByIdentifier && !matchedByStandardMacSuffix {
            if let targetMacSuffix, !shouldMatchByManufacturerIdentifier {
                let extractedIdentifier = BleAdvertisementMatcher.extractDeviceIdentifier(advertisementData: advertisementData, expectedCompanyIdLE: targetCompanyIdLE) ?? "<nil>"
                log("didDiscover not matched standard MAC suffix=\(targetMacSuffix) extractedIdentifier=\(extractedIdentifier)")
            } else {
                log("didDiscover not matched target identifier=\(targetDeviceIdentifier)")
            }
            return
        }

        if let connected = connectedPeripheral, connected.identifier == peripheral.identifier {
            return
        }

        let identifier = BleAdvertisementMatcher.extractDeviceIdentifier(advertisementData: advertisementData, expectedCompanyIdLE: targetCompanyIdLE) ?? targetDeviceIdentifier
        log("didDiscover matched identifier=\(identifier), connecting with callback peripheral", tone: .success)

        central.stopScan()
        connectionState = .connecting
        connectedPeripheral = peripheral
        peripheral.delegate = self
        central.connect(peripheral, options: nil)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        log("didConnect peripheral id=\(peripheral.identifier.uuidString)", tone: .success)
        saveKnownPeripheralUUID(peripheral.identifier.uuidString, for: activeMac.uppercased())
        connectionState = .discovering
        connectedPeripheral = peripheral
        peripheral.delegate = self
        peripheral.discoverServices([serviceUUID])
    }

    func centralManager(_ central: CBCentralManager,
                        didFailToConnect peripheral: CBPeripheral,
                        error: Error?) {
        let reason = error?.localizedDescription ?? "BLE_CONNECT_FAILED"
        log("didFailToConnect reason=\(reason)", tone: .warning)
        connectionState = .failed(reason)
    }

    func centralManager(_ central: CBCentralManager,
                        didDisconnectPeripheral peripheral: CBPeripheral,
                        error: Error?) {
        resetDeviceTokenState()
        if let error {
            log("didDisconnect with error=\(error.localizedDescription)", tone: .warning)
        } else {
            log("didDisconnect peripheral id=\(peripheral.identifier.uuidString)", tone: .warning)
        }
        connectedPeripheral = nil
        writeCharacteristic = nil
        notifyCharacteristic = nil
        targetMacIdentifier = nil
        targetCompanyIdLE = nil
        lastLoggedNotifyPlainHex = nil
        if error != nil, !activeMac.isEmpty {
            connectionState = .reconnecting
            startScan()
            return
        }
        connectionState = .disconnected
    }
}

private extension IOSBleManager {
    func normalizedNameHint(from raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return "" }
        if trimmed == "BLE_AUTO" { return "" }
        if trimmed.contains(":") { return "" }
        return trimmed.replacingOccurrences(of: "▼", with: "")
    }

    func knownPeripheralUUID(for mac: String) -> String? {
        guard let dict = UserDefaults.standard.dictionary(forKey: peripheralMapStoreKey) as? [String: String] else {
            return nil
        }
        return dict[mac]
    }

    func saveKnownPeripheralUUID(_ uuid: String, for mac: String) {
        guard !mac.isEmpty, !uuid.isEmpty else { return }
        var dict = UserDefaults.standard.dictionary(forKey: peripheralMapStoreKey) as? [String: String] ?? [:]
        dict[mac] = uuid
        UserDefaults.standard.set(dict, forKey: peripheralMapStoreKey)
    }

}

@MainActor
extension IOSBleManager: @preconcurrency CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error {
            log("didDiscoverServices failed: \(error.localizedDescription)", tone: .warning)
            connectionState = .failed(error.localizedDescription)
            return
        }
        guard let services = peripheral.services else {
            connectionState = .failed("NO_SERVICES")
            return
        }

        for service in services {
            guard service.uuid == serviceUUID else { continue }
            log("didDiscoverServices matched service=\(service.uuid.uuidString)")
            peripheral.discoverCharacteristics([writeUUID, notifyUUID], for: service)
        }
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didDiscoverCharacteristicsFor service: CBService,
                    error: Error?) {
        if let error {
            log("didDiscoverCharacteristics failed: \(error.localizedDescription)", tone: .warning)
            connectionState = .failed(error.localizedDescription)
            return
        }
        guard let characteristics = service.characteristics else { return }

        for characteristic in characteristics {
            if characteristic.uuid == writeUUID {
                writeCharacteristic = characteristic
                log("write characteristic discovered")
            } else if writeCharacteristic == nil,
                      characteristic.properties.contains(.write) || characteristic.properties.contains(.writeWithoutResponse) {
                writeCharacteristic = characteristic
                log("fallback write characteristic discovered")
            }

            if characteristic.uuid == notifyUUID {
                notifyCharacteristic = characteristic
                log("notify characteristic discovered")
                peripheral.setNotifyValue(true, for: characteristic)
            } else if notifyCharacteristic == nil,
                      characteristic.properties.contains(.notify) || characteristic.properties.contains(.indicate) {
                notifyCharacteristic = characteristic
                log("fallback notify characteristic discovered")
                peripheral.setNotifyValue(true, for: characteristic)
            }
        }

        updateReadyStateIfPossible()
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didUpdateNotificationStateFor characteristic: CBCharacteristic,
                    error: Error?) {
        if let error {
            log("didUpdateNotificationState failed: \(error.localizedDescription)", tone: .warning)
            connectionState = .failed(error.localizedDescription)
            return
        }
        log("notify enabled for characteristic=\(characteristic.uuid.uuidString)")
        updateReadyStateIfPossible()
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didUpdateValueFor characteristic: CBCharacteristic,
                    error: Error?) {
        if let error {
            log("didUpdateValue failed: \(error.localizedDescription)", tone: .warning)
            connectionState = .failed(error.localizedDescription)
            return
        }
        guard let data = characteristic.value, !data.isEmpty else { return }

        let hex = data.hexString.uppercased()
        guard let plainHex = decryptHex(hex) else {
            log("notify decrypt failed", tone: .warning)
            return
        }

        lastReceivedHex = plainHex
        let now = Date().timeIntervalSince1970
        if now < calibrationObserveUntil,
           !calibrationObservedPayloads.contains(plainHex) {
            calibrationObservedPayloads.insert(plainHex)
            log("calibration observe plain=\(plainHex)", tone: .success)
        }

        if plainHex.count >= 10,
           let kindRange = plainHex.rangeAt(start: 4, length: 2),
           plainHex[kindRange] == "A5",
           let tokenRange = plainHex.rangeAt(start: 6, length: 4) {
            let token = String(plainHex[tokenRange])
            if token.allSatisfy({ $0.isHexDigit }) {
                deviceToken = token
                log("token acquired token=\(token)", tone: .success)
                tokenTimeoutTask?.cancel()
                tokenTimeoutTask = nil
                if let waiter = tokenWaitContinuation {
                    tokenWaitContinuation = nil
                    waiter.resume(returning: token)
                }
            }
            return
        }

        if shouldSkipNotifyLog(plainHex) {
            return
        }
        lastLoggedNotifyPlainHex = plainHex
        log("notify plain=\(plainHex)")

        lastParsedMessage = codec.parseNotifyPlainHex(plainHex)
        if let parsed = lastParsedMessage {
            onParsedMessage?(parsed)
        }
    }
}

private extension Data {
    init?(hexEncoded hex: String) {
        let normalized = hex.replacingOccurrences(of: " ", with: "")
        guard normalized.count.isMultiple(of: 2) else { return nil }

        var output = Data(capacity: normalized.count / 2)
        var index = normalized.startIndex

        while index < normalized.endIndex {
            let next = normalized.index(index, offsetBy: 2)
            let byteString = normalized[index..<next]
            guard let byte = UInt8(byteString, radix: 16) else { return nil }
            output.append(byte)
            index = next
        }

        self = output
    }

    var hexString: String {
        map { String(format: "%02X", $0) }.joined()
    }
}

private extension IOSBleManager {
    func shouldSkipNotifyLog(_ plainHex: String) -> Bool {
        if seenNotifyPlainHex.contains(plainHex) {
            return true
        }

        seenNotifyPlainHex.insert(plainHex)
        return false
    }

    func encryptHex(_ plainHex: String) -> String? {
        guard let plain = Data(hexEncoded: plainHex), let key = Data(hexEncoded: encryptKeyHex) else {
            return nil
        }
        guard plain.count.isMultiple(of: kCCBlockSizeAES128) else { return nil }
        guard key.count == kCCKeySizeAES128 else { return nil }

        var output = Data(count: plain.count + kCCBlockSizeAES128)
        let outputCapacity = output.count
        var outLength: size_t = 0
        let status = output.withUnsafeMutableBytes { outBuf in
            plain.withUnsafeBytes { inBuf in
                key.withUnsafeBytes { keyBuf in
                    CCCrypt(
                        CCOperation(kCCEncrypt),
                        CCAlgorithm(kCCAlgorithmAES),
                        CCOptions(kCCOptionECBMode),
                        keyBuf.baseAddress,
                        key.count,
                        nil,
                        inBuf.baseAddress,
                        plain.count,
                        outBuf.baseAddress,
                        outputCapacity,
                        &outLength
                    )
                }
            }
        }

        guard status == kCCSuccess else { return nil }
        output.count = outLength
        return output.hexString.uppercased()
    }

    func decryptHex(_ cipherHex: String) -> String? {
        guard let cipher = Data(hexEncoded: cipherHex), let key = Data(hexEncoded: encryptKeyHex) else {
            return nil
        }
        guard cipher.count.isMultiple(of: kCCBlockSizeAES128) else { return nil }
        guard key.count == kCCKeySizeAES128 else { return nil }

        var output = Data(count: cipher.count + kCCBlockSizeAES128)
        let outputCapacity = output.count
        var outLength: size_t = 0
        let status = output.withUnsafeMutableBytes { outBuf in
            cipher.withUnsafeBytes { inBuf in
                key.withUnsafeBytes { keyBuf in
                    CCCrypt(
                        CCOperation(kCCDecrypt),
                        CCAlgorithm(kCCAlgorithmAES),
                        CCOptions(kCCOptionECBMode),
                        keyBuf.baseAddress,
                        key.count,
                        nil,
                        inBuf.baseAddress,
                        cipher.count,
                        outBuf.baseAddress,
                        outputCapacity,
                        &outLength
                    )
                }
            }
        }

        guard status == kCCSuccess else { return nil }
        output.count = outLength
        return output.hexString.uppercased()
    }
}

private extension String {
    func rangeAt(start: Int, length: Int) -> Range<String.Index>? {
        guard start >= 0, length > 0, count >= start + length else { return nil }
        let lower = index(startIndex, offsetBy: start)
        let upper = index(lower, offsetBy: length)
        return lower..<upper
    }
}
