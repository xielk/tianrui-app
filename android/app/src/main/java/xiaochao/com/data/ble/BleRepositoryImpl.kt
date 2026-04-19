package xiaochao.com.data.ble

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import xiaochao.com.core.result.AppResult
import xiaochao.com.data.model.ChannelAvailability
import xiaochao.com.data.model.ControlCommand
import xiaochao.com.data.session.AppSessionStore

class BleRepositoryImpl(
    private val manager: AndroidBleManager,
) : BleRepository {
    private fun debug(message: String) {
        Log.d("BLE-DEBUG", message)
    }

    override val channelAvailability: StateFlow<ChannelAvailability> = manager.availability
    override val realtimeState: StateFlow<BleRealtimeState> = manager.realtimeState

    /**
     * 后台持续重试连接（最多 20 次，每次间隔 2s）。
     * 真实场景：manager.startConnect() 触发 GATT，GATT 回调调用 markBleConnected(true)。
     */
    override suspend fun ensureConnectedInBackground(): AppResult<Unit> {
        val mac = manager.activeMac.ifEmpty { AppSessionStore.getLastBluetoothAddress() }
        if (manager.activeMac.isEmpty() && mac.isNotEmpty()) {
            debug("ensureConnectedInBackground restore mac from storage=$mac")
            manager.startConnect(mac)
        }
        debug("ensureConnectedInBackground start mac=$mac availability=${channelAvailability.value}")
        if (mac.isEmpty()) {
            manager.logPhase("ensure_skip_no_mac")
            debug("ensureConnectedInBackground no mac -> fail")
            return AppResult.Error("NO_MAC", "未配置设备 MAC 地址")
        }
        repeat(20) { attempt ->
            if (channelAvailability.value.bleAvailable) return AppResult.Success(Unit)
            debug("retry connect attempt=${attempt + 1} mac=$mac")
            manager.logPhase("ble_retry attempt=${attempt + 1} mac=$mac")
            manager.startConnect(mac)  // TODO: 替换为真实 GATT 连接
            delay(2_000)
        }
        return if (channelAvailability.value.bleAvailable) {
            debug("ensureConnectedInBackground success after retry mac=$mac")
            AppResult.Success(Unit)
        } else {
            debug("ensureConnectedInBackground exhausted mac=$mac")
            AppResult.Error("BLE_RETRY_EXHAUSTED", "蓝牙重连失败，已达最大重试次数")
        }
    }

    override suspend fun sendCommand(command: ControlCommand): AppResult<Unit> {
        val detail = when (command) {
            is ControlCommand.ToggleLock -> "ToggleLock locked=${command.locked}"
            is ControlCommand.ToggleMute -> "ToggleMute mute=${command.mute}"
            is ControlCommand.ToggleAutoSense -> "ToggleAutoSense enabled=${command.enabled}"
            ControlCommand.FindBike -> "FindBike"
            ControlCommand.SensorLevelLocation -> "SensorLevelLocation"
            is ControlCommand.SetBleDisarmSensorDistance -> "SetBleDisarmSensorDistance level=${command.level0To8}"
            is ControlCommand.SetBleArmSensorDistance -> "SetBleArmSensorDistance level=${command.level0To8}"
            is ControlCommand.SetBleAlarmSensitivity -> "SetBleAlarmSensitivity idx=${command.levelIndex0To2}"
            is ControlCommand.SetBleAutoShutdown -> "SetBleAutoShutdown min=${command.minutes}"
            is ControlCommand.SetBleAlarmSoundVolume -> "SetBleAlarmSoundVolume vol=${command.volume0To10}"
            is ControlCommand.MoveMode -> "MoveMode enabled=${command.enabled}"
            ControlCommand.MoveForward -> "MoveForward"
            ControlCommand.MoveBackward -> "MoveBackward"
        }
        debug("sendCommand command=$detail ble=${channelAvailability.value.bleAvailable}")
        return if (channelAvailability.value.bleAvailable) {
            manager.sendControlCommand(command)
        } else {
            AppResult.Error("BLE_DISCONNECTED", "蓝牙未连接")
        }
    }

    /** 主动断开：清空连接状态，切换设备前调用 */
    override suspend fun disconnect(): AppResult<Unit> {
        debug("disconnect CALLED - thread=${Thread.currentThread().name}")
        Exception("BLE_DISCONNECT_STACK").printStackTrace()
        manager.disconnect()
        return AppResult.Success(Unit)
    }

    /** 连接新设备：记录 MAC 并触发连接（真实 GATT 在 manager 内完成） */
    override suspend fun connectTo(macAddress: String): AppResult<Unit> {
        debug("connectTo mac=$macAddress")
        manager.startConnect(macAddress)
        return AppResult.Success(Unit)
    }

    override suspend fun ensurePaired(): AppResult<Boolean> {
        debug("ensurePaired called activeMac=${manager.activeMac}")
        return manager.ensurePaired()
    }

    override fun isCurrentDevicePaired(): Boolean {
        return manager.isCurrentDevicePaired()
    }

    override suspend fun removeCurrentPairing(): AppResult<Boolean> {
        debug("removeCurrentPairing called activeMac=${manager.activeMac}")
        return manager.removeCurrentPairing()
    }
}
