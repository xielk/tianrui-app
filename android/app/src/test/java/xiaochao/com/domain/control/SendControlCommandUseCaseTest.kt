package xiaochao.com.domain.control

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xiaochao.com.core.log.AppLogger
import xiaochao.com.core.result.AppResult
import xiaochao.com.data.api.ApiRepository
import xiaochao.com.data.api.DeviceDto
import xiaochao.com.data.api.DeviceSettingsData
import xiaochao.com.data.api.SharedUserItem
import xiaochao.com.data.api.TrackPoint
import xiaochao.com.data.ble.BleRealtimeState
import xiaochao.com.data.ble.BleRepository
import xiaochao.com.data.model.ChannelAvailability
import xiaochao.com.data.model.ControlCommand
import xiaochao.com.data.model.DeviceType
import xiaochao.com.data.model.VehicleStatus
import xiaochao.com.data.network.DeviceInfoDto

class SendControlCommandUseCaseTest {
    @Test
    fun invoke_f2WhenBleAvailable_prefersBle() = runBlocking {
        val ble = FakeBleRepository(
            availability = ChannelAvailability(bleAvailable = true, networkAvailable = true),
            paired = true,
        )
        val api = FakeApiRepository()
        val useCase = SendControlCommandUseCase(ControlChannelPolicy(), ble, api, AppLogger())

        val result = useCase(
            command = ControlCommand.ToggleLock(locked = true),
            deviceType = DeviceType.F2,
            deviceKey = "dev-1",
            networkOnline = true,
        )

        assertTrue(result is AppResult.Success)
        assertEquals(1, ble.sendCount)
        assertEquals(0, api.toggleLockCount)
    }

    @Test
    fun invoke_f2BleNotReadyButPaired_reconnectsThenUsesBle() = runBlocking {
        val ble = FakeBleRepository(
            availability = ChannelAvailability(bleAvailable = false, networkAvailable = true),
            paired = true,
        )
        ble.ensureHook = {
            ble.availabilityFlow.value = ble.availabilityFlow.value.copy(bleAvailable = true)
            AppResult.Success(Unit)
        }
        val api = FakeApiRepository()
        val useCase = SendControlCommandUseCase(ControlChannelPolicy(), ble, api, AppLogger())

        val result = useCase(
            command = ControlCommand.ToggleLock(locked = true),
            deviceType = DeviceType.F2,
            deviceKey = "dev-1",
            networkOnline = true,
        )

        assertTrue(result is AppResult.Success)
        assertEquals(1, ble.ensureCount)
        assertEquals(1, ble.sendCount)
        assertEquals(0, api.toggleLockCount)
    }
}

private class FakeBleRepository(
    availability: ChannelAvailability,
    private val paired: Boolean,
) : BleRepository {
    val availabilityFlow = MutableStateFlow(availability)
    override val channelAvailability: StateFlow<ChannelAvailability> = availabilityFlow
    override val realtimeState: StateFlow<BleRealtimeState> = MutableStateFlow(BleRealtimeState())

    var sendCount = 0
    var ensureCount = 0
    var ensureHook: (suspend () -> AppResult<Unit>)? = null

    override suspend fun ensureConnectedInBackground(): AppResult<Unit> {
        ensureCount += 1
        return ensureHook?.invoke() ?: AppResult.Error("BLE_RETRY_EXHAUSTED", "retry fail")
    }

    override suspend fun sendCommand(command: ControlCommand): AppResult<Unit> {
        sendCount += 1
        return if (availabilityFlow.value.bleAvailable) {
            AppResult.Success(Unit)
        } else {
            AppResult.Error("BLE_DISCONNECTED", "蓝牙未连接")
        }
    }

    override suspend fun disconnect(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun connectTo(macAddress: String): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun ensurePaired(): AppResult<Boolean> = AppResult.Success(paired)

    override fun isCurrentDevicePaired(): Boolean = paired

    override suspend fun removeCurrentPairing(): AppResult<Boolean> = AppResult.Success(true)
}

private class FakeApiRepository : ApiRepository {
    var toggleLockCount = 0

    override suspend fun toggleLock(deviceKey: String, locked: Boolean): AppResult<Unit> {
        toggleLockCount += 1
        return AppResult.Success(Unit)
    }

    override suspend fun fetchVehicleStatus(deviceKey: String): AppResult<VehicleStatus> = unsupported()
    override suspend fun fetchDeviceInfo(deviceKey: String): AppResult<DeviceInfoDto> = unsupported()
    override suspend fun fetchDeviceTrack(deviceKey: String, interval: Int): AppResult<Boolean> = unsupported()
    override suspend fun fetchDeviceTrackPoints(deviceKey: String, interval: Int): AppResult<List<TrackPoint>> = unsupported()
    override suspend fun fetchLocationAddress(latitude: Double, longitude: Double): AppResult<String> = unsupported()
    override suspend fun fetchUserProfile(): AppResult<String> = unsupported()
    override suspend fun bindDevice(deviceKey: String): AppResult<Unit> = unsupported()
    override suspend fun unbindDevice(deviceKey: String): AppResult<Unit> = unsupported()
    override suspend fun setDefaultDevice(deviceKey: String): AppResult<Unit> = unsupported()
    override suspend fun setSosPhone(deviceKey: String, phone: String): AppResult<String> = unsupported()
    override suspend fun toggleMute(deviceKey: String, mute: Boolean): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun toggleAutoSense(deviceKey: String, enabled: Boolean): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun findBike(deviceKey: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun fetchDeviceSettings(deviceKey: String): AppResult<DeviceSettingsData> = unsupported()
    override suspend fun updateDeviceSettings(deviceKey: String, settings: DeviceSettingsData): AppResult<Unit> = unsupported()
    override suspend fun setGpsLock(deviceKey: String, gpsLock: Int): AppResult<Unit> = unsupported()
    override suspend fun fetchUserDevices(): AppResult<List<DeviceDto>> = unsupported()
    override suspend fun fetchSharedUsers(deviceKey: String): AppResult<List<SharedUserItem>> = unsupported()
    override suspend fun shareDevice(deviceKey: String, phone: String): AppResult<Unit> = unsupported()
    override suspend fun removeSharedUser(deviceKey: String, memberId: String): AppResult<Unit> = unsupported()
    override suspend fun changeOwner(deviceKey: String, newOwnerPhone: String): AppResult<Unit> = unsupported()
    override suspend fun upsertCid(cid: String): AppResult<Unit> = unsupported()
    override suspend fun createBleLog(deviceKey: String, content: String): AppResult<Unit> = unsupported()

    private fun <T> unsupported(): AppResult<T> {
        throw UnsupportedOperationException("Not needed in this test")
    }
}
