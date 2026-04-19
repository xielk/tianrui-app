package xiaochao.com.domain.control

import android.util.Log
import xiaochao.com.core.log.AppLogger
import xiaochao.com.core.log.LogEntry
import xiaochao.com.core.result.AppResult
import xiaochao.com.data.api.ApiRepository
import xiaochao.com.data.ble.BleRepository
import xiaochao.com.data.model.ChannelAvailability
import xiaochao.com.data.model.CommandChannel
import xiaochao.com.data.model.ControlCommand
import xiaochao.com.data.model.DeviceType
import xiaochao.com.data.session.AppSessionStore

class SendControlCommandUseCase(
    private val policy: ControlChannelPolicy,
    private val bleRepository: BleRepository,
    private val apiRepository: ApiRepository,
    private val logger: AppLogger,
) {
    /**
     * 发送控制指令，通道策略由 [deviceType] 决定：
     *
     * - **F1**：BLE 不可用时直接返回 NO_CHANNEL，不走 4G。
     * - **F2**：BLE 可用时主发 BLE，并同时 fire-and-forget 发 API（对齐 F2.vue 行为）；
     *           BLE 不可用时降级纯 API；两者皆无时后台触发重连并返回 NO_CHANNEL。
     */
    suspend operator fun invoke(
        command: ControlCommand,
        deviceType: DeviceType,
        deviceKey: String,
        networkOnline: Boolean,
    ): AppResult<Unit> {
        val rawAvailability = bleRepository.channelAvailability.value
        val sessionReady = AppSessionStore.getUuid().isNotEmpty()
        var availability = if (deviceType == DeviceType.F2) {
            rawAvailability.copy(networkAvailable = networkOnline)
        } else {
            rawAvailability
        }
        var selected = policy.select(availability, deviceType)

        // F2: 如果系统已配对但通道层误判为未连接，先触发重连再二次选路。
        if (deviceType == DeviceType.F2 && !availability.bleAvailable && bleRepository.isCurrentDevicePaired()) {
            logger.info(
                LogEntry(
                    tag = "command_route",
                    message = "BLE unavailable but paired, reconnect before routing",
                    payload = mapOf(
                        "command" to command.javaClass.simpleName,
                        "deviceKey" to deviceKey,
                        "networkOnline" to networkOnline,
                    )
                )
            )
            val reconnectResult = bleRepository.ensureConnectedInBackground()
            availability = if (deviceType == DeviceType.F2) {
                bleRepository.channelAvailability.value.copy(networkAvailable = networkOnline)
            } else {
                bleRepository.channelAvailability.value
            }
            selected = policy.select(availability, deviceType)
            logger.info(
                LogEntry(
                    tag = "command_route",
                    message = "Reconnect probe completed",
                    payload = mapOf(
                        "reconnectResult" to reconnectResult.javaClass.simpleName,
                        "selectedAfterReconnect" to selected.name,
                        "bleAvailable" to availability.bleAvailable,
                        "networkAvailable" to availability.networkAvailable,
                    )
                )
            )
        }

        logger.info(
            LogEntry(
                tag = "command_route",
                message = "Select channel for command",
                payload = mapOf(
                    "command"       to command.javaClass.simpleName,
                    "deviceType"    to deviceType.name,
                    "selectedChannel" to selected.name,
                    "deviceKey" to deviceKey,
                    "bleAvailable"  to availability.bleAvailable,
                    "networkAvailable" to availability.networkAvailable,
                    "sessionReady" to sessionReady,
                    "networkOnline" to networkOnline,
                )
            )
        )

        return when (selected) {
            CommandChannel.BLE -> {
                logger.info(
                    LogEntry(
                        tag = "command_route",
                        message = "Dispatch command via BLE",
                        payload = mapOf("command" to command.javaClass.simpleName, "deviceKey" to deviceKey)
                    )
                )
                val bleResult = bleRepository.sendCommand(command)
                if (deviceType == DeviceType.F2 && bleResult is AppResult.Success) {
                    if (command is ControlCommand.ToggleAutoSense && !command.enabled) {
                        Log.d("BLE-DEBUG", "autoSense off: begin unpair flow")
                        val unpairResult = bleRepository.removeCurrentPairing()
                        when (unpairResult) {
                            is AppResult.Success -> {
                                Log.d("BLE-DEBUG", "autoSense off: unpair result success changed=${unpairResult.data}")
                            }
                            is AppResult.Error -> {
                                Log.d("BLE-DEBUG", "autoSense off: unpair failed code=${unpairResult.code} msg=${unpairResult.message}")
                                logger.error(
                                    LogEntry(
                                        tag = "command_route",
                                        message = "BLE auto-sense off unpair failed",
                                        payload = mapOf(
                                            "code" to unpairResult.code,
                                            "message" to unpairResult.message,
                                        )
                                    )
                                )
                                return AppResult.Error(unpairResult.code, unpairResult.message)
                            }
                        }
                    }
                }

                if (deviceType == DeviceType.F2 && bleResult is AppResult.Success && shouldBackfillApi(command)) {
                    val apiResult = sendViaApi(command, deviceKey)
                    if (apiResult is AppResult.Error) {
                        logger.error(
                            LogEntry(
                                tag = "command_route",
                                message = "BLE success but API backfill failed",
                                payload = mapOf(
                                    "command" to command.javaClass.simpleName,
                                    "code" to apiResult.code,
                                    "message" to apiResult.message,
                                )
                            )
                        )
                    }
                }
                bleResult
            }

            CommandChannel.CELLULAR_4G -> {
                logger.info(
                    LogEntry(
                        tag = "command_route",
                        message = "Dispatch command via 4G",
                        payload = mapOf("command" to command.javaClass.simpleName, "deviceKey" to deviceKey)
                    )
                )
                sendViaApi(command, deviceKey)
            }  // 仅 F2 会到达这里

            CommandChannel.NONE -> {
                // 触发后台重连（F1/F2 均执行），UI 侧已通过 hasAnyChannel 置灰
                bleRepository.ensureConnectedInBackground()
                AppResult.Error("NO_CHANNEL", "暂无可用通道")
            }
        }
    }

    private suspend fun sendViaApi(command: ControlCommand, deviceKey: String): AppResult<Unit> {
        return when (command) {
            is ControlCommand.ToggleLock      -> apiRepository.toggleLock(deviceKey, command.locked)
            is ControlCommand.ToggleMute      -> apiRepository.toggleMute(deviceKey, command.mute)
            is ControlCommand.ToggleAutoSense -> apiRepository.toggleAutoSense(deviceKey, command.enabled)
            ControlCommand.FindBike           -> apiRepository.findBike(deviceKey)
            ControlCommand.SensorLevelLocation -> AppResult.Success(Unit)
            is ControlCommand.SetBleDisarmSensorDistance,
            is ControlCommand.SetBleArmSensorDistance,
            is ControlCommand.SetBleAlarmSensitivity,
            is ControlCommand.SetBleAutoShutdown,
            is ControlCommand.SetBleAlarmSoundVolume,
            is ControlCommand.MoveMode,
            ControlCommand.MoveForward,
            ControlCommand.MoveBackward -> AppResult.Success(Unit)
        }
    }

    private fun shouldBackfillApi(command: ControlCommand): Boolean {
        return when (command) {
            is ControlCommand.ToggleMute -> true
            is ControlCommand.ToggleAutoSense -> true
            ControlCommand.FindBike -> true
            is ControlCommand.ToggleLock -> false
            ControlCommand.SensorLevelLocation -> false
            is ControlCommand.SetBleDisarmSensorDistance,
            is ControlCommand.SetBleArmSensorDistance,
            is ControlCommand.SetBleAlarmSensitivity,
            is ControlCommand.SetBleAutoShutdown,
            is ControlCommand.SetBleAlarmSoundVolume,
            is ControlCommand.MoveMode,
            ControlCommand.MoveForward,
            ControlCommand.MoveBackward -> false
        }
    }
}
