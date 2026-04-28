package xiaochao.com.feature.f2.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xiaochao.com.core.log.AppLogger
import xiaochao.com.core.log.LogEntry
import xiaochao.com.core.result.AppResult
import xiaochao.com.data.api.ApiRepository
import xiaochao.com.data.api.ApiRepositoryImpl
import xiaochao.com.data.ble.AndroidBleManager
import xiaochao.com.data.ble.BleRepository
import xiaochao.com.data.ble.BleRepositoryImpl
import xiaochao.com.data.model.ControlCommand
import xiaochao.com.data.model.DeviceType
import xiaochao.com.data.session.AppSessionStore
import xiaochao.com.domain.control.ControlChannelPolicy
import xiaochao.com.domain.control.SendControlCommandUseCase

class F2ViewModel(
    private val logger: AppLogger = AppLogger(),
    private val apiRepository: ApiRepository = ApiRepositoryImpl(),
    private val bleRepository: BleRepository = BleRepositoryImpl(AndroidBleManager.getInstance(AppLogger())),
) : ViewModel() {
    private var pendingMuteTarget: Boolean? = null
    private var muteProtectUntilMs: Long = 0L

    private fun bleDebug(message: String) {
        Log.d("BLE-DEBUG", message)
    }

    private val sendControlCommandUseCase = SendControlCommandUseCase(
        policy = ControlChannelPolicy(),
        bleRepository = bleRepository,
        apiRepository = apiRepository,
        logger = logger,
    )

    private val _uiState = MutableStateFlow(F2UiState(isLoading = true))
    val uiState: StateFlow<F2UiState> = _uiState.asStateFlow()

    init {
        // 持续监听 BLE 通道状态，实时更新 hasAnyChannel / bleConnected
        viewModelScope.launch {
            bleRepository.channelAvailability.collect { avail ->
                val deviceType = _uiState.value.deviceType
                val online = _uiState.value.onlineStatus == 1
                val hasAnyChannel = when (deviceType) {
                    DeviceType.F1 -> avail.bleAvailable
                    DeviceType.F2 -> avail.bleAvailable || online
                }
                _uiState.update {
                    it.copy(
                        bleConnected = avail.bleAvailable,
                        bleSystemConnected = bleRepository.isCurrentDevicePaired(),
                        hasAnyChannel = hasAnyChannel,
                    )
                }
                logger.info(
                    LogEntry(
                        tag = "channel_state",
                        message = "Channel availability changed",
                        payload = mapOf(
                            "ble" to avail.bleAvailable,
                            "net" to online,
                            "hasAnyChannel" to hasAnyChannel,
                            "deviceType" to deviceType.name,
                        )
                    )
                )
            }
        }

        viewModelScope.launch {
            bleRepository.realtimeState.collect { rt ->
                val bleAvail = bleRepository.channelAvailability.value.bleAvailable
                // [TODO: TEMP COMMENT] 暂时注释，后续还原
                // android.util.Log.d("F1_LOCK_DEBUG", "realtimeState collected: isLocked=${rt.isLocked} plainHex=${rt.lastPlainHex} bleAvailable=$bleAvail currentUiLocked=${_uiState.value.isLocked}")
                if (!bleAvail) return@collect
                val now = System.currentTimeMillis()
                _uiState.update {
                    val incomingMute = rt.isMuteEnabled
                    val nextMute = if (incomingMute == null) {
                        it.isMuteEnabled
                    } else if (shouldApplyRealtimeMute(pendingMuteTarget, muteProtectUntilMs, now, incomingMute)) {
                        incomingMute
                    } else {
                        bleDebug("ignore opposite realtime mute during protection window incoming=$incomingMute pending=$pendingMuteTarget")
                        it.isMuteEnabled
                    }
                    it.copy(
                        isLocked = rt.isLocked ?: it.isLocked,
                        isMuteEnabled = nextMute,
                        isAutoSenseEnabled = rt.isAutoSenseEnabled ?: it.isAutoSenseEnabled,
                    )
                }
                if (pendingMuteTarget != null && now > muteProtectUntilMs) {
                    pendingMuteTarget = null
                }
                if (rt.lastPlainHex.isNotEmpty()) {
                    bleDebug("realtimeState applied from BLE plain=${rt.lastPlainHex}")
                    persistBleControlSnapshot()
                }
            }
        }

        viewModelScope.launch {
            while (isActive) {
                val paired = bleRepository.isCurrentDevicePaired()
                _uiState.update {
                    if (it.bleSystemConnected == paired) it else it.copy(bleSystemConnected = paired)
                }
                delay(800)
            }
        }
        // 初次加载数据后，后台启动 BLE 重连
        dispatch(F2Intent.OnRefresh)
    }

    fun dispatch(intent: F2Intent) {
        when (intent) {
            F2Intent.OnRefresh          -> loadInitialData()
            F2Intent.OnScreenActive     -> syncBleStateFromRepo()
            F2Intent.OnAutoSenseClick   -> {
                bleDebug("OnAutoSenseClick deviceKey=${_uiState.value.deviceKey} owner=${_uiState.value.isOwner} bleConnected=${_uiState.value.bleConnected}")
                if (!checkOwnerAndDevice("您不是车主，无法进行此操作")) return
                if (!checkBleConnected()) return
                toggleAutoSense()
            }
            F2Intent.OnBluetoothClick   -> toggleBleConnection()
            F2Intent.OnFindBikeClick    -> {
                if (!checkDeviceExists()) return
                sendCommand(ControlCommand.FindBike)
            }
            F2Intent.OnMuteClick        -> {
                if (!checkOwnerAndDevice("您不是车主，无法进行此操作")) return
                if (!checkBleConnected()) return
                toggleMute()
            }
            is F2Intent.OnLockSliderChange -> {
                if (!checkBleConnected()) return
                toggleLock(intent.locked)
            }
            is F2Intent.OnDeviceSwitch  -> switchDevice(intent.deviceKey, intent.deviceType)
            F2Intent.OnScanClick -> _uiState.update { it.copy(navigateToAddVehicle = true) }
            F2Intent.OnAddVehicleNavigationConsumed -> _uiState.update { it.copy(navigateToAddVehicle = false) }
            F2Intent.OnSettingsNavigationConsumed -> _uiState.update { it.copy(navigateToSettings = false) }
            F2Intent.OnShareNavigationConsumed -> _uiState.update { it.copy(navigateToShareUsers = false) }
            F2Intent.OnM1BNavigationConsumed -> _uiState.update { it.copy(navigateToM1B = false) }
            F2Intent.OnTipConsumed -> _uiState.update { it.copy(tipMessage = null) }
            is F2Intent.OnSosSave -> saveSosPhone(intent.phone)
            F2Intent.OnCurrentLocationClick -> handleCurrentLocationClick()
            F2Intent.OnHistoryTrackClick -> handleHistoryTrackClick()
            F2Intent.OnSettingsClick -> {
                if (!checkOwnerAndDevice("您不是车主，无法进行此操作")) return
                _uiState.update { it.copy(navigateToSettings = true) }
            }
            F2Intent.OnShareClick -> {
                if (!checkOwnerAndDevice("您不是车主，无法进行此操作")) return
                _uiState.update { it.copy(navigateToShareUsers = true) }
            }
        }
    }

    private fun checkDeviceExists(): Boolean {
        if (_uiState.value.deviceKey.isBlank()) {
            _uiState.update { it.copy(tipMessage = "设备不存在") }
            return false
        }
        return true
    }

    private fun checkOwnerAndDevice(message: String): Boolean {
        if (!checkDeviceExists()) return false
        if (!_uiState.value.isOwner) {
            _uiState.update { it.copy(tipMessage = message) }
            return false
        }
        return true
    }

    private fun checkBleConnected(): Boolean {
        if (_uiState.value.deviceType == DeviceType.F1 && !_uiState.value.bleConnected) {
            _uiState.update { it.copy(tipMessage = "请先连接蓝牙") }
            return false
        }
        return true
    }

    private fun handleCurrentLocationClick() {
        if (!checkDeviceExists()) return
        if (_uiState.value.gpsLocked) {
            _uiState.update { it.copy(tipMessage = "GPS 锁定中") }
            return
        }
        viewModelScope.launch {
            when (val status = apiRepository.fetchVehicleStatus(_uiState.value.deviceKey)) {
                is AppResult.Success -> {
                    if (status.data.latitude == 0.0 && status.data.longitude == 0.0) {
                        _uiState.update { it.copy(tipMessage = "无法获取设备位置") }
                    } else {
                        _uiState.update {
                            it.copy(
                                latitude = status.data.latitude,
                                longitude = status.data.longitude,
                                tipMessage = "定位已更新"
                            )
                        }
                        refreshMapAddresses(
                            deviceKey = _uiState.value.deviceKey,
                            currentLatitude = status.data.latitude,
                            currentLongitude = status.data.longitude,
                        )
                    }
                }
                is AppResult.Error -> _uiState.update { it.copy(tipMessage = status.message.ifBlank { "加载地图失败" }) }
            }
        }
    }

    private fun handleHistoryTrackClick() {
        if (!checkOwnerAndDevice("您不是车主，无法进行此操作")) return
        viewModelScope.launch {
            when (val result = apiRepository.fetchDeviceTrack(_uiState.value.deviceKey, 20)) {
                is AppResult.Success -> _uiState.update { it.copy(tipMessage = "轨迹加载完成") }
                is AppResult.Error -> _uiState.update { it.copy(tipMessage = result.message.ifBlank { "获取轨迹失败" }) }
            }
        }
    }

    private fun saveSosPhone(phone: String) {
        if (!checkDeviceExists()) return
        val next = phone.trim()
        if (next.isEmpty()) {
            _uiState.update { it.copy(tipMessage = "请输入手机号") }
            return
        }
        if (!Regex("^1\\d{10}$").matches(next)) {
            _uiState.update { it.copy(tipMessage = "请输入11位手机号") }
            return
        }
        viewModelScope.launch {
            when (val result = apiRepository.setSosPhone(_uiState.value.deviceKey, next)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(sosPhoneMasked = result.data, tipMessage = "SOS 联系人已更新") }
                }
                is AppResult.Error -> {
                    _uiState.update { it.copy(tipMessage = result.message.ifBlank { "设置 SOS 联系人失败" }) }
                }
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val deviceResult = apiRepository.fetchUserDevices()) {
                is AppResult.Success -> {
                    val devices = deviceResult.data
                    val options = devices.map { DeviceOption(it.short_name, it.device_key) }
                    if (devices.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                vehicleOptions = emptyList(),
                                deviceKey = "",
                                isLoading = false,
                                navigateToAddVehicle = true,
                            )
                        }
                        return@launch
                    }

                    val storedKey = AppSessionStore.getLastDeviceKey()
                    val currentKey = _uiState.value.deviceKey
                    val selected = devices.firstOrNull { it.device_key == currentKey }
                        ?: devices.firstOrNull { it.device_key == storedKey }
                        ?: devices.first()

                    AppSessionStore.setLastDeviceKey(selected.device_key)
                    apiRepository.setDefaultDevice(selected.device_key)

                    when (val result = apiRepository.fetchVehicleStatus(selected.device_key)) {
                        is AppResult.Success -> {
                            val status = result.data
                            val upperModel = status.controlModel.uppercase()
                            val isM1Model = upperModel.contains("M1")
                            val resolvedDeviceType = if (upperModel.contains("F1")) {
                                DeviceType.F1
                            } else {
                                DeviceType.F2
                            }
                            AppSessionStore.setModelType(status.controlModel)
                            val bleAvailable = bleRepository.channelAvailability.value.bleAvailable
                            val hasAnyChannel = when (resolvedDeviceType) {
                                DeviceType.F1 -> bleAvailable
                                DeviceType.F2 -> bleAvailable || status.onlineStatus == 1
                            }
                            // 对齐 F1：只要蓝牙可用，锁/静音/感应优先跟随蓝牙（先实时，其次本机快照），避免被接口旧值覆盖。
                            val preferLocalBleControls =
                                bleAvailable || resolvedDeviceType == DeviceType.F1 || status.onlineStatus != 1
                            var isLocked = status.isLocked
                            var isMuteEnabled = status.isMuteEnabled
                            var isAutoSenseEnabled = status.isAutoSenseEnabled
                            if (preferLocalBleControls) {
                                AppSessionStore.getBleControlSnapshot(selected.device_key)?.let { snap ->
                                    isLocked = snap.isLocked
                                    isMuteEnabled = snap.isMuteEnabled
                                    isAutoSenseEnabled = snap.isAutoSenseEnabled
                                }
                                if (bleAvailable) {
                                    val rt = bleRepository.realtimeState.value
                                    isLocked = rt.isLocked ?: isLocked
                                    isMuteEnabled = rt.isMuteEnabled ?: isMuteEnabled
                                    isAutoSenseEnabled = rt.isAutoSenseEnabled ?: isAutoSenseEnabled
                                }
                            }
                            _uiState.update {
                                it.copy(
                                    deviceKey           = selected.device_key,
                                    frameNumber        = status.frameNumber,
                                    controlModel       = status.controlModel,
                                    deviceType         = resolvedDeviceType,
                                    historyMileKm      = status.historyMileKm,
                                    sosPhoneMasked     = status.sosPhoneMasked,
                                    isOwner            = status.isOwner,
                                    onlineStatus       = status.onlineStatus,
                                    deviceMacAddress   = status.bluetoothMacAddress,
                                    latitude           = status.latitude,
                                    longitude          = status.longitude,
                                    batteryPercent     = status.batteryPercent,
                                    showLightning      = status.showLightning,
                                    voltageText        = status.voltageText,
                                    signalStrengthLevel = status.signalStrengthLevel,
                                    bleConnected       = status.bleConnected,
                                    bleSystemConnected  = status.bleSystemConnected,
                                    isLocked           = isLocked,
                                    isMuteEnabled      = isMuteEnabled,
                                    isAutoSenseEnabled  = isAutoSenseEnabled,
                                    gpsLocked          = status.gpsLocked,
                                    timeText           = status.timeText,
                                    mileageText        = "${status.mileageKm}KM",
                                    durationText       = if (status.durationMin == 0) "--" else "${status.durationMin}min",
                                    topSpeedText       = "${status.topSpeedKmh}km/h",
                                    avgSpeedText       = "${status.avgSpeedKmh}km/h",
                                    vehicleOptions     = options,
                                    hasAnyChannel      = hasAnyChannel,
                                    navigateToAddVehicle = false,
                                    navigateToM1B       = isM1Model,
                                    isLoading          = false,
                                    currentLocationAddress = "获取地址中",
                                    trackLocationAddress = "获取地址中",
                                )
                            }

                            refreshMapAddresses(
                                deviceKey = selected.device_key,
                                currentLatitude = status.latitude,
                                currentLongitude = status.longitude,
                            )

                            if (status.bluetoothMacAddress.isNotBlank()) {
                                AppSessionStore.setLastBluetoothAddress(status.bluetoothMacAddress)
                            }

                            // 如果蓝牙已连接，不再重复连接
                            val alreadyConnected = bleRepository.channelAvailability.value.bleAvailable
                            if (!isM1Model && !alreadyConnected) {
                                connectBle(status.bluetoothMacAddress, withTip = false)
                            } else {
                                bleDebug("skip connectBle: alreadyConnected=$alreadyConnected")
                            }
                        }
                        is AppResult.Error -> {
                            logger.error(LogEntry("f2_api", "load status failed", mapOf("code" to result.code, "message" to result.message)))
                            _uiState.update { it.copy(vehicleOptions = options, isLoading = false, tipMessage = result.message) }
                        }
                    }
                }
                is AppResult.Error -> {
                    logger.error(LogEntry("f2_api", "load user devices failed", mapOf("code" to deviceResult.code, "message" to deviceResult.message)))
                    _uiState.update { it.copy(isLoading = false, tipMessage = deviceResult.message) }
                }
            }
        }
    }

    /**
     * 切换设备：
     * 1. 断开旧设备的 BLE 连接
     * 2. 更新 deviceKey / deviceType
     * 3. 保存默认设备并重新拉取数据
     */
    private fun switchDevice(deviceKey: String, deviceType: DeviceType) {
        viewModelScope.launch {
            logger.info(LogEntry("device_switch", "Switching device", mapOf(
                "newDeviceKey" to deviceKey,
                "deviceType"   to deviceType.name,
            )))
            bleRepository.disconnect()
            AppSessionStore.setLastDeviceKey(deviceKey)
            when (val setDefault = apiRepository.setDefaultDevice(deviceKey)) {
                is AppResult.Success -> Unit
                is AppResult.Error -> {
                    _uiState.update { it.copy(tipMessage = setDefault.message.ifBlank { "切换车辆失败" }) }
                }
            }

            _uiState.update {
                it.copy(
                    deviceKey       = deviceKey,
                    deviceType      = deviceType,
                    deviceMacAddress = "",
                    hasAnyChannel   = false,
                    bleConnected    = false,
                    bleConnecting   = false,
                    tipMessage      = "车辆切换中...",
                    isLoading       = true,
                )
            }
            AppSessionStore.setLastBluetoothAddress("")

            loadInitialData()
        }
    }

    private fun toggleBleConnection() {
        val state = _uiState.value
        bleDebug("toggleBleConnection click connected=${state.bleConnected} connecting=${state.bleConnecting} mac=${state.deviceMacAddress}")
        if (state.bleConnecting) {
            _uiState.update { it.copy(tipMessage = "蓝牙连接中，请稍候") }
            return
        }
        if (state.bleConnected) {
            viewModelScope.launch {
                bleDebug("toggleBleConnection disconnect flow")
                bleRepository.disconnect()
                _uiState.update { it.copy(bleConnected = false, bleConnecting = false, tipMessage = "蓝牙已断开") }
            }
            return
        }
        connectBle(state.deviceMacAddress, withTip = true)
    }

    private fun connectBle(macAddress: String, withTip: Boolean) {
        val resolvedMac = macAddress.ifBlank { AppSessionStore.getLastBluetoothAddress() }
        bleDebug("connectBle start mac=$macAddress resolved=$resolvedMac withTip=$withTip")
        if (resolvedMac.isBlank()) {
            if (withTip) {
                _uiState.update { it.copy(tipMessage = "未获取到蓝牙地址") }
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    bleConnecting = true,
                    tipMessage = if (withTip) "蓝牙连接中..." else it.tipMessage,
                )
            }
            bleRepository.connectTo(resolvedMac)
            when (val result = bleRepository.ensureConnectedInBackground()) {
                is AppResult.Success -> {
                    bleDebug("connectBle success mac=$resolvedMac")
                    _uiState.update {
                        it.copy(
                            deviceMacAddress = resolvedMac,
                            bleConnecting = false,
                            tipMessage = if (withTip) "蓝牙已连接" else it.tipMessage,
                        )
                    }
                }
                is AppResult.Error -> {
                    bleDebug("connectBle fail mac=$resolvedMac message=${result.message}")
                    _uiState.update {
                        it.copy(
                            bleConnecting = false,
                            tipMessage = if (withTip) result.message.ifBlank { "蓝牙连接失败" } else it.tipMessage,
                        )
                    }
                }
            }
        }
    }

    private fun toggleLock(locked: Boolean) {
        _uiState.update { it.copy(isLocked = locked) }
        persistBleControlSnapshot()
        sendCommand(ControlCommand.ToggleLock(locked))
    }

    private fun toggleMute() {
        val current = _uiState.value.isMuteEnabled
        val next = resolveMuteTarget(current)
        pendingMuteTarget = next
        muteProtectUntilMs = System.currentTimeMillis() + 2500L
        _uiState.update { it.copy(isMuteEnabled = next) }
        persistBleControlSnapshot()
        sendCommand(ControlCommand.ToggleMute(next))
    }

    private fun toggleAutoSense() {
        val next = resolveAutoSenseTarget(_uiState.value.isAutoSenseEnabled)
        val state = _uiState.value
        viewModelScope.launch {
            bleDebug("toggleAutoSense click current=${state.isAutoSenseEnabled} next=$next bleConnected=${state.bleConnected} paired=${bleRepository.isCurrentDevicePaired()}")
            if (next) {
                if (!state.bleConnected) {
                    bleDebug("toggleAutoSense on blocked: ble disconnected")
                    _uiState.update { it.copy(tipMessage = "感应开启需要先连接蓝牙") }
                    return@launch
                }

                _uiState.update { it.copy(isAutoSenseEnabled = true, tipMessage = "正在发送配对指令...") }

                // 1) 先发蓝牙感应开指令（参考 F1 autoOn）
                when (val bleOn = bleRepository.sendCommand(ControlCommand.ToggleAutoSense(true))) {
                    is AppResult.Success -> {
                        bleDebug("autoSense on command sent")
                    }
                    is AppResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isAutoSenseEnabled = false,
                                tipMessage = bleOn.message.ifBlank { "蓝牙发送感应开启失败" }
                            )
                        }
                        persistBleControlSnapshot()
                        return@launch
                    }
                }

                // 2) 再走系统蓝牙配对
                _uiState.update { it.copy(tipMessage = "正在系统配对...") }
                when (val pairRes = bleRepository.ensurePaired()) {
                    is AppResult.Success -> {
                        if (!shouldSyncAutoSenseToApi(state.deviceType)) {
                            val tip = if (pairRes.data) "感应解锁已开启" else "请在系统配对弹窗完成配对"
                            _uiState.update { it.copy(isAutoSenseEnabled = true, tipMessage = tip) }
                            persistBleControlSnapshot()
                            return@launch
                        }

                        // 3) F2 配对成功后，再走 API 结果
                        when (val apiRes = apiRepository.toggleAutoSense(state.deviceKey, true)) {
                            is AppResult.Success -> {
                                _uiState.update { it.copy(isAutoSenseEnabled = true, tipMessage = "感应解锁已开启") }
                                persistBleControlSnapshot()
                            }
                            is AppResult.Error -> {
                                bleRepository.sendCommand(ControlCommand.ToggleAutoSense(false))
                                _uiState.update {
                                    it.copy(
                                        isAutoSenseEnabled = false,
                                        tipMessage = apiRes.message.ifBlank { "感应开启失败，已恢复关闭" }
                                    )
                                }
                                persistBleControlSnapshot()
                            }
                        }
                    }
                    is AppResult.Error -> {
                        bleDebug("autoSense pair failed, rollback off: ${pairRes.message}")
                        bleRepository.sendCommand(ControlCommand.ToggleAutoSense(false))
                        _uiState.update {
                            it.copy(
                                isAutoSenseEnabled = false,
                                tipMessage = pairRes.message.ifBlank { "系统配对失败，已恢复关闭" }
                            )
                        }
                        persistBleControlSnapshot()
                    }
                }
            } else {
                _uiState.update { it.copy(isAutoSenseEnabled = false, tipMessage = "正在关闭感应解锁...") }
                persistBleControlSnapshot()

                val bleOff = bleRepository.sendCommand(ControlCommand.ToggleAutoSense(false))
                when (bleOff) {
                    is AppResult.Success -> {
                        bleDebug("autoSense off command sent")
                    }
                    is AppResult.Error -> {
                        bleDebug("autoSense off command failed: ${bleOff.message}")
                    }
                }

                val unpairRes = bleRepository.removeCurrentPairing()
                when (unpairRes) {
                    is AppResult.Success -> {
                        bleDebug("autoSense off unpair result=${unpairRes.data}")
                    }
                    is AppResult.Error -> {
                        bleDebug("autoSense off unpair failed: ${unpairRes.message}")
                    }
                }

                if (shouldSyncAutoSenseToApi(state.deviceType)) {
                    when (val apiRes = apiRepository.toggleAutoSense(state.deviceKey, false)) {
                        is AppResult.Success -> {
                            _uiState.update { it.copy(isAutoSenseEnabled = false, tipMessage = "感应解锁已关闭") }
                        }
                        is AppResult.Error -> {
                            _uiState.update {
                                it.copy(
                                    isAutoSenseEnabled = false,
                                    tipMessage = apiRes.message.ifBlank { "感应解锁已关闭（云端同步失败）" }
                                )
                            }
                        }
                    }
                } else {
                    _uiState.update { it.copy(isAutoSenseEnabled = false, tipMessage = "感应解锁已关闭") }
                }
                persistBleControlSnapshot()
            }
        }
    }

    /** 从单例 repository 同步当前 BLE 状态到 UI（页面可见时调用） */
    private fun syncBleStateFromRepo() {
        val avail = bleRepository.channelAvailability.value
        val deviceType = _uiState.value.deviceType
        val online = _uiState.value.onlineStatus == 1
        val hasAnyChannel = when (deviceType) {
            DeviceType.F1 -> avail.bleAvailable
            DeviceType.F2 -> avail.bleAvailable || online
        }
        _uiState.update {
            it.copy(
                bleConnected = avail.bleAvailable,
                bleSystemConnected = bleRepository.isCurrentDevicePaired(),
                hasAnyChannel = hasAnyChannel,
            )
        }
        val rt = bleRepository.realtimeState.value
        if (avail.bleAvailable && rt.lastPlainHex.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    isLocked = rt.isLocked ?: it.isLocked,
                    isMuteEnabled = rt.isMuteEnabled ?: it.isMuteEnabled,
                    isAutoSenseEnabled = rt.isAutoSenseEnabled ?: it.isAutoSenseEnabled,
                )
            }
            persistBleControlSnapshot()
        }
        bleDebug("syncBleStateFromRepo bleAvailable=${avail.bleAvailable} hasAnyChannel=$hasAnyChannel")
    }

    private fun persistBleControlSnapshot() {
        val u = _uiState.value
        if (u.deviceKey.isBlank()) return
        AppSessionStore.putBleControlSnapshot(
            u.deviceKey,
            u.isLocked,
            u.isMuteEnabled,
            u.isAutoSenseEnabled,
        )
    }

    private fun sendCommand(command: ControlCommand) {
        val uiStateSnapshot = _uiState.value
        val deviceType = uiStateSnapshot.deviceType
        val deviceKey = uiStateSnapshot.deviceKey
        val networkOnline = uiStateSnapshot.onlineStatus == 1
        viewModelScope.launch {
            when (val result = sendControlCommandUseCase(command, deviceType, deviceKey, networkOnline)) {
                is AppResult.Success -> Unit
                is AppResult.Error -> {
                    logger.error(
                        LogEntry(
                            tag = "f2_control",
                            message = "Command failed",
                            payload = mapOf("code" to result.code, "message" to result.message)
                        )
                    )
                }
            }
        }
    }

    private fun refreshMapAddresses(deviceKey: String, currentLatitude: Double, currentLongitude: Double) {
        if (deviceKey.isBlank()) return
        viewModelScope.launch {
            var currentAddress = "当前位置"
            if (currentLatitude != 0.0 && currentLongitude != 0.0) {
                currentAddress = when (val currentRes = apiRepository.fetchLocationAddress(currentLatitude, currentLongitude)) {
                    is AppResult.Success -> currentRes.data
                    is AppResult.Error -> "当前位置"
                }
            }

            var trackAddress = "暂无轨迹"
            var trackLatitude = 0.0
            var trackLongitude = 0.0
            when (val trackRes = apiRepository.fetchDeviceTrackPoints(deviceKey, 20)) {
                is AppResult.Success -> {
                    val endPoint = resolveTrackPreviewPoint(trackRes.data)
                    if (endPoint != null) {
                        trackLatitude = endPoint.latitude
                        trackLongitude = endPoint.longitude
                        trackAddress = when (val addrRes = apiRepository.fetchLocationAddress(endPoint.latitude, endPoint.longitude)) {
                            is AppResult.Success -> addrRes.data
                            is AppResult.Error -> "历史轨迹"
                        }
                    }
                }
                is AppResult.Error -> {
                    trackAddress = "暂无轨迹"
                }
            }

            _uiState.update {
                it.copy(
                    currentLocationAddress = currentAddress,
                    trackLocationAddress = trackAddress,
                    trackLatitude = trackLatitude,
                    trackLongitude = trackLongitude,
                )
            }
        }
    }
}

internal fun resolveTrackPreviewPoint(points: List<xiaochao.com.data.api.TrackPoint>): xiaochao.com.data.api.TrackPoint? {
    return points.lastOrNull { it.latitude != 0.0 && it.longitude != 0.0 }
}

internal fun resolveMuteTarget(current: Boolean): Boolean = !current

internal fun resolveAutoSenseTarget(current: Boolean): Boolean = !current

internal fun shouldApplyRealtimeMute(
    pendingTarget: Boolean?,
    protectUntilMs: Long,
    nowMs: Long,
    incoming: Boolean,
): Boolean {
    if (pendingTarget == null) return true
    if (nowMs > protectUntilMs) return true
    return incoming == pendingTarget
}

internal fun shouldSyncAutoSenseToApi(deviceType: DeviceType): Boolean {
    return deviceType != DeviceType.F1
}
