package xiaochao.com.data.ble

data class BleRealtimeState(
    val lastPlainHex: String = "",
    val isLocked: Boolean? = null,
    val isMuteEnabled: Boolean? = null,
    val isAutoSenseEnabled: Boolean? = null,
)
