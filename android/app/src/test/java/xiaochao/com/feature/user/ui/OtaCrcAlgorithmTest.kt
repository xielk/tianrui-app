package xiaochao.com.feature.user.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OtaCrcAlgorithmTest {

    @Test
    fun `crc is zero when payload no more than 256 bytes`() {
        val data = ByteArray(256) { it.toByte() }
        assertEquals(0, calcFr8010LegacyCrc(data))
    }

    @Test
    fun `crc matches legacy fr8010 algorithm`() {
        val data = ByteArray(700) { ((it * 31 + 7) and 0xFF).toByte() }
        val expected = referenceFr8010Crc(data)
        assertEquals(expected, calcFr8010LegacyCrc(data))
    }

    private fun referenceFr8010Crc(bytes: ByteArray): Int {
        if (bytes.size <= 256) return 0
        val table = IntArray(256)
        for (i in 0 until 256) {
            var c = i
            repeat(8) {
                c = if ((c and 1) != 0) {
                    0xEDB88320.toInt() xor (c ushr 1)
                } else {
                    c ushr 1
                }
            }
            table[i] = c
        }

        var crc = 0
        var index = 256
        while (index < bytes.size) {
            val high = crc / 256
            crc = crc shl 8
            crc = crc xor table[(high xor (bytes[index].toInt() and 0xFF)) and 0xFF]
            index++
        }
        return crc
    }
}
