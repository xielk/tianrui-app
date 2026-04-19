package xiaochao.com.data.ble

import kotlinx.coroutines.flow.StateFlow
import xiaochao.com.core.result.AppResult
import xiaochao.com.data.model.ChannelAvailability
import xiaochao.com.data.model.ControlCommand

interface BleRepository {
    val channelAvailability: StateFlow<ChannelAvailability>
    val realtimeState: StateFlow<BleRealtimeState>
    /** 在后台持续尝试连接当前设备 */
    suspend fun ensureConnectedInBackground(): AppResult<Unit>
    /** 发送控制指令 */
    suspend fun sendCommand(command: ControlCommand): AppResult<Unit>
    /** 主动断开当前 GATT 连接（切换设备前调用） */
    suspend fun disconnect(): AppResult<Unit>
    /** 连接指定 MAC 地址的设备 */
    suspend fun connectTo(macAddress: String): AppResult<Unit>
    /** 蓝牙配对（用于感应功能） */
    suspend fun ensurePaired(): AppResult<Boolean>
    /** 当前设备是否已系统配对 */
    fun isCurrentDevicePaired(): Boolean
    /** 解除当前设备系统配对 */
    suspend fun removeCurrentPairing(): AppResult<Boolean>
}
