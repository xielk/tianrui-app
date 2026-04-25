import Foundation

final class BlePacketCodec {
    func buildControlHex(command: BleControlCommand, token: String) -> String {
        let op: String
        let value: String

        switch command {
        case .toggleLock(let locked):
            op = "03"
            value = locked ? "00" : "01"
        case .toggleMute(let mute):
            op = "0F"
            value = mute ? "00" : "0A"
        case .toggleAutoSense(let enabled):
            op = "0A"
            value = enabled ? "01" : "00"
        case .findBike:
            op = "02"
            value = "01"
        case .sensorLevelLocation:
            op = "1A"
            value = "00"
        case .setDisarmSensitivity(let level):
            op = "0B"
            value = String(format: "%02X", max(0, min(8, level)))
        case .setArmSensitivity(let level):
            op = "19"
            value = String(format: "%02X", max(0, min(8, level)))
        case .setAlarmSensitivity(let index):
            op = "05"
            value = String(format: "%02X", max(1, min(3, index + 1)))
        case .setAutoShutdown(let minutes):
            op = "09"
            switch minutes {
            case 3:
                value = "03"
            case 5:
                value = "05"
            case 10:
                value = "0A"
            default:
                value = "00"
            }
        }

        let normalizedToken = token.uppercased()
        let tokenPart: String
        if normalizedToken.count == 4, normalizedToken.allSatisfy({ $0.isHexDigit }) {
            tokenPart = normalizedToken
        } else {
            tokenPart = "1234"
        }

        return "C303C3\(op)\(value)112233445566778899\(tokenPart)"
    }

    func parseNotifyPlainHex(_ plainHex: String) -> BleParsedMessage? {
        let text = plainHex.uppercased()
        guard text.count >= 6 else { return nil }
        let idx1 = text.index(text.startIndex, offsetBy: 4)
        let idx2 = text.index(text.startIndex, offsetBy: 6)
        let kind = String(text[idx1..<idx2])
        return BleParsedMessage(kind: kind, plainHex: text)
    }
}
