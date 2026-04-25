import XCTest
@testable import Tianruiapp

final class BlePacketCodecTests: XCTestCase {
    func test_toggleLock_encodesAndroidAlignedFrame() {
        let codec = BlePacketCodec()
        let hex = codec.buildControlHex(command: .toggleLock(locked: true), token: "1234")
        XCTAssertEqual(hex, "C303C303001122334455667788991234")
    }

    func test_parseC6Message_detectsKindAndKeepsHex() {
        let codec = BlePacketCodec()
        let parsed = codec.parseNotifyPlainHex("C30AC6AABB")
        XCTAssertEqual(parsed?.kind, "C6")
        XCTAssertEqual(parsed?.plainHex, "C30AC6AABB")
    }

    func test_sensorLevelLocation_encodesAndroidAlignedFrame() {
        let codec = BlePacketCodec()
        let hex = codec.buildControlHex(command: .sensorLevelLocation, token: "1234")
        XCTAssertEqual(hex, "C303C31A001122334455667788991234")
    }

    func test_fr8010Codec_buildGetBasePacket_matchesExpectedLayout() {
        let codec = Fr8010OtaCodec()
        let packet = codec.buildCommand(opcode: .getStartBase, address: 0, dataLength: 0, data: nil)
        XCTAssertEqual(packet.map { String(format: "%02X", $0) }.joined(), "010300000000000000")
    }

    func test_fr8010Codec_buildRebootPacket_containsLengthAndCrc() {
        let codec = Fr8010OtaCodec()
        let packet = codec.buildRebootCommand(fileLength: 0x12345678, crc: 0xA1B2C3D4)
        XCTAssertEqual(packet.map { String(format: "%02X", $0) }.joined(), "090A0078563412D4C3B2A1")
    }

    func test_fr8010Codec_payloadSize_followsAndroidRule() {
        XCTAssertEqual(Fr8010OtaRunner.payloadSize(forMtu: 247), 235)
        XCTAssertEqual(Fr8010OtaRunner.payloadSize(forMtu: 23), 20)
    }
}
