package xiaochao.com.feature.f2.ui

import android.Manifest
import android.os.Build
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlPermissionsTest {

    @Test
    fun `f2 on android 12 uses bluetooth permissions`() {
        val permissions = requiredControlPermissions(Build.VERSION_CODES.S, isF1Layout = false).toSet()
        assertTrue(permissions.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(permissions.contains(Manifest.permission.BLUETOOTH_CONNECT))
    }

    @Test
    fun `f1 on android 12 adds location permission`() {
        val permissions = requiredControlPermissions(Build.VERSION_CODES.S, isF1Layout = true).toSet()
        assertTrue(permissions.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(permissions.contains(Manifest.permission.BLUETOOTH_CONNECT))
        assertTrue(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    @Test
    fun `f2 on android 11 uses location permission`() {
        val permissions = requiredControlPermissions(Build.VERSION_CODES.R, isF1Layout = false).toSet()
        assertTrue(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
    }
}
