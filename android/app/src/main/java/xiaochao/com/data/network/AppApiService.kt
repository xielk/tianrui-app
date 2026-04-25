package xiaochao.com.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.http.*

// ─────────── 通用响应外壳 ───────────
@Serializable
data class ApiResponse<T>(
    val code: Int,
    val message: String = "",
    val data: T? = null
)

// ─────────── 请求体 ───────────
@Serializable
data class DeviceKeyBody(
    @SerialName("device_key") val deviceKey: String
)

@Serializable
data class InductionLockBody(
    @SerialName("device_key") val deviceKey: String,
    @SerialName("induction_lock") val inductionLock: Int
)

@Serializable
data class SilenceLockBody(
    @SerialName("device_key") val deviceKey: String,
    @SerialName("silence_lock") val silenceLock: Int
)

@Serializable
data class SettingBody(
    @SerialName("device_key") val deviceKey: String,
    val content: Map<String, Int>
)

@Serializable
data class SosPhoneBody(
    @SerialName("device_key") val deviceKey: String,
    @SerialName("sos_phone") val sosPhone: String
)

@Serializable
data class BindDeviceBody(
    @SerialName("device_key") val deviceKey: String
)

@Serializable
data class ShareDeviceBody(
    @SerialName("device_key") val deviceKey: String,
    val phone: String? = null,
    @SerialName("share_phone") val sharePhone: String? = null,
)

@Serializable
data class RemoveSharedUserBody(
    @SerialName("device_key") val deviceKey: String,
    @SerialName("member_id") val memberId: JsonElement? = null,
    @SerialName("share_uuid") val shareUuid: String? = null,
)

@Serializable
data class ChangeOwnerBody(
    @SerialName("device_key") val deviceKey: String,
    @SerialName("new_owner_phone") val newOwnerPhone: String,
)

@Serializable
data class UpsertCidBody(
    val cid: String,
    val token: String? = null,
    val platform: String? = null,
    @SerialName("app_instance_id") val appInstanceId: String? = null,
    @SerialName("device_key") val deviceKey: String? = null,
    @SerialName("member_id") val memberId: Int? = null,
)

@Serializable
data class UpsertPushDeviceBody(
    val token: String,
    val platform: String,
    @SerialName("app_instance_id") val appInstanceId: String,
    @SerialName("device_model") val deviceModel: String,
    @SerialName("app_version") val appVersion: String,
)

@Serializable
data class BleLogBody(
    @SerialName("device_key") val deviceKey: String,
    val content: String,
)

@Serializable
data class BleUpdateSuccessBody(
    @SerialName("device_key") val deviceKey: String,
    val version: String,
)

@Serializable
data class BatteryProfileBody(
    @SerialName("device_key") val deviceKey: String,
    @SerialName("battery_profile") val batteryProfile: String,
)

@Serializable
data class DebugLogBody(
    @SerialName("device_key") val deviceKey: String,
    val content: String,
)

@Serializable
data class IosDeviceBody(
    @SerialName("ios_uuid") val iosUuid: String,
    val mac: String,
)

// ─────────── 响应数据 ───────────
@Serializable
data class DeviceDto(
    @SerialName("short_name") val shortName: String,
    @SerialName("device_key") val deviceKey: String
)

@Serializable
data class DeviceInfoDto(
    @SerialName("device_key") val deviceKey: String = "",
    @SerialName("short_name") val shortName: String = "",
    @SerialName("frame_no") val frameNo: String = "",
    @SerialName("iot_version") val iotVersion: String = "",
    @SerialName("imei") val imei: String = "",
    @SerialName("model_name") val modelName: String = "",
    @SerialName("total_distance") val totalDistance: Double = 0.0,
    @Serializable(with = StringOrNumberSerializer::class)
    @SerialName("battery_voltage") val batteryVoltage: String = "",
    @SerialName("battery_life") val batteryLife: Int? = null,
    @Serializable(with = IntOrBooleanSerializer::class)
    @SerialName("signal_strength") val signalStrength: Int = 0,
    @Serializable(with = IntOrBooleanSerializer::class)
    @SerialName("online_status") val onlineStatus: Int = 0,
    @Serializable(with = IntOrBooleanSerializer::class)
    @SerialName("lock_status") val lockStatus: Int = 0,
    @Serializable(with = IntOrBooleanSerializer::class)
    @SerialName("silence_lock") val silenceLock: Int = 0,
    @Serializable(with = IntOrBooleanSerializer::class)
    @SerialName("gps_lock") val gpsLock: Int = 0,
    @Serializable(with = IntOrBooleanSerializer::class)
    @SerialName("is_owner") val isOwner: Int = 0,
    @Serializable(with = IntOrBooleanSerializer::class)
    @SerialName("power_supply_status") val powerSupplyStatus: Int = 0,
    @SerialName("bluetooth_mac_address") val bluetoothMacAddress: String = "",
    @SerialName("sos_phone") val sosPhone: String = "",
    @SerialName("last_trip") val lastTrip: LastTripDto? = null,
    @SerialName("device_model") val deviceModel: DeviceModelDto? = null,
    @SerialName("battery_info") val batteryInfo: BatteryInfoDto? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

@Serializable
data class BatteryInfoDto(
    @SerialName("raw_soc") val rawSoc: Int? = null,
    val bar: Int? = null,
)

@Serializable
data class LastTripDto(
    @SerialName("start_time") val startTime: String = "",
    @SerialName("total_distance") val totalDistance: Double = 0.0,
    val duration: Int = 0,
    @SerialName("max_speed") val maxSpeed: Double = 0.0,
    @SerialName("avg_speed") val avgSpeed: Double = 0.0
)

@Serializable
data class DeviceModelDto(
    @SerialName("control_model") val controlModel: String = "",
    @SerialName("model_name") val modelName: String = "",
    @Serializable(with = StringOrNumberSerializer::class)
    val length: String = "",
    @Serializable(with = StringOrNumberSerializer::class)
    val width: String = "",
    @Serializable(with = StringOrNumberSerializer::class)
    val height: String = "",
    @Serializable(with = StringOrNumberSerializer::class)
    val weight: String = "",
    @Serializable(with = StringOrNumberSerializer::class)
    @SerialName("motor_power") val motorPower: String = "",
    @Serializable(with = StringOrNumberSerializer::class)
    val range: String = "",
    @Serializable(with = StringOrNumberSerializer::class)
    @SerialName("battery_capacity") val batteryCapacity: String = "",
    @SerialName("is_control") val isControl: Int = 1,
    @SerialName("is_bms") val isBms: Int = 0,
    @SerialName("image_url") val imageUrl: String = ""
)

@Serializable
data class DeviceSettingsDto(
    @SerialName("induction_lock") val inductionLock: Int = 0,
    @SerialName("silence_lock") val silenceLock: Int = 0
)

// ─────────── Retrofit Service ───────────
interface AppApiService {

    @GET("mine/profile")
    suspend fun getUserProfile(): ApiResponse<JsonObject>

    // GET /mine/user-devices
    @GET("mine/user-devices")
    suspend fun getUserDevices(): ApiResponse<List<DeviceDto>>

    // GET /mine/device-info?device_key=xxx
    @GET("mine/device-info")
    suspend fun getDeviceInfo(@Query("device_key") deviceKey: String): ApiResponse<DeviceInfoDto>

    @GET("mine/device-track")
    suspend fun getDeviceTrack(
        @Query("device_key") deviceKey: String,
        @Query("interval") interval: String,
    ): ApiResponse<JsonObject>

    @GET("mine/remove-device-track")
    suspend fun removeDeviceTrack(@Query("device_key") deviceKey: String): ApiResponse<Unit>

    // GET /mine/settings?device_key=xxx
    @GET("mine/settings")
    suspend fun getDeviceSettings(@Query("device_key") deviceKey: String): ApiResponse<DeviceSettingsDto>

    @GET("mine/settings")
    suspend fun getDeviceSettingsRaw(@Query("device_key") deviceKey: String): ApiResponse<JsonObject>

    // GET /api/control?device_key=xxx&action=xxx
    @GET("api/control")
    suspend fun control(
        @Query("device_key") deviceKey: String,
        @Query("action") action: String
    ): ApiResponse<Unit>

    // POST /api/setting
    @POST("api/setting")
    suspend fun updateSetting(@Body body: SettingBody): ApiResponse<Unit>

    // POST /mine/induction-lock
    @POST("mine/induction-lock")
    suspend fun toggleInductionLock(@Body body: InductionLockBody): ApiResponse<Unit>

    // POST /mine/silence-lock
    @POST("mine/silence-lock")
    suspend fun toggleSilenceLock(@Body body: SilenceLockBody): ApiResponse<Unit>

    // POST /mine/set-default-device
    @POST("mine/set-default-device")
    suspend fun setDefaultDevice(@Body body: DeviceKeyBody): ApiResponse<Unit>

    // POST /mine/set-sos-phone
    @POST("mine/set-sos-phone")
    suspend fun setSosPhone(@Body body: SosPhoneBody): ApiResponse<Unit>

    @POST("mine/settings")
    suspend fun updateDeviceSettings(@Body body: JsonObject): ApiResponse<Unit>

    @GET("mine/battery-options")
    suspend fun getBatteryOptions(): ApiResponse<JsonObject>

    @POST("mine/set-battery-profile")
    suspend fun setBatteryProfile(@Body body: BatteryProfileBody): ApiResponse<Unit>

    @POST("mine/gps-lock")
    suspend fun setGpsLock(@Body body: JsonObject): ApiResponse<Unit>

    @POST("api/debug-logs")
    suspend fun uploadDebugLog(@Body body: DebugLogBody): ApiResponse<Unit>

    // GET /api/location-address?lat=x&lng=y
    @GET("api/location-address")
    suspend fun getLocationAddress(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double
    ): ApiResponse<JsonObject>

    // POST /mine/logout
    @POST("mine/logout")
    suspend fun logout(): ApiResponse<Unit>

    @POST("mine/bind_device")
    suspend fun bindDevice(@Body body: BindDeviceBody): ApiResponse<Unit>

    @POST("mine/unbind-device")
    suspend fun unbindDevice(@Body body: BindDeviceBody): ApiResponse<Unit>

    @GET("mine/shared-devices")
    suspend fun getSharedDevices(): ApiResponse<List<DeviceDto>>

    @POST("mine/share-device")
    suspend fun shareDevice(@Body body: ShareDeviceBody): ApiResponse<Unit>

    @GET("mine/shared-users")
    suspend fun getSharedUsers(@Query("device_key") deviceKey: String): ApiResponse<JsonObject>

    @POST("mine/remove-shared-user")
    suspend fun removeSharedUser(@Body body: RemoveSharedUserBody): ApiResponse<Unit>

    @POST("mine/change-owner")
    suspend fun changeOwner(@Body body: ChangeOwnerBody): ApiResponse<Unit>

    @POST("mine/upsert-cid")
    suspend fun upsertCid(@Body body: UpsertCidBody): ApiResponse<Unit>

    @POST("mine/upsert-push-device")
    suspend fun upsertPushDevice(@Body body: UpsertPushDeviceBody): ApiResponse<Unit>

    @POST("mine/new-share")
    suspend fun bindShare(@Body body: JsonObject): ApiResponse<Unit>

    @POST("mine/ble/logs")
    suspend fun createBleLog(@Body body: BleLogBody): ApiResponse<Unit>

    @GET("api/ble/check_update")
    suspend fun bleCheckUpdate(
        @Query("device_key") deviceKey: String,
        @Query("version") version: String,
    ): ApiResponse<JsonObject>

    @POST("api/ble/update_success")
    suspend fun bleUpdateSuccess(@Body body: BleUpdateSuccessBody): ApiResponse<Unit>

    @GET("check-update")
    suspend fun checkUpdate(
        @Query("version") version: String,
        @Query("platform") platform: String,
    ): ApiResponse<JsonObject>

    @GET("mine/ios-device")
    suspend fun getIosDeviceId(
        @Query("ios_uuid") iosUuid: String,
        @Query("mac") mac: String,
    ): ApiResponse<JsonObject>

    @POST("mine/ios-device")
    suspend fun saveIosDeviceId(@Body body: IosDeviceBody): ApiResponse<Unit>
}
