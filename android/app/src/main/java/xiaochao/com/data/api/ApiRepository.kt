package xiaochao.com.data.api

import xiaochao.com.core.result.AppResult
import xiaochao.com.data.model.VehicleStatus
import xiaochao.com.data.network.DeviceInfoDto

data class DeviceSettingsData(
    val sensitivityDistance: Int,
    val sensitivityDistance2: Int,
    val alarmSensitivity: String,
    val autoShutdownTime: Int,
    val gpsLock: Int,
)

data class SharedUserItem(
    val memberId: String,
    val phone: String,
    val isOwner: Boolean,
)

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
)

interface ApiRepository {
    suspend fun fetchVehicleStatus(deviceKey: String): AppResult<VehicleStatus>
    suspend fun fetchDeviceInfo(deviceKey: String): AppResult<DeviceInfoDto>
    suspend fun fetchDeviceTrack(deviceKey: String, interval: Int = 20): AppResult<Boolean>
    suspend fun fetchDeviceTrackPoints(deviceKey: String, interval: Int = 20): AppResult<List<TrackPoint>>
    suspend fun fetchLocationAddress(latitude: Double, longitude: Double): AppResult<String>
    suspend fun fetchUserProfile(): AppResult<String>
    suspend fun bindDevice(deviceKey: String): AppResult<Unit>
    suspend fun unbindDevice(deviceKey: String): AppResult<Unit>
    suspend fun setDefaultDevice(deviceKey: String): AppResult<Unit>
    suspend fun setSosPhone(deviceKey: String, phone: String): AppResult<String>
    suspend fun toggleLock(deviceKey: String, locked: Boolean): AppResult<Unit>
    suspend fun toggleMute(deviceKey: String, mute: Boolean): AppResult<Unit>
    suspend fun toggleAutoSense(deviceKey: String, enabled: Boolean): AppResult<Unit>
    suspend fun findBike(deviceKey: String): AppResult<Unit>
    suspend fun fetchDeviceSettings(deviceKey: String): AppResult<DeviceSettingsData>
    suspend fun updateDeviceSettings(deviceKey: String, settings: DeviceSettingsData): AppResult<Unit>
    suspend fun setGpsLock(deviceKey: String, gpsLock: Int): AppResult<Unit>
    suspend fun fetchUserDevices(): AppResult<List<DeviceDto>>
    suspend fun fetchSharedUsers(deviceKey: String): AppResult<List<SharedUserItem>>
    suspend fun shareDevice(deviceKey: String, phone: String): AppResult<Unit>
    suspend fun removeSharedUser(deviceKey: String, memberId: String): AppResult<Unit>
    suspend fun changeOwner(deviceKey: String, newOwnerPhone: String): AppResult<Unit>
    suspend fun upsertCid(cid: String): AppResult<Unit>
    suspend fun upsertPushDevice(token: String): AppResult<Unit>
    suspend fun createBleLog(deviceKey: String, content: String): AppResult<Unit>
}
