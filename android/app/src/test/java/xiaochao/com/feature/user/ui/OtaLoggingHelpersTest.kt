package xiaochao.com.feature.user.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OtaLoggingHelpersTest {

    @Test
    fun `opcode names are readable`() {
        assertEquals("GET_STR_BASE", otaOpcodeName(1))
        assertEquals("PAGE_ERASE", otaOpcodeName(3))
        assertEquals("WRITE_DATA", otaOpcodeName(5))
        assertEquals("REBOOT", otaOpcodeName(9))
        assertEquals("UNKNOWN(66)", otaOpcodeName(66))
    }

    @Test
    fun `hex preview trims and truncates long payload`() {
        val preview = otaHexPreview(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05), 3)
        assertEquals("01 02 03...(+2)", preview)
    }
}
