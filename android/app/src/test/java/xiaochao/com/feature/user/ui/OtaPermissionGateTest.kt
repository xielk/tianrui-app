package xiaochao.com.feature.user.ui

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaPermissionGateTest {

    @Test
    fun `bluetooth connect permission is not required before android 12`() {
        assertFalse(shouldCheckBluetoothConnectPermission(Build.VERSION_CODES.R))
    }

    @Test
    fun `bluetooth connect permission is required on android 12 and above`() {
        assertTrue(shouldCheckBluetoothConnectPermission(Build.VERSION_CODES.S))
        assertTrue(shouldCheckBluetoothConnectPermission(Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
    }
}
