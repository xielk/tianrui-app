package xiaochao.com.domain.control

import xiaochao.com.data.model.ChannelAvailability
import xiaochao.com.data.model.CommandChannel
import xiaochao.com.data.model.DeviceType

class ControlChannelPolicy {
    /**
     * 根据设备类型和当前通道可用性，选择发送通道。
     *
     * - F1（纯蓝牙）：BLE 可用则走 BLE，否则 NONE（不降级到 4G）
     * - F2（双通道）：BLE 优先，不可用时降级 4G，两者均无则 NONE
     */
    fun select(availability: ChannelAvailability, deviceType: DeviceType): CommandChannel {
        return when (deviceType) {
            DeviceType.F1 -> if (availability.bleAvailable) CommandChannel.BLE else CommandChannel.NONE
            DeviceType.F2 -> when {
                availability.bleAvailable    -> CommandChannel.BLE
                availability.networkAvailable -> CommandChannel.CELLULAR_4G
                else                          -> CommandChannel.NONE
            }
        }
    }
}
