package xiaochao.com.data.model

data class ChannelAvailability(
    val bleAvailable: Boolean,
    val networkAvailable: Boolean,
)

enum class CommandChannel {
    BLE,
    CELLULAR_4G,
    NONE,
}

/** F1 = 纯蓝牙设备；F2 = BLE + 4G 双通道设备 */
enum class DeviceType { F1, F2 }
