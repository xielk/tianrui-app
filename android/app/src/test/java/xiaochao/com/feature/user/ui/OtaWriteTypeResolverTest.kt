package xiaochao.com.feature.user.ui

import android.bluetooth.BluetoothGattCharacteristic
import org.junit.Assert.assertEquals
import org.junit.Test

class OtaWriteTypeResolverTest {

    @Test
    fun `prefers no response when characteristic supports it`() {
        val properties = BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or BluetoothGattCharacteristic.PROPERTY_WRITE
        assertEquals(
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
            resolveOtaWriteType(properties)
        )
    }

    @Test
    fun `falls back to default write when no response is unavailable`() {
        val properties = BluetoothGattCharacteristic.PROPERTY_WRITE
        assertEquals(
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            resolveOtaWriteType(properties)
        )
    }
}
