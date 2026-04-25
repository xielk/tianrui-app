import XCTest
import CoreBluetooth
@testable import Tianruiapp

@MainActor
private final class MockBleManager: BleManaging {
    var connectionState: BleConnectionState = .idle
    var activeMac: String = ""
    var isSystemConnected: Bool = false
    var currentDeviceToken: String = "1234"
    var onConnectionStateChanged: ((BleConnectionState) -> Void)?
    var onParsedMessage: ((BleParsedMessage) -> Void)?
    var startConnectCallCount = 0
    var lastSentHex: String?
    var ensureTokenCallCount = 0
    var ensureTokenResult: AppResult<Void> = .success(())
    var onStartConnect: (() -> Void)?

    func startConnect(macAddress: String) {
        activeMac = macAddress
        startConnectCallCount += 1
        onStartConnect?()
        onConnectionStateChanged?(connectionState)
    }

    func disconnect() {
        connectionState = .disconnected
        onConnectionStateChanged?(connectionState)
    }

    func sendControlHex(_ hex: String) {
        lastSentHex = hex
    }

    func ensureDeviceToken() async -> AppResult<Void> {
        ensureTokenCallCount += 1
        return ensureTokenResult
    }

    func removeCurrentPairingRecord() -> Bool {
        true
    }

    func emitConnectionState(_ state: BleConnectionState) {
        connectionState = state
        onConnectionStateChanged?(state)
    }

    func emitParsedMessage(_ message: BleParsedMessage) {
        onParsedMessage?(message)
    }
}

@MainActor
final class BleRepositoryTests: XCTestCase {
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

    func test_iosBleManager_disconnectSetsDisconnectedState() {
        let manager = IOSBleManager()
        manager.disconnect()
        XCTAssertEqual(manager.connectionState, .disconnected)
    }

    func test_repository_ensureConnectedFailsWithoutMac() async {
        let manager = IOSBleManager()
        let repo = BleRepositoryImpl(manager: manager)
        let result = await repo.ensureConnectedInBackground()
        XCTAssertEqual(result.errorCode, "NO_MAC")
    }

    func test_repository_returnsRetryExhaustedWhenStillNotReady() async {
        let manager = MockBleManager()
        manager.activeMac = "AA:BB:CC:DD:EE:FF"
        manager.connectionState = .connecting
        let repo = BleRepositoryImpl(manager: manager)

        let result = await repo.ensureConnectedInBackground()

        XCTAssertEqual(result.errorCode, "BLE_RETRY_EXHAUSTED")
        XCTAssertEqual(manager.startConnectCallCount, 6)
    }

    func test_repository_waitsForAsyncReadyStateAfterConnect() async {
        let manager = MockBleManager()
        manager.activeMac = "GC0018002"
        manager.connectionState = .connecting
        manager.onStartConnect = {
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 350_000_000)
                manager.emitConnectionState(.ready)
            }
        }
        let repo = BleRepositoryImpl(manager: manager)

        let result = await repo.ensureConnectedInBackground()

        if case .error(let err) = result {
            XCTFail("expected success but got error: \(err.code)")
        }
        XCTAssertEqual(repo.latestConnectionState, .ready)
    }

    func test_repository_sendCommandFailsWhenNotReady() async {
        let manager = MockBleManager()
        manager.connectionState = .connecting
        let repo = BleRepositoryImpl(manager: manager)

        let result = await repo.sendCommand(.findBike, token: "1234")

        XCTAssertEqual(result.errorCode, "BLE_NOT_READY")
        XCTAssertNil(manager.lastSentHex)
    }

    func test_repository_sendCommandWritesEncodedHexWhenReady() async {
        let manager = MockBleManager()
        manager.connectionState = .ready
        manager.currentDeviceToken = "ABCD"
        let repo = BleRepositoryImpl(manager: manager)

        let result = await repo.sendCommand(.findBike, token: "")

        if case .error(let err) = result {
            XCTFail("expected success but got error: \(err.code)")
        }
        XCTAssertEqual(manager.ensureTokenCallCount, 1)
        XCTAssertEqual(manager.lastSentHex, "C303C30201112233445566778899ABCD")
    }

    func test_repository_sendCommandBlockedDuringOtaExecution() async {
        let manager = MockBleManager()
        manager.connectionState = .ready
        let repo = BleRepositoryImpl(manager: manager)

        repo.setOtaExecutionActive(true)
        let result = await repo.sendCommand(.findBike, token: "")

        XCTAssertEqual(result.errorCode, "BLE_OTA_BUSY")
        XCTAssertNil(manager.lastSentHex)
    }

    func test_repository_sendCommandReturnsErrorWhenTokenAcquireFails() async {
        let manager = MockBleManager()
        manager.connectionState = .ready
        manager.ensureTokenResult = .error(BleError(code: "BLE_TOKEN_TIMEOUT", message: "获取蓝牙Token超时"))
        let repo = BleRepositoryImpl(manager: manager)

        let result = await repo.sendCommand(.sensorLevelLocation, token: "")

        XCTAssertEqual(result.errorCode, "BLE_TOKEN_TIMEOUT")
        XCTAssertNil(manager.lastSentHex)
    }

    func test_repository_updatesLatestConnectionStateFromManagerCallback() {
        let manager = MockBleManager()
        let repo = BleRepositoryImpl(manager: manager)

        manager.emitConnectionState(.ready)

        XCTAssertEqual(repo.latestConnectionState, .ready)
    }

    func test_repository_updatesRealtimeStateFromParsedC6Message() {
        let manager = MockBleManager()
        let repo = BleRepositoryImpl(manager: manager)
        let message = BleParsedMessage(kind: "C6", plainHex: "C35AC603000F000A01")

        manager.emitParsedMessage(message)

        XCTAssertEqual(repo.latestRealtimeState.isLocked, true)
        XCTAssertNotNil(repo.latestRealtimeState.isMuteEnabled)
        XCTAssertEqual(repo.latestRealtimeState.isAutoSenseEnabled, true)
        XCTAssertEqual(repo.latestRealtimeState.lastPlainHex, "C35AC603000F000A01")
    }

    func test_advMatcher_extractsIdentifierFromManufacturerDataWithCompanyId() {
        let advertisement: [String: Any] = [
            CBAdvertisementDataManufacturerDataKey: Data(hex: "F2D824188002B101020110A1"),
        ]

        let identifier = BleAdvertisementMatcher.extractDeviceIdentifier(advertisementData: advertisement)

        XCTAssertEqual(identifier, "24188002")
    }

    func test_advMatcher_acceptsCompanyIdAndExtractsIdentifier() {
        let advertisement: [String: Any] = [
            CBAdvertisementDataManufacturerDataKey: Data(hex: "F2D824188002B101020110A1"),
        ]

        let identifier = BleAdvertisementMatcher.extractDeviceIdentifier(advertisementData: advertisement)

        XCTAssertEqual(identifier, "24188002")
    }

    func test_advMatcher_rejectsWrongCompanyId() {
        let advertisement: [String: Any] = [
            CBAdvertisementDataManufacturerDataKey: Data(hex: "F1D824188002B101020110A1"),
        ]

        let identifier = BleAdvertisementMatcher.extractDeviceIdentifier(advertisementData: advertisement)

        XCTAssertNil(identifier)
    }

    func test_advMatcher_matchesTargetDeviceIdentifier() {
        let advertisement: [String: Any] = [
            CBAdvertisementDataManufacturerDataKey: Data(hex: "F2D824188002B101020110A1"),
        ]

        let shouldConnect = BleAdvertisementMatcher.shouldConnect(advertisementData: advertisement, targetIdentifier: "24188002")

        XCTAssertTrue(shouldConnect)
    }

    func test_advMatcher_rejectsNonTargetDeviceIdentifier() {
        let advertisement: [String: Any] = [
            CBAdvertisementDataManufacturerDataKey: Data(hex: "24188003B101020110A1"),
        ]

        let shouldConnect = BleAdvertisementMatcher.shouldConnect(advertisementData: advertisement, targetIdentifier: "24188002")

        XCTAssertFalse(shouldConnect)
    }

    func test_advMatcher_targetIdentifierParsesApiMacValue() {
        let parsed = BleAdvertisementMatcher.targetIdentifier(from: "F2:D8:24:18:80:C2")

        XCTAssertEqual(parsed, "241880C2")
    }

    func test_advMatcher_targetIdentifierParsesReversedApiMacValue() {
        let parsed = BleAdvertisementMatcher.targetIdentifier(from: "D8:F2:24:18:80:C2")

        XCTAssertEqual(parsed, "241880C2")
    }

    func test_advMatcher_acceptsRawEightHexIdentifier() {
        let parsed = BleAdvertisementMatcher.targetIdentifier(from: "241880C2")
        let parsedMac = BleAdvertisementMatcher.targetIdentifier(from: "E0:00:00:00:00:29")

        XCTAssertEqual(parsed, "241880C2")
        XCTAssertEqual(parsedMac, "E00000000029")
    }

    func test_advMatcher_standardMacSuffixParsesApiMacValue() {
        let suffix = BleAdvertisementMatcher.standardMacSuffix(from: "E0:00:00:00:00:29")

        XCTAssertEqual(suffix, "0029")
    }

    func test_advMatcher_standardMacSuffixMatchAcceptsMatchingIdentifier() {
        let advertisement: [String: Any] = [
            CBAdvertisementDataManufacturerDataKey: Data(hex: "F2D824180029B101020110A1"),
        ]

        let shouldConnect = BleAdvertisementMatcher.shouldConnectUsingStandardMacSuffix(
            advertisementData: advertisement,
            targetMacSuffix: "0029"
        )

        XCTAssertTrue(shouldConnect)
    }

    func test_advMatcher_standardMacSuffixMatchRejectsDifferentIdentifier() {
        let advertisement: [String: Any] = [
            CBAdvertisementDataManufacturerDataKey: Data(hex: "F2D824188002B101020110A1"),
        ]

        let shouldConnect = BleAdvertisementMatcher.shouldConnectUsingStandardMacSuffix(
            advertisementData: advertisement,
            targetMacSuffix: "0029"
        )

        XCTAssertFalse(shouldConnect)
    }

    func test_advMatcher_dynamicCompanyIdParsesFromStandardMac() {
        let company = BleAdvertisementMatcher.expectedCompanyIdLE(from: "F8:88:88:88:88:88")

        XCTAssertEqual(company ?? [], [0xF8, 0x88])
    }

    func test_advMatcher_standardMacSuffixMatchWithDynamicCompanyId() {
        let advertisement: [String: Any] = [
            CBAdvertisementDataManufacturerDataKey: Data(hex: "F888888888B101020110A1"),
        ]

        let shouldConnect = BleAdvertisementMatcher.shouldConnectUsingStandardMacSuffix(
            advertisementData: advertisement,
            targetMacSuffix: "8888",
            expectedCompanyIdLE: [0xF8, 0x88]
        )

        XCTAssertTrue(shouldConnect)
    }

    func test_advMatcher_companySegmentOnlyDoesNotPassWhenIdentifierMismatched() {
        let advertisement: [String: Any] = [
            CBAdvertisementDataManufacturerDataKey: Data(hex: "E00024188002B101020110A1"),
        ]

        let shouldConnect = BleAdvertisementMatcher.shouldConnectUsingStandardMacSuffix(
            advertisementData: advertisement,
            targetMacSuffix: "FFFF",
            targetIdentifier: "FFFFFFFF",
            expectedCompanyIdLE: [0xE0, 0x00]
        )

        XCTAssertFalse(shouldConnect)
    }

    func test_advMatcher_companyAndIdentifierBothMatchThenConnect() {
        let advertisement: [String: Any] = [
            CBAdvertisementDataManufacturerDataKey: Data(hex: "F888888888B101020110A1"),
        ]

        let shouldConnect = BleAdvertisementMatcher.shouldConnectUsingStandardMacSuffix(
            advertisementData: advertisement,
            targetMacSuffix: "8888",
            targetIdentifier: "88888888",
            expectedCompanyIdLE: [0xF8, 0x88]
        )

        XCTAssertTrue(shouldConnect)
    }
}

private extension Data {
    init(hex: String) {
        self.init()
        let cleaned = hex.replacingOccurrences(of: " ", with: "")
        var index = cleaned.startIndex
        while index < cleaned.endIndex {
            let next = cleaned.index(index, offsetBy: 2)
            let byte = cleaned[index..<next]
            self.append(UInt8(byte, radix: 16) ?? 0)
            index = next
        }
    }
}
