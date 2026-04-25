import Foundation

@MainActor
final class BleRepositoryImpl: BleRepository {
    private(set) var latestConnectionState: BleConnectionState = .idle
    private(set) var latestRealtimeState: BleRealtimeState = .init()
    private(set) var latestSystemConnected: Bool = false
    private(set) var isOtaExecutionActive: Bool = false

    private let manager: BleManaging
    private let codec: BlePacketCodec

    init(manager: BleManaging, codec: BlePacketCodec = BlePacketCodec()) {
        self.manager = manager
        self.codec = codec

        manager.onConnectionStateChanged = { [weak self] state in
            self?.latestConnectionState = state
            self?.latestSystemConnected = self?.manager.isSystemConnected ?? false
        }

        manager.onParsedMessage = { [weak self] message in
            self?.apply(parsedMessage: message)
        }
    }

    func ensureConnectedInBackground() async -> AppResult<Void> {
        if manager.activeMac.isEmpty {
            return .error(BleError(code: "NO_MAC", message: "未配置设备 MAC 地址"))
        }
        if case .ready = manager.connectionState {
            return .success(())
        }

        let maxAttempts = 6
        for _ in 0..<maxAttempts {
            manager.startConnect(macAddress: manager.activeMac)
            latestSystemConnected = manager.isSystemConnected

            let waitUntil = Date().addingTimeInterval(1.5)
            while Date() < waitUntil {
                switch manager.connectionState {
                case .ready:
                    return .success(())
                case .failed, .disconnected:
                    break
                default:
                    try? await Task.sleep(nanoseconds: 120_000_000)
                }

                if case .failed = manager.connectionState {
                    break
                }
                if case .disconnected = manager.connectionState {
                    break
                }
            }
        }

        if case .ready = manager.connectionState {
            return .success(())
        }

        return .error(BleError(code: "BLE_RETRY_EXHAUSTED", message: "蓝牙连接失败，请重试"))
    }

    func connectTo(macAddress: String) async -> AppResult<Void> {
        manager.startConnect(macAddress: macAddress)
        latestSystemConnected = manager.isSystemConnected
        return .success(())
    }

    func disconnect() async -> AppResult<Void> {
        manager.disconnect()
        latestSystemConnected = manager.isSystemConnected
        return .success(())
    }

    func removeCurrentPairingRecord() async -> AppResult<Bool> {
        let removed = manager.removeCurrentPairingRecord()
        return .success(removed)
    }

    func setOtaExecutionActive(_ active: Bool) {
        isOtaExecutionActive = active
    }

    func sendCommand(_ command: BleControlCommand, token: String) async -> AppResult<Void> {
        if isOtaExecutionActive {
            return .error(BleError(code: "BLE_OTA_BUSY", message: "OTA升级中，请稍候"))
        }
        guard case .ready = manager.connectionState else {
            return .error(BleError(code: "BLE_NOT_READY", message: "蓝牙通道未就绪"))
        }
        let ensured = await manager.ensureDeviceToken()
        if case .error(let err) = ensured {
            return .error(err)
        }
        let tokenToUse = manager.currentDeviceToken
        let commandHex = codec.buildControlHex(command: command, token: tokenToUse)
        manager.sendControlHex(commandHex)
        return .success(())
    }

    private func apply(parsedMessage: BleParsedMessage) {
        latestRealtimeState.lastPlainHex = parsedMessage.plainHex

        switch parsedMessage.kind {
        case "C4":
            let hex = parsedMessage.plainHex
            guard hex.count >= 10 else { break }
            let payload = String(hex.dropFirst(6))
            if payload.hasPrefix("02") {
                let stateCode = String(payload.dropFirst(2).prefix(2))
                latestRealtimeState.isLocked = stateCode == "00"
            }
        case "C6":
            let payload = String(parsedMessage.plainHex.dropFirst(6))
            var index = payload.startIndex
            while payload.distance(from: index, to: payload.endIndex) >= 4 {
                let next = payload.index(index, offsetBy: 4)
                let pair = String(payload[index..<next])
                let param = String(pair.prefix(2))
                let value = String(pair.suffix(2))
                switch (param, value) {
                case ("03", "00"):
                    latestRealtimeState.isLocked = true
                case ("03", "01"):
                    latestRealtimeState.isLocked = false
                case ("0F", "00"):
                    latestRealtimeState.isMuteEnabled = true
                case ("0F", "0A"):
                    latestRealtimeState.isMuteEnabled = false
                case ("0A", "01"):
                    latestRealtimeState.isAutoSenseEnabled = true
                case ("0A", "00"):
                    latestRealtimeState.isAutoSenseEnabled = false
                default:
                    break
                }
                index = next
            }
        default:
            break
        }
    }
}
