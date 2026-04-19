package xiaochao.com.domain.control

import org.junit.Assert.assertEquals
import org.junit.Test
import xiaochao.com.data.model.ChannelAvailability
import xiaochao.com.data.model.CommandChannel
import xiaochao.com.data.model.DeviceType

class ControlChannelPolicyTest {
    private val policy = ControlChannelPolicy()

    @Test
    fun select_prefersBleWhenBothAvailable() {
        val selected = policy.select(
            ChannelAvailability(bleAvailable = true, networkAvailable = true),
            DeviceType.F2,
        )
        assertEquals(CommandChannel.BLE, selected)
    }

    @Test
    fun select_uses4gWhenBleUnavailable() {
        val selected = policy.select(
            ChannelAvailability(bleAvailable = false, networkAvailable = true),
            DeviceType.F2,
        )
        assertEquals(CommandChannel.CELLULAR_4G, selected)
    }

    @Test
    fun select_noneWhenNoChannels() {
        val selected = policy.select(
            ChannelAvailability(bleAvailable = false, networkAvailable = false),
            DeviceType.F2,
        )
        assertEquals(CommandChannel.NONE, selected)
    }

    @Test
    fun select_f1OnlyUsesBle() {
        val selected = policy.select(
            ChannelAvailability(bleAvailable = false, networkAvailable = true),
            DeviceType.F1,
        )
        assertEquals(CommandChannel.NONE, selected)
    }
}
