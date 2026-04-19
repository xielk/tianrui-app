package xiaochao.com.data.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class ControlCommandEncodingTest {

    @Test
    fun `toggle mute true uses alarm sound level0`() {
        val (param, value) = resolveToggleMuteParamValue(mute = true)
        assertEquals("0F", param)
        assertEquals("00", value)
    }

    @Test
    fun `toggle mute false uses alarm sound level10`() {
        val (param, value) = resolveToggleMuteParamValue(mute = false)
        assertEquals("0F", param)
        assertEquals("0A", value)
    }

    @Test
    fun `move mode values map to protocol`() {
        assertEquals("01", resolveMoveModeValue(enabled = true))
        assertEquals("00", resolveMoveModeValue(enabled = false))
        assertEquals("01", resolveMoveStepValue())
    }
}
