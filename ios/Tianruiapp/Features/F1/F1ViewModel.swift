import Foundation
import Combine
import OSLog
import CoreBluetooth

private let f1Logger = Logger(subsystem: "xiaochao.com", category: "BLE.F1ViewModel")

@MainActor
final class F1ViewModel: ObservableObject {
    enum LayoutMode {
        case f1
        case f2
        case m1b
    }

    struct VehicleOption: Identifiable, Equatable {
        let id: String
        let name: String
        let key: String

        init(name: String, key: String) {
            self.id = key
            self.name = name
            self.key = key
        }
    }

    @Published private(set) var connectionState: BleConnectionState = .idle
    @Published private(set) var bleSystemConnected: Bool = false
    @Published private(set) var isLocked: Bool = false
    @Published private(set) var isMuteEnabled: Bool = false
    @Published private(set) var isAutoSenseEnabled: Bool = false
    @Published private(set) var hasAnyChannel: Bool = false
    @Published private(set) var statusTip: String?
    @Published private(set) var vehicleOptions: [VehicleOption] = []
    @Published private(set) var selectedDeviceName: String = "--"
    @Published private(set) var selectedDeviceKey: String = ""
    @Published private(set) var currentLayoutMode: LayoutMode = .f1
    @Published private(set) var signalStrengthLevel: Int = 0
    @Published private(set) var onlineStatus: Int = 0
    @Published private(set) var metricsTimeText: String = ""
    @Published var isScannerPresented: Bool = false
    @Published var isSettingsPresented: Bool = false
    @Published var isAddVehiclePresented: Bool = false
    @Published private(set) var isAddingVehicle: Bool = false
    @Published var isShareUsersPresented: Bool = false
    @Published var isTrackMapPresented: Bool = false
    @Published var isProductDetailsPresented: Bool = false
    @Published var showAutoSenseDisableConfirm: Bool = false
    @Published var shouldOpenBluetoothSettings: Bool = false
    @Published var isBleCalibrationPresented: Bool = false
    @Published var isOtaPresented: Bool = false
    @Published private(set) var isCalibratingBle: Bool = false
    @Published private(set) var latitude: Double = 0
    @Published private(set) var longitude: Double = 0
    @Published private(set) var currentLocationAddress: String = "位置信息不可用"
    @Published private(set) var trackMapPoints: [DeviceTrackPoint] = []
    @Published private(set) var trackMapShouldShowNoDataHint: Bool = false
    @Published var disarmSensitivityLevel: Int = 0
    @Published var armSensitivityLevel: Int = 0
    @Published var alarmSensitivityIndex: Int = 2
    @Published var autoShutdownMinutes: Int = 0

    var autoSenseTitle: String {
        isAutoSenseEnabled ? "感应解锁开" : "感应解锁关"
    }

    var muteTitle: String {
        isMuteEnabled ? "静音设防" : "有声设防"
    }

    var bleIconName: String {
        switch connectionState {
        case .ready:
            return "ble_successful"
        case .scanning, .connecting, .discovering, .reconnecting:
            return "ble_connecting"
        default:
            return "ble_wait"
        }
    }

    var bleIconBlinking: Bool {
        switch connectionState {
        case .scanning, .connecting, .discovering, .reconnecting:
            return true
        default:
            return false
        }
    }

    var currentLocationURL: URL? {
        guard latitude != 0, longitude != 0 else { return nil }
        var components = URLComponents(string: "http://maps.apple.com/")
        components?.queryItems = [
            URLQueryItem(name: "ll", value: "\(latitude),\(longitude)"),
            URLQueryItem(name: "q", value: "车辆位置"),
        ]
        return components?.url
    }

    private let repository: BleRepository
    private var pollingTask: Task<Void, Never>?
    private var hasStarted = false
    private var connectAddress: String = ""
    private var cachedOtaViewModel: F1OtaViewModel?
    private let lastDeviceKeyStoreKey = "last_device_key"
    private let lastBluetoothMacStoreKey = "last_bluetooth_mac"
    private let deviceMacMapStoreKey = "bluetooth_mac_by_key"
    private let localBleStatePrefix = "f1_ble_state_"
    private let f1SettingsStorePrefix = "f1_settings_"
    private var tipDismissTask: Task<Void, Never>?

    init(repository: BleRepository) {
        self.repository = repository
    }

    deinit {
        pollingTask?.cancel()
        tipDismissTask?.cancel()
    }

    func start() {
        guard !hasStarted else { return }
        hasStarted = true
        f1Logger.info("F1 start requested")
        syncFromRepository()

        pollingTask = Task { [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                self.syncFromRepository()
                try? await Task.sleep(nanoseconds: 300_000_000)
            }
        }

        Task {
            let connected = await connectUsingResolvedMac()
            if case .error(let err) = connected {
                f1Logger.error("initial connect failed code=\(err.code) message=\(err.message)")
            } else {
                f1Logger.info("initial connect succeeded")
            }
            syncFromRepository()
        }
    }

    func onScanTap() {
        isAddVehiclePresented = true
    }

    func onScannerDismiss() {
        isScannerPresented = false
    }

    func onScanResult(_ value: String) {
        let code = value.trimmingCharacters(in: .whitespacesAndNewlines)
        isScannerPresented = false
        guard !code.isEmpty else {
            setStatusTip("未识别到有效二维码")
            return
        }

        if let matched = vehicleOptions.first(where: { $0.key.caseInsensitiveCompare(code) == .orderedSame }) {
            selectVehicle(matched)
            return
        }

        setStatusTip("扫码成功：\(code)")
    }

    func onBluetoothTap() {
        f1Logger.info("bluetooth icon tapped state=\(String(describing: self.connectionState))")
        switch connectionState {
        case .scanning, .connecting, .discovering, .reconnecting:
            setStatusTip("蓝牙连接中，请稍候")
        case .ready:
            Task {
                let _ = await repository.disconnect()
                setStatusTip("蓝牙已断开")
                syncFromRepository()
            }
        default:
            Task {
                let _ = await connectUsingResolvedMac()
                syncFromRepository()
            }
        }
    }

    func onBlePairedTap() {
        f1Logger.info("ble paired icon tapped")
        isBleCalibrationPresented = true
    }

    func onBleCalibrationDismiss() {
        isBleCalibrationPresented = false
    }

    func onOtaTap() {
        _ = makeOtaViewModel()
        isOtaPresented = true
    }

    func onOtaDismiss() {
        isOtaPresented = false
        cachedOtaViewModel = nil
    }

    func onDisableAutoSenseCancel() {
        showAutoSenseDisableConfirm = false
    }

    func onBluetoothSettingsOpened() {
        shouldOpenBluetoothSettings = false
    }

    func confirmDisableAutoSense() {
        showAutoSenseDisableConfirm = false
        Task {
            await disableAutoSenseAndCleanup()
            syncFromRepository()
        }
    }

    func calibrateBleDistance() async -> AppResult<Void> {
        guard !isCalibratingBle else {
            return .error(BleError(code: "BLE_CALIBRATING", message: "校准中，请稍候"))
        }

        isCalibratingBle = true
        defer { isCalibratingBle = false }

        if connectionState != .ready {
            let ensured = await repository.ensureConnectedInBackground()
            syncFromRepository()
            if case .error(let err) = ensured {
                setStatusTip(err.message)
                return .error(err)
            }
        }

        guard connectionState == .ready else {
            let err = BleError(code: "BLE_NOT_READY", message: "请先连接蓝牙")
            setStatusTip(err.message)
            return .error(err)
        }

        let result = await repository.sendCommand(.sensorLevelLocation, token: "")
        switch result {
        case .success:
            setStatusTip("校准指令已发送")
        case .error(let err):
            setStatusTip(err.message)
        }
        syncFromRepository()
        return result
    }

    func onAppBecameActive() {
        guard currentLayoutMode != .m1b else { return }
        switch connectionState {
        case .ready, .scanning, .connecting, .discovering, .reconnecting:
            return
        default:
            Task {
                let _ = await connectUsingResolvedMac()
                syncFromRepository()
            }
        }
    }

    func onSettingsTap() {
        isSettingsPresented = true
    }

    func onSettingsDismiss() {
        isSettingsPresented = false
    }

    func onShareTap() {
        guard !selectedDeviceKey.isEmpty else {
            setStatusTip("请先选择车辆")
            return
        }
        isShareUsersPresented = true
    }

    func onTrackTap() {
        guard !selectedDeviceKey.isEmpty else {
            setStatusTip("请先选择车辆")
            return
        }

        Task {
            do {
                let points = try await AuthAPIClient.shared.fetchDeviceTrackPoints(deviceKey: selectedDeviceKey, interval: 20)
                trackMapPoints = points
                trackMapShouldShowNoDataHint = F1TrackMapDisplayPolicy.shouldShowNoDataHint(points: points)
                isTrackMapPresented = true
            } catch {
                setStatusTip(error.localizedDescription)
            }
        }
    }

    func onTrackMapDismiss() {
        isTrackMapPresented = false
        trackMapShouldShowNoDataHint = false
    }

    func onProductDetailsTap() {
        guard !selectedDeviceKey.isEmpty else {
            setStatusTip("请先绑定设备")
            return
        }
        isProductDetailsPresented = true
    }

    func onProductDetailsDismiss() {
        isProductDetailsPresented = false
    }

    func showTip(_ message: String) {
        setStatusTip(message)
    }

    func onMapLocationUnavailable() {
        setStatusTip("位置信息不可用")
    }

    func onAddVehicleTap() {
        isAddVehiclePresented = true
    }

    func onAddVehicleDismiss() {
        isAddVehiclePresented = false
    }

    func onAddVehicleScanCancelled() {
        setStatusTip("已退出扫码，请手动输入设备号")
    }

    func makeOtaViewModel() -> F1OtaViewModel {
        if let cachedOtaViewModel {
            return cachedOtaViewModel
        }
        let modelType = currentLayoutMode == .f2 ? "F2" : "F1"
        let viewModel = F1OtaViewModel(
            repository: repository,
            runner: Fr8010OtaRunner(),
            macAddress: connectAddress,
            modelType: modelType
        )
        cachedOtaViewModel = viewModel
        return viewModel
    }

    func addVehicle(deviceKey rawValue: String) {
        let deviceKey = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !deviceKey.isEmpty else {
            setStatusTip("请输入设备号")
            return
        }
        guard !isAddingVehicle else { return }

        Task {
            isAddingVehicle = true
            defer { isAddingVehicle = false }
            f1Logger.info("add vehicle requested key=\(deviceKey)")
            setStatusTip("绑定中...")
            do {
                try await AuthAPIClient.shared.bindDevice(deviceKey: deviceKey)
                do {
                    try await AuthAPIClient.shared.setDefaultDevice(deviceKey: deviceKey)
                } catch {
                    f1Logger.error("set default device failed but continue bind flow: \(error.localizedDescription)")
                }

                UserDefaults.standard.set(deviceKey, forKey: lastDeviceKeyStoreKey)
                connectAddress = ""
                UserDefaults.standard.set("", forKey: lastBluetoothMacStoreKey)

                await refreshVehicleOptions()
                if let option = vehicleOptions.first(where: { $0.key.caseInsensitiveCompare(deviceKey) == .orderedSame }) {
                    selectedDeviceName = option.name
                } else {
                    selectedDeviceName = deviceKey
                }
                selectedDeviceKey = deviceKey

                if let info = try? await AuthAPIClient.shared.fetchDeviceInfo(deviceKey: deviceKey) {
                    applyDeviceInfoMetadata(info)
                    let mac = info.bluetoothMacAddress.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !mac.isEmpty {
                        saveMac(mac, forDeviceKey: deviceKey)
                    }
                } else {
                    updateLayoutMode(name: selectedDeviceName, controlModel: nil)
                }

                let connected = await connectUsingResolvedMac()
                switch connected {
                case .success:
                    f1Logger.info("add vehicle success key=\(deviceKey)")
                    setStatusTip("添加成功")
                    isAddVehiclePresented = false
                case .error(let err):
                    f1Logger.error("add vehicle connect failed key=\(deviceKey) code=\(err.code) message=\(err.message)")
                    setStatusTip(err.message)
                }
                syncFromRepository()
            } catch {
                let message = error.localizedDescription
                f1Logger.error("add vehicle failed key=\(deviceKey) message=\(message)")
                if message.localizedCaseInsensitiveContains("decode") {
                    setStatusTip("添加失败，请检查设备号后重试")
                } else {
                    setStatusTip(message)
                }
            }
        }
    }

    func selectVehicle(_ option: VehicleOption) {
        Task {
            setStatusTip("车辆切换中...")
            let _ = await repository.disconnect()
            UserDefaults.standard.set(option.key, forKey: lastDeviceKeyStoreKey)
            selectedDeviceName = option.name
            selectedDeviceKey = option.key
            updateLayoutMode(name: option.name, controlModel: nil)
            connectAddress = ""
            UserDefaults.standard.set("", forKey: lastBluetoothMacStoreKey)

            do {
                try await AuthAPIClient.shared.setDefaultDevice(deviceKey: option.key)
                if let info = try? await AuthAPIClient.shared.fetchDeviceInfo(deviceKey: option.key) {
                    applyDeviceInfoMetadata(info)
                    let mac = info.bluetoothMacAddress.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !mac.isEmpty {
                        saveMac(mac, forDeviceKey: option.key)
                    }
                }
            } catch {
                setStatusTip(error.localizedDescription)
            }

            let connected = await connectUsingResolvedMac()
            if case .error(let err) = connected {
                setStatusTip(err.message)
            }
            syncFromRepository()
        }
    }

    func toggleAutoSense() {
        if isAutoSenseEnabled {
            showAutoSenseDisableConfirm = true
            return
        }

        Task {
            let next = true
            isAutoSenseEnabled = next
            persistLocalStateSnapshot()
            let result = await sendControlCommand(.toggleAutoSense(enabled: next))
            if case .success = result {
                setStatusTip("感应解锁已开启")
            } else if case .error(let err) = result {
                setStatusTip(err.message)
            }
            syncFromRepository()
        }
    }

    private func disableAutoSenseAndCleanup() async {
        isAutoSenseEnabled = false
        persistLocalStateSnapshot()

        let result = await sendControlCommand(.toggleAutoSense(enabled: false))
        switch result {
        case .success:
            if currentLayoutMode == .f2 {
                setStatusTip("感应解锁已关闭")
                return
            }
            _ = await repository.removeCurrentPairingRecord()
            let _ = await repository.disconnect()
            if !selectedDeviceKey.isEmpty {
                var dict = UserDefaults.standard.dictionary(forKey: deviceMacMapStoreKey) as? [String: String] ?? [:]
                dict.removeValue(forKey: selectedDeviceKey)
                UserDefaults.standard.set(dict, forKey: deviceMacMapStoreKey)
            }
            UserDefaults.standard.set("", forKey: lastBluetoothMacStoreKey)
            connectAddress = ""
            bleSystemConnected = false
            connectionState = .disconnected
            setStatusTip("感应解锁已关闭")
            shouldOpenBluetoothSettings = true
        case .error(let err):
            isAutoSenseEnabled = true
            persistLocalStateSnapshot()
            setStatusTip(err.message)
        }
    }

    func toggleMute() {
        Task {
            let next = !isMuteEnabled
            isMuteEnabled = next
            persistLocalStateSnapshot()
            let result = await sendControlCommand(.toggleMute(mute: next))
            if case .success = result {
                setStatusTip(next ? "静音设防已开启" : "有声设防已开启")
            } else if case .error(let err) = result {
                setStatusTip(err.message)
            }
            syncFromRepository()
        }
    }

    func setLock(locked: Bool) {
        Task {
            if isLocked == locked { return }
            isLocked = locked
            persistLocalStateSnapshot()
            let result = await sendControlCommand(.toggleLock(locked: locked))
            if case .success = result {
                setStatusTip(locked ? "车辆已上锁" : "车辆已解锁")
            } else if case .error(let err) = result {
                setStatusTip(err.message)
            }
            syncFromRepository()
        }
    }

    func findBike() {
        Task {
            let result = await sendControlCommand(.findBike)
            if case .success = result {
                setStatusTip("寻车指令已发送")
            } else if case .error(let err) = result {
                setStatusTip(err.message)
            }
            syncFromRepository()
        }
    }

    func applyDisarmSensitivity(_ level: Int) {
        disarmSensitivityLevel = max(0, min(8, level))
        persistSettingsSnapshot()
        sendSettingCommand(.setDisarmSensitivity(level0To8: disarmSensitivityLevel))
    }

    func applyArmSensitivity(_ level: Int) {
        armSensitivityLevel = max(0, min(8, level))
        persistSettingsSnapshot()
        sendSettingCommand(.setArmSensitivity(level0To8: armSensitivityLevel))
    }

    func applyAlarmSensitivity(_ index: Int) {
        alarmSensitivityIndex = max(0, min(2, index))
        persistSettingsSnapshot()
        sendSettingCommand(.setAlarmSensitivity(levelIndex0To2: alarmSensitivityIndex))
    }

    func applyAutoShutdown(_ minutes: Int) {
        let valid = [0, 3, 5, 10]
        autoShutdownMinutes = valid.contains(minutes) ? minutes : 0
        persistSettingsSnapshot()
        sendSettingCommand(.setAutoShutdown(minutes: autoShutdownMinutes))
    }

    private func connectUsingResolvedMac() async -> AppResult<Void> {
        let macResult = await resolveMacAddress()
        switch macResult {
        case .success(let mac):
            if mac.isEmpty {
                let _ = await repository.disconnect()
                setStatusTip(nil, autoHide: false)
                return .success(())
            }
            let _ = await repository.connectTo(macAddress: mac)
            let connected = await repository.ensureConnectedInBackground()
            switch connected {
            case .success:
                setStatusTip(nil, autoHide: false)
            case .error(let err):
                setStatusTip(err.message)
            }
            return connected
        case .error(let err):
            setStatusTip(err.message)
            return .error(err)
        }
    }

    private func resolveMacAddress() async -> AppResult<String> {
        if currentLayoutMode == .m1b {
            return .success("")
        }

        if !connectAddress.isEmpty {
            return .success(connectAddress)
        }

        let cachedMac = UserDefaults.standard.string(forKey: lastBluetoothMacStoreKey)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !cachedMac.isEmpty {
            connectAddress = cachedMac
            loadLocalStateSnapshotIfExists(for: cachedMac)
            loadSettingsSnapshotIfExists(for: cachedMac)
            if selectedDeviceName == "--" {
                selectedDeviceName = cachedMac
            }
            f1Logger.info("use cached bluetooth mac=\(cachedMac)")
            await refreshVehicleOptions()
            if currentLayoutMode == .m1b {
                return .success("")
            }
            return .success(cachedMac)
        }

        do {
            let devices = try await AuthAPIClient.shared.fetchUserDevices()
            guard !devices.isEmpty else {
                return .error(BleError(code: "NO_DEVICE", message: "暂无可连接设备"))
            }

            let options = devices.map { makeVehicleOption(from: $0) }
            vehicleOptions = options

            let lastDeviceKey = UserDefaults.standard.string(forKey: lastDeviceKeyStoreKey) ?? ""
            let selected = devices.first(where: { $0.deviceKey == lastDeviceKey }) ?? devices[0]
            UserDefaults.standard.set(selected.deviceKey, forKey: lastDeviceKeyStoreKey)
            selectedDeviceName = selected.shortName.isEmpty ? selected.deviceKey : selected.shortName
            selectedDeviceKey = selected.deviceKey
            updateLayoutMode(name: selectedDeviceName, controlModel: nil)

            if let cachedByKey = macForDeviceKey(selected.deviceKey), !cachedByKey.isEmpty {
                connectAddress = cachedByKey
                UserDefaults.standard.set(cachedByKey, forKey: lastBluetoothMacStoreKey)
                loadLocalStateSnapshotIfExists(for: cachedByKey)
                loadSettingsSnapshotIfExists(for: cachedByKey)
                if let info = try? await AuthAPIClient.shared.fetchDeviceInfo(deviceKey: selected.deviceKey) {
                    applyDeviceInfoMetadata(info)
                } else {
                    Task { await refreshSelectedDeviceLocation() }
                }
                if currentLayoutMode == .m1b {
                    return .success("")
                }
                return .success(cachedByKey)
            }

            let info = try await AuthAPIClient.shared.fetchDeviceInfo(deviceKey: selected.deviceKey)
            if (info.deviceModel?.controlModel.uppercased().contains("M1")) == true {
                applyDeviceInfoMetadata(info)
                return .success("")
            }
            let mac = info.bluetoothMacAddress.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !mac.isEmpty else {
                return .error(BleError(code: "NO_MAC", message: "设备未配置蓝牙 MAC"))
            }

            connectAddress = mac
            UserDefaults.standard.set(mac, forKey: lastBluetoothMacStoreKey)
            saveMac(mac, forDeviceKey: selected.deviceKey)
            f1Logger.info("resolved bluetooth mac from api key=\(selected.deviceKey) mac=\(mac)")
            loadLocalStateSnapshotIfExists(for: mac)
            loadSettingsSnapshotIfExists(for: mac)
            applyDeviceInfoMetadata(info)
            return .success(mac)
        } catch {
            return .error(BleError(code: "API_ERROR", message: error.localizedDescription))
        }
    }

    private func stateSnapshotKey(for mac: String) -> String {
        localBleStatePrefix + mac.replacingOccurrences(of: ":", with: "").uppercased()
    }

    private func settingsSnapshotKey(for mac: String) -> String {
        f1SettingsStorePrefix + mac.replacingOccurrences(of: ":", with: "").uppercased()
    }

    private func persistLocalStateSnapshot() {
        guard !connectAddress.isEmpty else { return }
        let key = stateSnapshotKey(for: connectAddress)
        let dict: [String: Bool] = [
            "isLocked": isLocked,
            "isMuteEnabled": isMuteEnabled,
            "isAutoSenseEnabled": isAutoSenseEnabled,
        ]
        UserDefaults.standard.set(dict, forKey: key)
    }

    private func persistSettingsSnapshot() {
        guard !connectAddress.isEmpty else { return }
        let key = settingsSnapshotKey(for: connectAddress)
        let dict: [String: Int] = [
            "disarmSensitivityLevel": disarmSensitivityLevel,
            "armSensitivityLevel": armSensitivityLevel,
            "alarmSensitivityIndex": alarmSensitivityIndex,
            "autoShutdownMinutes": autoShutdownMinutes,
        ]
        UserDefaults.standard.set(dict, forKey: key)
    }

    private func loadLocalStateSnapshotIfExists(for mac: String) {
        let key = stateSnapshotKey(for: mac)
        guard let dict = UserDefaults.standard.dictionary(forKey: key) as? [String: Bool] else { return }
        isLocked = dict["isLocked"] ?? isLocked
        isMuteEnabled = dict["isMuteEnabled"] ?? isMuteEnabled
        isAutoSenseEnabled = dict["isAutoSenseEnabled"] ?? isAutoSenseEnabled
    }

    private func loadSettingsSnapshotIfExists(for mac: String) {
        let key = settingsSnapshotKey(for: mac)
        guard let dict = UserDefaults.standard.dictionary(forKey: key) as? [String: Int] else { return }
        disarmSensitivityLevel = max(0, min(8, dict["disarmSensitivityLevel"] ?? disarmSensitivityLevel))
        armSensitivityLevel = max(0, min(8, dict["armSensitivityLevel"] ?? armSensitivityLevel))
        alarmSensitivityIndex = max(0, min(2, dict["alarmSensitivityIndex"] ?? alarmSensitivityIndex))
        let auto = dict["autoShutdownMinutes"] ?? autoShutdownMinutes
        autoShutdownMinutes = [0, 3, 5, 10].contains(auto) ? auto : 0
    }

    private func syncFromRepository() {
        connectionState = repository.latestConnectionState
        bleSystemConnected = repository.latestSystemConnected
        let realtime = repository.latestRealtimeState
        if let lock = realtime.isLocked { isLocked = lock }
        if let mute = realtime.isMuteEnabled { isMuteEnabled = mute }
        if let auto = realtime.isAutoSenseEnabled { isAutoSenseEnabled = auto }
        if realtime.lastPlainHex.isEmpty == false {
            persistLocalStateSnapshot()
        }
        updateChannelAvailability()
    }

    private func setStatusTip(_ tip: String?, autoHide: Bool = true) {
        tipDismissTask?.cancel()
        statusTip = tip
        guard autoHide, let tip, !tip.isEmpty else { return }
        tipDismissTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            guard !Task.isCancelled else { return }
            self?.statusTip = nil
        }
    }

    private func refreshVehicleOptions() async {
        do {
            let devices = try await AuthAPIClient.shared.fetchUserDevices()
            vehicleOptions = devices.map { makeVehicleOption(from: $0) }
            let currentKey = UserDefaults.standard.string(forKey: lastDeviceKeyStoreKey) ?? ""
            if let selected = vehicleOptions.first(where: { $0.key == currentKey }) {
                selectedDeviceName = selected.name
                selectedDeviceKey = selected.key
                if let info = try? await AuthAPIClient.shared.fetchDeviceInfo(deviceKey: selected.key) {
                    applyDeviceInfoMetadata(info)
                } else {
                    updateLayoutMode(name: selected.name, controlModel: nil)
                }
            }
        } catch {
            f1Logger.error("refresh vehicle options failed=\(error.localizedDescription)")
        }
    }

    var shouldShowVehicleSelector: Bool {
        !vehicleOptions.isEmpty && !selectedDeviceName.isEmpty && selectedDeviceName != "--"
    }

    private var isNetworkOnline: Bool {
        onlineStatus == 1
    }

    private func macForDeviceKey(_ key: String) -> String? {
        guard let dict = UserDefaults.standard.dictionary(forKey: deviceMacMapStoreKey) as? [String: String] else {
            return nil
        }
        return dict[key]?.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func saveMac(_ mac: String, forDeviceKey key: String) {
        var dict = UserDefaults.standard.dictionary(forKey: deviceMacMapStoreKey) as? [String: String] ?? [:]
        dict[key] = mac
        UserDefaults.standard.set(dict, forKey: deviceMacMapStoreKey)
    }

    private func makeVehicleOption(from device: DeviceSummary) -> VehicleOption {
        let key = device.deviceKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let model = device.shortName.trimmingCharacters(in: .whitespacesAndNewlines)
        let suffix4 = key.count >= 4 ? String(key.suffix(4)) : key

        let displayName: String
        if model.isEmpty {
            displayName = key
        } else if model.hasSuffix(suffix4) {
            displayName = model
        } else {
            displayName = "\(model)\(suffix4)"
        }

        return VehicleOption(name: displayName, key: key)
    }

    private func updateLayoutMode(name: String, controlModel: String?) {
        let modelUpper = controlModel?.uppercased() ?? ""
        let upper = name.uppercased()
        let nextMode: LayoutMode
        if modelUpper.contains("M1") {
            nextMode = .m1b
        } else if modelUpper.contains("F2") {
            nextMode = .f2
        } else if modelUpper.contains("F1") {
            nextMode = .f1
        } else if upper.hasPrefix("F2") {
            nextMode = .f2
        } else if upper.hasPrefix("M1") {
            nextMode = .m1b
        } else {
            nextMode = .f1
        }

        if currentLayoutMode != nextMode {
            currentLayoutMode = nextMode
        }
        updateChannelAvailability()
    }

    private func refreshSelectedDeviceLocation() async {
        guard !selectedDeviceKey.isEmpty else { return }
        do {
            let info = try await AuthAPIClient.shared.fetchDeviceInfo(deviceKey: selectedDeviceKey)
            applyDeviceInfoMetadata(info)
        } catch {
            f1Logger.error("refresh selected device location failed=\(error.localizedDescription)")
        }
    }

    private func applyDeviceInfoMetadata(_ info: DeviceInfoResponse) {
        if selectedDeviceName.isEmpty || selectedDeviceName == "--" {
            if !info.shortName.isEmpty {
                selectedDeviceName = info.shortName
            } else if !selectedDeviceKey.isEmpty {
                selectedDeviceName = selectedDeviceKey
            }
        }
        signalStrengthLevel = info.onlineStatus == 1 ? max(0, min(5, info.signalStrength)) : 0
        onlineStatus = info.onlineStatus
        metricsTimeText = info.lastTripStartTime
        updateLayoutMode(name: selectedDeviceName, controlModel: info.deviceModel?.controlModel)
        applyLocation(from: info)
        updateChannelAvailability()
    }

    private func applyLocation(from info: DeviceInfoResponse) {
        latitude = info.latitude ?? 0
        longitude = info.longitude ?? 0
        let resolved = info.address?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !resolved.isEmpty {
            currentLocationAddress = resolved
        } else if latitude != 0 || longitude != 0 {
            currentLocationAddress = "获取地址中"
            Task {
                if let text = try? await AuthAPIClient.shared.fetchLocationAddress(lat: latitude, lng: longitude), !text.isEmpty {
                    currentLocationAddress = text
                }
            }
        } else {
            currentLocationAddress = "位置信息不可用"
        }
    }

    private func currentControlChannel() -> F1ControlChannel {
        F1ControlChannelPolicy.select(
            isF2: currentLayoutMode == .f2,
            bleReady: connectionState == .ready,
            networkOnline: isNetworkOnline
        )
    }

    private func updateChannelAvailability() {
        hasAnyChannel = currentControlChannel() != .none
    }

    private func sendControlCommand(_ command: BleControlCommand) async -> AppResult<Void> {
        let channel = currentControlChannel()
        switch channel {
        case .ble:
            let result = await repository.sendCommand(command, token: "")
            if currentLayoutMode == .f2, case .success = result {
                await backfillApiIfNeeded(for: command)
            }
            return result
        case .cellular4G:
            return await sendViaCellular(command)
        case .none:
            return .error(BleError(code: "NO_CHANNEL", message: "暂无可用通道"))
        }
    }

    private func backfillApiIfNeeded(for command: BleControlCommand) async {
        switch command {
        case .toggleMute,
             .toggleAutoSense,
             .findBike:
            _ = await sendViaCellular(command)
        default:
            break
        }
    }

    private func sendViaCellular(_ command: BleControlCommand) async -> AppResult<Void> {
        guard !selectedDeviceKey.isEmpty else {
            return .error(BleError(code: "NO_DEVICE", message: "设备不存在"))
        }

        do {
            switch command {
            case .toggleLock(let locked):
                try await AuthAPIClient.shared.toggleLock(deviceKey: selectedDeviceKey, locked: locked)
            case .toggleMute(let mute):
                try await AuthAPIClient.shared.toggleMute(deviceKey: selectedDeviceKey, mute: mute)
            case .toggleAutoSense(let enabled):
                try await AuthAPIClient.shared.toggleAutoSense(deviceKey: selectedDeviceKey, enabled: enabled)
            case .findBike:
                try await AuthAPIClient.shared.findBike(deviceKey: selectedDeviceKey)
            default:
                return .error(BleError(code: "NO_CHANNEL", message: "当前操作仅支持蓝牙"))
            }
            return .success(())
        } catch {
            return .error(BleError(code: "API_ERROR", message: error.localizedDescription))
        }
    }

    private func sendSettingCommand(_ command: BleControlCommand) {
        Task {
            let result = await repository.sendCommand(command, token: "")
            if case .error(let err) = result {
                setStatusTip(err.message)
            } else {
                switch command {
                case .setDisarmSensitivity:
                    setStatusTip("解防感应距离已设置")
                case .setArmSensitivity:
                    setStatusTip("设防感应距离已设置")
                case .setAlarmSensitivity:
                    setStatusTip("报警灵敏度已设置")
                case .setAutoShutdown:
                    setStatusTip("自动关机时间已设置")
                default:
                    setStatusTip("设置成功")
                }
            }
            syncFromRepository()
        }
    }
}

enum F1OtaStage: Equatable {
    case idle
    case preparing
    case downloading
    case requestingBase
    case erasing
    case writing
    case rebooting
    case success
    case failed(String)
}

struct OtaPackageOption: Identifiable, Equatable {
    let id: String
    let label: String
    let modelPrefix: String
    let version: String
    let url: String
}

@MainActor
protocol F1OtaRunning: AnyObject {
    func readFirmwareVersion(mac: String) async throws -> String
    func runOta(mac: String, url: String, onProgress: @escaping (Int, String) -> Void) async throws
}

enum OtaOpcode: UInt8 {
    case getStartBase = 1
    case pageErase = 3
    case writeData = 5
    case reboot = 9
}

struct Fr8010OtaCodec {
    func buildCommand(opcode: OtaOpcode, address: UInt32, dataLength: UInt16, data: [UInt8]?) -> [UInt8] {
        let headerLen = opcode == .pageErase ? 7 : 9
        let payload = data ?? []
        var out = [UInt8](repeating: 0, count: headerLen + payload.count)
        let lengthField: UInt16 = opcode == .pageErase ? 7 : (opcode == .getStartBase ? 3 : 9)
        out[0] = opcode.rawValue
        out[1] = UInt8(lengthField & 0xFF)
        out[2] = UInt8((lengthField >> 8) & 0xFF)
        out[3] = UInt8(address & 0xFF)
        out[4] = UInt8((address >> 8) & 0xFF)
        out[5] = UInt8((address >> 16) & 0xFF)
        out[6] = UInt8((address >> 24) & 0xFF)
        if headerLen > 7 {
            out[7] = UInt8(dataLength & 0xFF)
            out[8] = UInt8((dataLength >> 8) & 0xFF)
        }
        if !payload.isEmpty {
            out.replaceSubrange(headerLen..<(headerLen + payload.count), with: payload)
        }
        return out
    }

    func buildRebootCommand(fileLength: UInt32, crc: UInt32) -> [UInt8] {
        [
            OtaOpcode.reboot.rawValue,
            0x0A,
            0x00,
            UInt8(fileLength & 0xFF),
            UInt8((fileLength >> 8) & 0xFF),
            UInt8((fileLength >> 16) & 0xFF),
            UInt8((fileLength >> 24) & 0xFF),
            UInt8(crc & 0xFF),
            UInt8((crc >> 8) & 0xFF),
            UInt8((crc >> 16) & 0xFF),
            UInt8((crc >> 24) & 0xFF),
        ]
    }

    func calcLegacyCrc(_ bytes: [UInt8]) -> UInt32 {
        guard bytes.count > 256 else { return 0 }
        var crc: UInt32 = 0
        for byte in bytes.dropFirst(256) {
            let high = crc / 256
            crc = crc << 8
            crc = crc ^ Self.crcTable[Int((high ^ UInt32(byte)) & 0xFF)]
        }
        return crc
    }

    private static let crcTable: [UInt32] = {
        var table = [UInt32](repeating: 0, count: 256)
        for i in 0..<256 {
            var c = UInt32(i)
            for _ in 0..<8 {
                if (c & 1) != 0 {
                    c = 0xEDB88320 ^ (c >> 1)
                } else {
                    c >>= 1
                }
            }
            table[i] = c
        }
        return table
    }()
}

@MainActor
final class Fr8010OtaRunner: NSObject, F1OtaRunning {
    private static let androidAlignedMaxPayload = 235
    private let otaLogger = Logger(subsystem: "xiaochao.com", category: "BLE.OTA.FR8010")
    private let otaServiceUUID = CBUUID(string: "02f00000-0000-0000-0000-00000000fe00")
    private let otaWriteUUID = CBUUID(string: "02f00000-0000-0000-0000-00000000ff01")
    private let otaNotifyUUID = CBUUID(string: "02f00000-0000-0000-0000-00000000ff02")
    private let disServiceUUID = CBUUID(string: "0000180a-0000-1000-8000-00805f9b34fb")
    private let fwRevUUID = CBUUID(string: "00002a26-0000-1000-8000-00805f9b34fb")

    private let codec = Fr8010OtaCodec()
    private var central: CBCentralManager?
    private var peripheral: CBPeripheral?
    private var writeChar: CBCharacteristic?
    private var notifyChar: CBCharacteristic?
    private var fwChar: CBCharacteristic?
    private var targetMacSuffix: String?
    private var targetMacIdentifier: String?
    private var targetCompanyIdLE: [UInt8]?

    private var waitPowerOn: CheckedContinuation<Void, Error>?
    private var waitDiscover: CheckedContinuation<CBPeripheral, Error>?
    private var waitConnect: CheckedContinuation<Void, Error>?
    private var waitServicesReady: CheckedContinuation<Void, Error>?
    private var waitNotifyEnabled: CheckedContinuation<Void, Error>?
    private var waitReadVersion: CheckedContinuation<String, Error>?
    private var waitAck: CheckedContinuation<Data, Error>?
    private var waitReadyToSendWrite: CheckedContinuation<Void, Error>?
    private var ackTimeoutTask: Task<Void, Never>?
    private var writeReadyTimeoutTask: Task<Void, Never>?
    private var latestNotifyData: Data?
    private var writePacketLogCounter: Int = 0
    private var progressThrottleCounter: Int = 0

    static func payloadSize(forMtu mtu: Int) -> Int {
        payloadSize(forWriteLength: max(20, mtu - 3))
    }

    static func payloadSize(forWriteLength writeLength: Int) -> Int {
        min(androidAlignedMaxPayload, max(20, writeLength - 9))
    }

    static func preferredWriteType(for properties: CBCharacteristicProperties) -> CBCharacteristicWriteType {
        if properties.contains(.writeWithoutResponse) {
            return .withoutResponse
        }
        return .withResponse
    }

    func readFirmwareVersion(mac: String) async throws -> String {
        try await ensureOtaChannelReady(mac: mac)
        guard let p = peripheral, let fwChar else {
            throw NSError(domain: "OTA", code: -1, userInfo: [NSLocalizedDescriptionKey: "未找到版本特征"]) }

        return try await withCheckedThrowingContinuation { continuation in
            waitReadVersion = continuation
            p.readValue(for: fwChar)
        }
    }

    func runOta(mac: String, url: String, onProgress: @escaping (Int, String) -> Void) async throws {
        try await ensureOtaChannelReady(mac: mac)

        onProgress(5, "下载升级包")
        let fileData = try await downloadBin(url: url)
        guard fileData.count >= 100 else {
            throw NSError(domain: "OTA", code: -1, userInfo: [NSLocalizedDescriptionKey: "bin 文件无效"]) }
        let bytes = [UInt8](fileData)
        let crc = codec.calcLegacyCrc(bytes)
        let packetSize = resolvePayloadSize()
        writePacketLogCounter = 0
        progressThrottleCounter = 0
        otaLog("runOta start mac=\(mac) fileSize=\(bytes.count) packetSize=\(packetSize)")

        onProgress(10, "获取基地址")
        let baseAck = try await sendAndWaitAck(codec.buildCommand(opcode: .getStartBase, address: 0, dataLength: 0, data: nil))
        let base = parseAddress(from: baseAck)
        otaLog("base address=0x\(String(base, radix: 16))")

        onProgress(15, "擦除扇区")
        let pageCount = (bytes.count + 0xFFF) / 0x1000
        for i in 0..<pageCount {
            let addr = UInt32(base + i * 0x1000)
            _ = try await sendAndWaitAck(codec.buildCommand(opcode: .pageErase, address: addr, dataLength: 0, data: nil))
            let p = 15 + ((i + 1) * 10 / max(1, pageCount))
            onProgress(p, "擦除扇区 \(i + 1)/\(pageCount)")
        }

        onProgress(25, "写入固件")
        var offset = 0
        var addr = UInt32(base)
        var lastWriteAddress = Int(base)
        while offset < bytes.count {
            let len = min(packetSize, bytes.count - offset)
            let chunk = Array(bytes[offset..<(offset + len)])
            lastWriteAddress = Int(addr)
            try await sendWriteDataWithRetry(address: addr, length: len, chunk: chunk)
            offset += len
            addr += UInt32(len)
            // Throttle UI progress updates to every 20 packets to reduce
            // MainActor contention from SwiftUI @Published re-renders.
            progressThrottleCounter += 1
            let isLast = offset >= bytes.count
            if isLast || progressThrottleCounter % 20 == 0 {
                let progress = 25 + (offset * 70 / bytes.count)
                onProgress(progress, "写入中 \(offset)/\(bytes.count)")
            }
            if isLast || offset % max(1, packetSize * 40) == 0 {
                otaLog("write progress offset=\(offset)/\(bytes.count) addr=0x\(String(addr, radix: 16))")
            }
        }

        if let latestNotifyData {
            let finalAckAddress = parseAddress(from: latestNotifyData)
            if finalAckAddress != lastWriteAddress {
                throw NSError(
                    domain: "OTA",
                    code: -1,
                    userInfo: [NSLocalizedDescriptionKey: "最后写入ACK地址异常: 0x\(String(finalAckAddress, radix: 16))"]
                )
            }
        }

        onProgress(98, "发送重启命令")
        try sendWithoutAck(codec.buildRebootCommand(fileLength: UInt32(bytes.count), crc: crc))
        otaLog("reboot command sent fileLen=\(bytes.count) crc=0x\(String(crc, radix: 16))")
        onProgress(100, "OTA 完成")
    }

    private func ensureOtaChannelReady(mac: String) async throws {
        targetMacSuffix = BleAdvertisementMatcher.standardMacSuffix(from: mac)
        targetMacIdentifier = BleAdvertisementMatcher.standardMacIdentifier(from: mac)
        targetCompanyIdLE = BleAdvertisementMatcher.expectedCompanyIdLE(from: mac)
        if central == nil {
            central = CBCentralManager(delegate: self, queue: nil)
        }
        let manager = central!
        if manager.state != .poweredOn {
            try await withCheckedThrowingContinuation { continuation in
                waitPowerOn = continuation
            }
        }

        if peripheral == nil {
            if let reused = manager.retrieveConnectedPeripherals(withServices: [otaServiceUUID]).first {
                peripheral = reused
            } else {
                peripheral = try await discoverPeripheral(manager: manager)
            }
        }

        if let p = peripheral, p.state != .connected {
            p.delegate = self
            try await withCheckedThrowingContinuation { continuation in
                waitConnect = continuation
                manager.connect(p, options: nil)
            }
        }

        guard writeChar == nil || notifyChar == nil || fwChar == nil else { return }
        guard let p = peripheral else { throw NSError(domain: "OTA", code: -1, userInfo: [NSLocalizedDescriptionKey: "蓝牙未连接"]) }
        p.delegate = self
        try await withCheckedThrowingContinuation { continuation in
            waitServicesReady = continuation
            p.discoverServices([otaServiceUUID, disServiceUUID])
        }
    }

    private func discoverPeripheral(manager: CBCentralManager) async throws -> CBPeripheral {
        try await withCheckedThrowingContinuation { continuation in
            waitDiscover = continuation
            manager.scanForPeripherals(withServices: [otaServiceUUID], options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
            Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: 8_000_000_000)
                guard let self, let waitDiscover = self.waitDiscover else { return }
                self.waitDiscover = nil
                manager.stopScan()
                waitDiscover.resume(throwing: NSError(domain: "OTA", code: -1, userInfo: [NSLocalizedDescriptionKey: "未找到 OTA 设备"]))
            }
        }
    }

    private func parseAddress(from data: Data) -> Int {
        let bytes = [UInt8](data)
        guard bytes.count >= 8 else { return 0 }
        return Int(bytes[4]) | (Int(bytes[5]) << 8) | (Int(bytes[6]) << 16) | (Int(bytes[7]) << 24)
    }

    private func downloadBin(url: String) async throws -> Data {
        guard let value = URL(string: url) else {
            throw NSError(domain: "OTA", code: -1, userInfo: [NSLocalizedDescriptionKey: "OTA URL 无效"]) }
        let (data, response) = try await URLSession.shared.data(from: value)
        guard let http = response as? HTTPURLResponse, 200..<300 ~= http.statusCode else {
            throw NSError(domain: "OTA", code: -1, userInfo: [NSLocalizedDescriptionKey: "下载升级包失败"]) }
        return data
    }

    /// Send a packet and wait for the device's ACK notification.
    /// Critical path for OTA throughput — avoid spawning extra Tasks.
    /// Instead of nested Task { @MainActor } that competes for the actor,
    /// we send inline (we're already on MainActor) and use a single
    /// timeout Task. This cuts per-packet MainActor hops from 4+ to 2.
    private func sendAndWaitAck(_ packet: [UInt8]) async throws -> Data {
        let opcode = packet.first.map { Int($0) } ?? -1
        if shouldLogPacket(opcode: opcode) {
            otaLog("tx \(Self.opcodeName(opcode)) len=\(packet.count) data=\(Self.hexPreview(packet, maxBytes: 24))")
        }

        // 1. Wait for BLE write-without-response flow control (inline, no extra Task)
        try await waitForNoResponseReadyIfNeeded()

        // 2. Send the packet directly (we are on MainActor)
        try sendWithoutAck(packet)

        // 3. Wait for ACK via notification, with timeout
        return try await withCheckedThrowingContinuation { continuation in
            waitAck = continuation
            ackTimeoutTask?.cancel()
            ackTimeoutTask = Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: 6_000_000_000)
                guard !Task.isCancelled else { return }
                guard let self, let pending = self.waitAck else { return }
                self.waitAck = nil
                pending.resume(throwing: NSError(domain: "OTA", code: -1, userInfo: [NSLocalizedDescriptionKey: "等待ACK超时"]))
            }
        }
    }

    private func sendWithoutAck(_ packet: [UInt8]) throws {
        guard let p = peripheral, let writeChar else {
            throw NSError(domain: "OTA", code: -1, userInfo: [NSLocalizedDescriptionKey: "写特征未就绪"]) }
        let data = Data(packet)
        let writeType = currentWriteType()
        p.writeValue(data, for: writeChar, type: writeType)
    }

    private func currentWriteType() -> CBCharacteristicWriteType {
        guard let writeChar else { return .withResponse }
        return Self.preferredWriteType(for: writeChar.properties)
    }

    /// Check BLE flow control for write-without-response.
    /// Inlined into the send path (no extra Task spawn) to reduce latency.
    private func waitForNoResponseReadyIfNeeded() async throws {
        guard let peripheral else { return }
        guard currentWriteType() == .withoutResponse else { return }
        guard !peripheral.canSendWriteWithoutResponse else { return }

        try await withCheckedThrowingContinuation { continuation in
            waitReadyToSendWrite = continuation
            writeReadyTimeoutTask?.cancel()
            writeReadyTimeoutTask = Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: 2_000_000_000)
                guard !Task.isCancelled else { return }
                guard let self, let pending = self.waitReadyToSendWrite else { return }
                self.waitReadyToSendWrite = nil
                pending.resume(throwing: NSError(domain: "OTA", code: -1, userInfo: [NSLocalizedDescriptionKey: "等待写入窗口超时"]))
            }
        }
    }

    private func sendWriteDataWithRetry(address: UInt32, length: Int, chunk: [UInt8]) async throws {
        let packet = codec.buildCommand(opcode: .writeData, address: address, dataLength: UInt16(length), data: chunk)
        do {
            _ = try await sendAndWaitAck(packet)
        } catch {
            let message = error.localizedDescription
            guard message.contains("等待ACK超时") else {
                throw error
            }
            otaLog("write retry addr=0x\(String(address, radix: 16)) len=\(length)")
            try? await Task.sleep(nanoseconds: 10_000_000)
            _ = try await sendAndWaitAck(packet)
        }
    }

    private func resolvePayloadSize() -> Int {
        Self.androidAlignedMaxPayload
    }

    private func otaLog(_ message: String) {
        otaLogger.debug("\(message, privacy: .public)")
    }

    private func shouldLogPacket(opcode: Int) -> Bool {
        guard opcode == 5 else { return true }
        writePacketLogCounter += 1
        return writePacketLogCounter % 40 == 0
    }

    private static func opcodeName(_ opcode: Int) -> String {
        switch opcode {
        case 1: return "GET_STR_BASE"
        case 3: return "PAGE_ERASE"
        case 5: return "WRITE_DATA"
        case 9: return "REBOOT"
        default: return "UNKNOWN(\(opcode))"
        }
    }

    private static func hexPreview(_ bytes: [UInt8], maxBytes: Int) -> String {
        if bytes.isEmpty { return "empty" }
        let shown = min(max(1, maxBytes), bytes.count)
        let prefix = bytes.prefix(shown).map { String(format: "%02X", $0) }.joined(separator: " ")
        if bytes.count > shown {
            return "\(prefix)...(+\(bytes.count - shown))"
        }
        return prefix
    }
}

@MainActor
extension Fr8010OtaRunner: @preconcurrency CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            waitPowerOn?.resume()
            waitPowerOn = nil
            return
        }

        if let continuation = waitPowerOn {
            waitPowerOn = nil
            continuation.resume(throwing: NSError(domain: "OTA", code: -1, userInfo: [NSLocalizedDescriptionKey: "蓝牙未开启"]))
        }
    }

    func centralManager(_ central: CBCentralManager,
                        didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any],
                        rssi RSSI: NSNumber) {
        if let targetMacSuffix,
           !BleAdvertisementMatcher.shouldConnectUsingStandardMacSuffix(
               advertisementData: advertisementData,
               targetMacSuffix: targetMacSuffix,
               targetIdentifier: targetMacIdentifier,
               expectedCompanyIdLE: targetCompanyIdLE
           ) {
            return
        }

        central.stopScan()
        waitDiscover?.resume(returning: peripheral)
        waitDiscover = nil
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        waitConnect?.resume()
        waitConnect = nil
    }

    func centralManager(_ central: CBCentralManager,
                        didFailToConnect peripheral: CBPeripheral,
                        error: Error?) {
        let err = error ?? NSError(domain: "OTA", code: -1, userInfo: [NSLocalizedDescriptionKey: "连接失败"])
        waitConnect?.resume(throwing: err)
        waitConnect = nil
    }
}

@MainActor
extension Fr8010OtaRunner: @preconcurrency CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error {
            waitServicesReady?.resume(throwing: error)
            waitServicesReady = nil
            return
        }
        peripheral.services?.forEach { service in
            if service.uuid == otaServiceUUID {
                peripheral.discoverCharacteristics([otaWriteUUID, otaNotifyUUID], for: service)
            } else if service.uuid == disServiceUUID {
                peripheral.discoverCharacteristics([fwRevUUID], for: service)
            }
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        if let error {
            waitServicesReady?.resume(throwing: error)
            waitServicesReady = nil
            return
        }

        for characteristic in service.characteristics ?? [] {
            if characteristic.uuid == otaWriteUUID {
                writeChar = characteristic
            } else if characteristic.uuid == otaNotifyUUID {
                notifyChar = characteristic
                waitNotifyEnabled = waitServicesReady
                peripheral.setNotifyValue(true, for: characteristic)
            } else if characteristic.uuid == fwRevUUID {
                fwChar = characteristic
            }
        }

        if notifyChar == nil, writeChar != nil, fwChar != nil {
            waitServicesReady?.resume()
            waitServicesReady = nil
        }
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didUpdateNotificationStateFor characteristic: CBCharacteristic,
                    error: Error?) {
        if let error {
            waitNotifyEnabled?.resume(throwing: error)
            waitNotifyEnabled = nil
            waitServicesReady = nil
            return
        }

        waitNotifyEnabled?.resume()
        waitNotifyEnabled = nil
        waitServicesReady = nil
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        if let error {
            if let pending = waitReadVersion {
                waitReadVersion = nil
                pending.resume(throwing: error)
            }
            return
        }

        guard let data = characteristic.value else { return }
        latestNotifyData = data

        if characteristic.uuid == fwRevUUID {
            let text = String(decoding: data, as: UTF8.self).trimmingCharacters(in: .controlCharacters.union(.whitespacesAndNewlines))
            waitReadVersion?.resume(returning: text.isEmpty ? "-" : text)
            waitReadVersion = nil
            return
        }

        if characteristic.uuid == otaNotifyUUID, let pending = waitAck {
            waitAck = nil
            ackTimeoutTask?.cancel()
            ackTimeoutTask = nil
            let opcode = Int(data.first ?? 0)
            if shouldLogPacket(opcode: opcode) {
                otaLog("ack len=\(data.count) data=\(Self.hexPreview([UInt8](data), maxBytes: 24))")
            }
            pending.resume(returning: data)
        }
    }

    func peripheralIsReady(toSendWriteWithoutResponse peripheral: CBPeripheral) {
        writeReadyTimeoutTask?.cancel()
        writeReadyTimeoutTask = nil
        waitReadyToSendWrite?.resume()
        waitReadyToSendWrite = nil
    }
}

@MainActor
final class F1OtaViewModel: ObservableObject {
    @Published private(set) var stage: F1OtaStage = .idle
    @Published private(set) var progress: Int = 0
    @Published private(set) var progressText: String = "等待开始"
    @Published private(set) var currentVersion: String = "-"
    @Published private(set) var isRunning: Bool = false
    @Published var selectedOptionIndex: Int = 0

    let packageOptions: [OtaPackageOption]
    let macAddress: String

    private let repository: BleRepository
    private let runner: F1OtaRunning

    init(repository: BleRepository, runner: F1OtaRunning, macAddress: String, modelType: String) {
        self.repository = repository
        self.runner = runner
        self.macAddress = macAddress
        self.packageOptions = Self.defaultPackageOptions(modelType: modelType)
        if let idx = packageOptions.firstIndex(where: { modelType.uppercased().hasPrefix($0.modelPrefix) }) {
            self.selectedOptionIndex = idx
        }
    }

    func refreshVersion() async {
        guard !macAddress.isEmpty else {
            stage = .failed("未找到蓝牙 MAC 地址")
            return
        }
        do {
            stage = .preparing
            currentVersion = try await runner.readFirmwareVersion(mac: macAddress)
            stage = .idle
        } catch {
            stage = .failed(error.localizedDescription)
        }
    }

    func startUpgrade() async {
        guard !isRunning else { return }
        guard !macAddress.isEmpty else {
            stage = .failed("未找到蓝牙 MAC 地址")
            return
        }

        isRunning = true
        progress = 0
        progressText = "开始 OTA"
        stage = .preparing
        repository.setOtaExecutionActive(true)
        defer {
            repository.setOtaExecutionActive(false)
            isRunning = false
        }

        let option = packageOptions[min(selectedOptionIndex, packageOptions.count - 1)]
        do {
            try await runner.runOta(mac: macAddress, url: option.url) { [weak self] p, text in
                self?.progress = max(0, min(100, p))
                self?.progressText = text
                if p <= 10 {
                    self?.stage = .requestingBase
                } else if p < 25 {
                    self?.stage = .erasing
                } else if p < 98 {
                    self?.stage = .writing
                } else {
                    self?.stage = .rebooting
                }
            }
            stage = .success
            progress = 100
            progressText = "OTA 完成"
        } catch {
            stage = .failed(error.localizedDescription)
        }
    }

    private static func defaultPackageOptions(modelType: String) -> [OtaPackageOption] {
        [
            .init(id: "f1-1.2.0", label: "F1 固件 v1.2.0", modelPrefix: "F1", version: "1.2.0", url: "https://cdn.tr.sheyutech.com/upload/device_firmware/TR_H810x_F1_ota_v1.2.0.bin"),
            .init(id: "f1-1.3.0", label: "F1 固件 v1.3.0", modelPrefix: "F1", version: "1.3.0", url: "https://cdn.tr.sheyutech.com/upload/device_firmware/TR_H810x_F1_ota_v1.3.0.bin"),
            .init(id: "f1-1.3.1", label: "F1 固件 v1.3.1", modelPrefix: "F1", version: "1.3.1", url: "https://cdn.tr.sheyutech.com/upload/device_firmware/TR_H810x_F1_ota_v1.3.1.bin"),
            .init(id: "f2-1.3.0", label: "F2 固件 v1.3.0", modelPrefix: "F2", version: "1.3.0", url: "http://cdn.tr.sheyutech.com/upload/device_firmware/TR_H810x_F2_ota_v1.3.0.bin"),
        ]
    }
}

@MainActor
extension F1ViewModel {
    static var preview: F1ViewModel {
        F1ViewModel(repository: PreviewBleRepository())
    }
}

@MainActor
private final class PreviewBleRepository: BleRepository {
    var latestConnectionState: BleConnectionState = .ready
    var latestRealtimeState: BleRealtimeState = BleRealtimeState(isLocked: true, isMuteEnabled: false, isAutoSenseEnabled: false)
    var latestSystemConnected: Bool = true
    var isOtaExecutionActive: Bool = false

    func ensureConnectedInBackground() async -> AppResult<Void> {
        .success(())
    }

    func connectTo(macAddress: String) async -> AppResult<Void> {
        .success(())
    }

    func disconnect() async -> AppResult<Void> {
        .success(())
    }

    func removeCurrentPairingRecord() async -> AppResult<Bool> {
        .success(true)
    }

    func sendCommand(_ command: BleControlCommand, token: String) async -> AppResult<Void> {
        switch command {
        case .toggleLock(let locked):
            latestRealtimeState.isLocked = locked
        case .toggleMute(let mute):
            latestRealtimeState.isMuteEnabled = mute
        case .toggleAutoSense(let enabled):
            latestRealtimeState.isAutoSenseEnabled = enabled
        case .findBike:
            break
        case .setDisarmSensitivity,
             .setArmSensitivity,
             .setAlarmSensitivity,
             .setAutoShutdown,
             .sensorLevelLocation:
            break
        }
        return .success(())
    }

    func setOtaExecutionActive(_ active: Bool) {
        isOtaExecutionActive = active
    }
}
