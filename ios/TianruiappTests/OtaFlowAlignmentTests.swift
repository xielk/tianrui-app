import XCTest
import CoreBluetooth
@testable import Tianruiapp

@MainActor
final class OtaFlowAlignmentTests: XCTestCase {
    func test_makeOtaViewModel_reusesSameInstanceDuringPresentation() {
        let viewModel = F1ViewModel(repository: OtaFlowTestRepository())

        let first = viewModel.makeOtaViewModel()
        let second = viewModel.makeOtaViewModel()

        XCTAssertTrue(first === second)
    }

    func test_payloadSizeForWriteLength_matchesAndroidRule() {
        XCTAssertEqual(Fr8010OtaRunner.payloadSize(forWriteLength: 244), 235)
        XCTAssertEqual(Fr8010OtaRunner.payloadSize(forWriteLength: 20), 20)
        XCTAssertEqual(Fr8010OtaRunner.payloadSize(forWriteLength: 512), 235)
    }

    func test_fr8010Codec_buildWriteDataPacket_matchesAndroidLayout() {
        let codec = Fr8010OtaCodec()
        let packet = codec.buildCommand(
            opcode: .writeData,
            address: 0x12345678,
            dataLength: 3,
            data: [0xAA, 0xBB, 0xCC]
        )
        XCTAssertEqual(packet.map { String(format: "%02X", $0) }.joined(), "050900785634120300AABBCC")
    }

    func test_otaWriteType_prefersNoResponseWhenSupported() {
        XCTAssertEqual(
            Fr8010OtaRunner.preferredWriteType(for: [.write, .writeWithoutResponse]),
            .withoutResponse
        )
        XCTAssertEqual(
            Fr8010OtaRunner.preferredWriteType(for: [.writeWithoutResponse]),
            .withoutResponse
        )
    }

    func test_payloadSizeForPreferredWriteLength_clampsToAndroidSizedChunks() {
        XCTAssertEqual(Fr8010OtaRunner.payloadSize(forWriteLength: 244), 235)
        XCTAssertEqual(Fr8010OtaRunner.payloadSize(forWriteLength: 512), 235)
    }
}

@MainActor
private final class OtaFlowTestRepository: BleRepository {
    var latestConnectionState: BleConnectionState = .ready
    var latestRealtimeState: BleRealtimeState = .init(isLocked: false, isMuteEnabled: false, isAutoSenseEnabled: false)
    var latestSystemConnected: Bool = true
    var isOtaExecutionActive: Bool = false

    func ensureConnectedInBackground() async -> AppResult<Void> { .success(()) }
    func connectTo(macAddress: String) async -> AppResult<Void> { .success(()) }
    func disconnect() async -> AppResult<Void> { .success(()) }
    func removeCurrentPairingRecord() async -> AppResult<Bool> { .success(true) }
    func sendCommand(_ command: BleControlCommand, token: String) async -> AppResult<Void> { .success(()) }
    func setOtaExecutionActive(_ active: Bool) { isOtaExecutionActive = active }
}
