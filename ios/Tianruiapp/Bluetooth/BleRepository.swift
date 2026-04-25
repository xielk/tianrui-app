import Foundation

@MainActor
protocol BleRepository {
    var latestConnectionState: BleConnectionState { get }
    var latestRealtimeState: BleRealtimeState { get }
    var latestSystemConnected: Bool { get }
    var isOtaExecutionActive: Bool { get }

    func ensureConnectedInBackground() async -> AppResult<Void>
    func connectTo(macAddress: String) async -> AppResult<Void>
    func disconnect() async -> AppResult<Void>
    func removeCurrentPairingRecord() async -> AppResult<Bool>
    func sendCommand(_ command: BleControlCommand, token: String) async -> AppResult<Void>
    func setOtaExecutionActive(_ active: Bool)
}
