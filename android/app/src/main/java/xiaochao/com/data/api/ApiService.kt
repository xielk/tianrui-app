package xiaochao.com.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.GET

interface ApiService {
    @GET("vehicle/status")
    suspend fun getVehicleStatus(): VehicleStatusDto

    @GET("user/devices")
    suspend fun getUserDevices(): List<DeviceDto>
}

@Serializable
data class DeviceDto(
    val short_name: String,
    val device_key: String
)

@Serializable
data class VehicleStatusDto(
    val frameNumber: String,
    val historyMileKm: Int,
    val sosPhoneMasked: String,
    val batteryPercent: Int,
    val voltageText: String,
    val signalStrengthLevel: Int,
    val bleConnected: Boolean,
    val bleSystemConnected: Boolean,
    val isLocked: Boolean,
    val isMuteEnabled: Boolean,
    val isAutoSenseEnabled: Boolean,
    val gpsLocked: Boolean,
    val timeText: String,
    val mileageKm: Int,
    val durationMin: Int,
    val topSpeedKmh: Int,
    val avgSpeedKmh: Int,
)
