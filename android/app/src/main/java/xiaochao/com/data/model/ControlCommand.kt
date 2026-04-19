package xiaochao.com.data.model

sealed interface ControlCommand {
    data class ToggleLock(val locked: Boolean) : ControlCommand
    data class ToggleMute(val mute: Boolean) : ControlCommand
    data class ToggleAutoSense(val enabled: Boolean) : ControlCommand
    data object FindBike : ControlCommand
    data object SensorLevelLocation : ControlCommand

    /** 解防感应距离0–8 档，对应 BLE `SENSOR_LEVEL` param 0B（与 F1.vue / commandUtils.js 一致） */
    data class SetBleDisarmSensorDistance(val level0To8: Int) : ControlCommand
    /** 设防感应距离 0–8 档，param 19 */
    data class SetBleArmSensorDistance(val level0To8: Int) : ControlCommand
    /** 报警灵敏度 UI 下标 0=低 1=中 2=高 → 下发01/02/03 */
    data class SetBleAlarmSensitivity(val levelIndex0To2: Int) : ControlCommand
    /** 自动关机：仅允许 0 / 3 / 5 / 10（分钟），param 09 */
    data class SetBleAutoShutdown(val minutes: Int) : ControlCommand

    /** 报警提示音量 0–10，对应 BLE `ALARM_SOUND` param 0F（隐藏功能，仅 BLE 生效） */
    data class SetBleAlarmSoundVolume(val volume0To10: Int) : ControlCommand

    /** 挪车模式开关，param 1C，00=关，01=开 */
    data class MoveMode(val enabled: Boolean) : ControlCommand
    /** 挪车前进，param 1D，01=前进 */
    data object MoveForward : ControlCommand
    /** 挪车后退，param 1E，01=后退 */
    data object MoveBackward : ControlCommand
}
