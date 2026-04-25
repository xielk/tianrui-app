import Foundation

struct ChannelAvailability: Equatable {
    var bleAvailable: Bool
    var networkAvailable: Bool

    var hasAnyChannel: Bool {
        bleAvailable || networkAvailable
    }
}

enum BleConnectionState: Equatable {
    case idle
    case scanning
    case connecting
    case discovering
    case ready
    case reconnecting
    case disconnected
    case failed(String)
}

enum BleControlCommand: Equatable {
    case toggleLock(locked: Bool)
    case toggleMute(mute: Bool)
    case toggleAutoSense(enabled: Bool)
    case findBike
    case sensorLevelLocation
    case setDisarmSensitivity(level0To8: Int)
    case setArmSensitivity(level0To8: Int)
    case setAlarmSensitivity(levelIndex0To2: Int)
    case setAutoShutdown(minutes: Int)
}

struct BleRealtimeState: Equatable {
    var isLocked: Bool? = nil
    var isMuteEnabled: Bool? = nil
    var isAutoSenseEnabled: Bool? = nil
    var lastPlainHex: String = ""
}

struct BleParsedMessage: Equatable {
    var kind: String
    var plainHex: String
}

struct BleError: Error, Equatable {
    var code: String
    var message: String
}

enum AppResult<T> {
    case success(T)
    case error(BleError)

    var errorCode: String? {
        if case .error(let err) = self {
            return err.code
        }
        return nil
    }
}
