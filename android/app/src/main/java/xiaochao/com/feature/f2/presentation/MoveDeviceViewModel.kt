package xiaochao.com.feature.f2.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xiaochao.com.core.log.AppLogger
import xiaochao.com.core.result.AppResult
import xiaochao.com.data.ble.AndroidBleManager
import xiaochao.com.data.ble.BleRepository
import xiaochao.com.data.ble.BleRepositoryImpl
import xiaochao.com.data.model.ControlCommand
import xiaochao.com.data.session.AppSessionStore

data class MoveDeviceUiState(
    val modeEnabled: Boolean = false,
    val bleConnected: Boolean = false,
    val bleConnecting: Boolean = false,
    val bleSystemConnected: Boolean = false,
    val signalStrengthLevel: Int = 0,
    val tipMessage: String? = null,
)

private enum class MoveDirection {
    FORWARD,
    BACKWARD,
}

class MoveDeviceViewModel(
    private val bleRepository: BleRepository = BleRepositoryImpl(AndroidBleManager.getInstance(AppLogger())),
) : ViewModel() {
    private val _uiState = MutableStateFlow(MoveDeviceUiState())
    val uiState: StateFlow<MoveDeviceUiState> = _uiState.asStateFlow()
    private val _vibrationEvents = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val vibrationEvents = _vibrationEvents.asSharedFlow()

    private var moveJob: Job? = null
    private var holdJob: Job? = null
    private var idleCloseJob: Job? = null
    private var activeDirection: MoveDirection? = null

    init {
        viewModelScope.launch {
            bleRepository.channelAvailability.collect { avail ->
                _uiState.update {
                    it.copy(
                        bleConnected = avail.bleAvailable,
                        bleSystemConnected = bleRepository.isCurrentDevicePaired(),
                    )
                }
            }
        }
    }

    fun toggleMoveMode() {
        viewModelScope.launch {
            val current = _uiState.value
            if (!current.bleConnected) {
                tip("请先连接蓝牙")
                return@launch
            }
            val next = !current.modeEnabled
            moveLog("toggle mode to=$next")
            when (val result = bleRepository.sendCommand(ControlCommand.MoveMode(next))) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(modeEnabled = next, tipMessage = if (next) "已开启挪车模式" else "已关闭挪车模式") }
                    if (next) {
                        _vibrationEvents.tryEmit(30)
                        resetIdleAutoCloseTimer()
                    }
                    if (!next) {
                        stopMove()
                        cancelIdleAutoCloseTimer()
                    }
                }

                is AppResult.Error -> {
                    moveLog("toggle mode failed msg=${result.message}")
                    tip(result.message.ifBlank { "挪车模式设置失败" })
                }
            }
        }
    }

    fun toggleBleConnection() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.bleConnecting) return@launch
            if (state.bleConnected) {
                _uiState.update { it.copy(bleConnecting = true) }
                moveLog("disconnect ble")
                bleRepository.disconnect()
                _uiState.update { it.copy(bleConnecting = false, bleConnected = false) }
                return@launch
            }

            val mac = AppSessionStore.getLastBluetoothAddress()
            if (mac.isBlank()) {
                tip("未获取到蓝牙地址")
                return@launch
            }
            _uiState.update { it.copy(bleConnecting = true) }
            moveLog("connect ble mac=$mac")
            bleRepository.connectTo(mac)
            val result = bleRepository.ensureConnectedInBackground()
            _uiState.update { it.copy(bleConnecting = false) }
            if (result is AppResult.Error) {
                tip(result.message.ifBlank { "蓝牙连接失败" })
            }
        }
    }

    fun pressForwardStart() {
        markUserAction()
        requestStartMoveHold(MoveDirection.FORWARD)
    }

    fun pressBackwardStart() {
        markUserAction()
        requestStartMoveHold(MoveDirection.BACKWARD)
    }

    fun pressEnd() {
        markUserAction()
        holdJob?.cancel()
        holdJob = null
        stopMove()
    }

    private fun requestStartMoveHold(direction: MoveDirection) {
        val state = _uiState.value
        if (!state.modeEnabled) {
            tip("请先开启挪车模式")
            return
        }
        if (!state.bleConnected) {
            tip("请先连接蓝牙")
            return
        }
        holdJob?.cancel()
        holdJob = viewModelScope.launch {
            moveLog("hold start direction=$direction")
            delay(800)
            moveLog("hold passed direction=$direction")
            startMoveLoop(direction)
        }
    }

    private fun startMoveLoop(direction: MoveDirection) {
        val state = _uiState.value
        if (!state.modeEnabled) {
            tip("请先开启挪车模式")
            return
        }
        if (!state.bleConnected) {
            tip("请先连接蓝牙")
            return
        }
        if (activeDirection == direction && moveJob?.isActive == true) {
            return
        }

        stopMove()
        activeDirection = direction
        val command = if (direction == MoveDirection.FORWARD) ControlCommand.MoveForward else ControlCommand.MoveBackward
        moveLog("start direction=$direction")
        _vibrationEvents.tryEmit(20)
        moveJob = viewModelScope.launch {
            while (isActive) {
                when (val result = bleRepository.sendCommand(command)) {
                    is AppResult.Success -> {
                        moveLog("send direction=$direction ok")
                        markUserAction()
                    }

                    is AppResult.Error -> {
                        moveLog("send direction=$direction failed msg=${result.message}")
                        tip(result.message.ifBlank { "挪车指令发送失败" })
                        break
                    }
                }
                delay(100)
            }
            moveLog("loop stopped direction=$direction")
            activeDirection = null
        }
    }

    private fun stopMove() {
        holdJob?.cancel()
        holdJob = null
        if (moveJob?.isActive == true) {
            moveLog("stop direction=$activeDirection")
            _vibrationEvents.tryEmit(10)
        }
        moveJob?.cancel()
        moveJob = null
        activeDirection = null
    }

    private fun markUserAction() {
        if (_uiState.value.modeEnabled) {
            resetIdleAutoCloseTimer()
        }
    }

    private fun resetIdleAutoCloseTimer() {
        idleCloseJob?.cancel()
        idleCloseJob = viewModelScope.launch {
            delay(MOVE_DEVICE_IDLE_TIMEOUT_MS)
            autoCloseMoveModeByIdle()
        }
        moveLog("idle timer reset ${MOVE_DEVICE_IDLE_TIMEOUT_MS}ms")
    }

    private fun cancelIdleAutoCloseTimer() {
        idleCloseJob?.cancel()
        idleCloseJob = null
    }

    private suspend fun autoCloseMoveModeByIdle() {
        if (!_uiState.value.modeEnabled) return
        moveLog("idle timeout reached, auto close move mode")
        stopMove()
        val result = bleRepository.sendCommand(ControlCommand.MoveMode(false))
        when (result) {
            is AppResult.Success -> {
                _uiState.update { it.copy(modeEnabled = false, tipMessage = "10秒无操作，已自动关闭挪车模式") }
                moveLog("idle auto close command sent")
            }

            is AppResult.Error -> {
                _uiState.update { it.copy(modeEnabled = false, tipMessage = "10秒无操作，已自动关闭挪车模式") }
                moveLog("idle auto close command failed msg=${result.message}")
            }
        }
        cancelIdleAutoCloseTimer()
    }

    private fun tip(message: String) {
        _uiState.update { it.copy(tipMessage = message) }
    }

    fun consumeTip() {
        _uiState.update { it.copy(tipMessage = null) }
    }

    private fun moveLog(message: String) {
        Log.d("move-device", message)
    }

    override fun onCleared() {
        cancelIdleAutoCloseTimer()
        stopMove()
        super.onCleared()
    }
}

private const val MOVE_DEVICE_IDLE_TIMEOUT_MS = 10_000L
