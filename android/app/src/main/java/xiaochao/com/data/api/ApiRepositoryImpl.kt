package xiaochao.com.data.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import retrofit2.HttpException
import xiaochao.com.core.result.AppResult
import xiaochao.com.data.model.VehicleStatus
import xiaochao.com.data.push.PushIdentity
import xiaochao.com.data.network.AppApiService
import xiaochao.com.data.network.BindDeviceBody
import xiaochao.com.data.network.BleLogBody
import xiaochao.com.data.network.ChangeOwnerBody
import xiaochao.com.data.network.DeviceKeyBody
import xiaochao.com.data.network.DeviceInfoDto
import xiaochao.com.data.network.InductionLockBody
import xiaochao.com.data.network.RemoveSharedUserBody
import xiaochao.com.data.network.RetrofitClient
import xiaochao.com.data.network.ShareDeviceBody
import xiaochao.com.data.network.SilenceLockBody
import xiaochao.com.data.network.SosPhoneBody
import xiaochao.com.data.network.UpsertCidBody
import xiaochao.com.data.network.UpsertPushDeviceBody

class ApiRepositoryImpl(
    private val apiService: AppApiService = RetrofitClient.appApiService
) : ApiRepository {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        fun buildRemoveSharedUserBody(deviceKey: String, memberId: String): RemoveSharedUserBody {
            val memberElement: JsonElement = memberId.toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(memberId)
            return RemoveSharedUserBody(deviceKey = deviceKey, memberId = memberElement, shareUuid = null)
        }
    }

    private fun apiErrorMessage(e: Exception, fallback: String): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string().orEmpty()
            if (body.isNotEmpty()) {
                runCatching {
                    val obj = json.parseToJsonElement(body).jsonObject
                    val message = obj["message"]?.toString()?.trim('"').orEmpty()
                    if (message.isNotEmpty()) return message
                }
            }
        }
        return e.message?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun resolveBatteryPercent(info: xiaochao.com.data.network.DeviceInfoDto): Int {
        val rawSoc = info.batteryInfo?.rawSoc
        if (rawSoc != null && rawSoc != 255) {
            return rawSoc.coerceIn(0, 100)
        }
        val bar = info.batteryInfo?.bar
        if (bar != null && bar in 0..5) {
            return (bar * 20).coerceIn(0, 100)
        }
        return (info.batteryLife ?: 0).coerceIn(0, 100)
    }

    private fun signalLevel(rawSignal: Int): Int {
        return (rawSignal / 3).coerceIn(0, 5)
    }

    override suspend fun fetchUserProfile(): AppResult<String> {
        return try {
            val resp = apiService.getUserProfile()
            if (resp.code == 0 && resp.data != null) {
                AppResult.Success(json.encodeToString(resp.data))
            } else {
                AppResult.Error("API_ERROR", resp.message)
            }
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "fetchUserProfile error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "网络错误"))
        }
    }

    override suspend fun bindDevice(deviceKey: String): AppResult<Unit> {
        return try {
            val resp = apiService.bindDevice(BindDeviceBody(deviceKey))
            if (resp.code == 0) AppResult.Success(Unit) else AppResult.Error("API_ERROR", resp.message)
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "bindDevice error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "绑定失败"))
        }
    }

    override suspend fun unbindDevice(deviceKey: String): AppResult<Unit> {
        return try {
            val resp = apiService.unbindDevice(BindDeviceBody(deviceKey))
            if (resp.code == 0) AppResult.Success(Unit) else AppResult.Error("API_ERROR", resp.message)
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "unbindDevice error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "解绑失败"))
        }
    }

    override suspend fun setDefaultDevice(deviceKey: String): AppResult<Unit> {
        return try {
            val resp = apiService.setDefaultDevice(DeviceKeyBody(deviceKey))
            if (resp.code == 0) AppResult.Success(Unit) else AppResult.Error("API_ERROR", resp.message)
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "setDefaultDevice error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "设置默认车辆失败"))
        }
    }

    override suspend fun setSosPhone(deviceKey: String, phone: String): AppResult<String> {
        return try {
            val resp = apiService.setSosPhone(SosPhoneBody(deviceKey = deviceKey, sosPhone = phone))
            if (resp.code == 0) AppResult.Success(phone) else AppResult.Error("API_ERROR", resp.message)
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "setSosPhone error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "设置 SOS 联系人失败"))
        }
    }


    // ─── 获取设备详情 + 设备设置（对应 F2.vue 的 loadVehicleInfo）───
    override suspend fun fetchVehicleStatus(deviceKey: String): AppResult<VehicleStatus> {
        return try {
            val infoResp = apiService.getDeviceInfo(deviceKey)
            if (infoResp.code != 0 || infoResp.data == null) {
                return AppResult.Error("API_ERROR", infoResp.message)
            }
            val info = infoResp.data

            val settingsResp = apiService.getDeviceSettings(deviceKey)
            val settings = settingsResp.data

            val lastTrip = info.lastTrip
            val status = VehicleStatus(
                frameNumber          = info.shortName.ifEmpty { info.deviceKey },
                controlModel         = info.deviceModel?.controlModel.orEmpty(),
                historyMileKm        = info.totalDistance.toInt(),
                sosPhoneMasked       = info.sosPhone,
                batteryPercent       = resolveBatteryPercent(info),
                showLightning        = info.powerSupplyStatus == 1,
                voltageText          = info.batteryVoltage,
                signalStrengthLevel  = if (info.onlineStatus == 1) signalLevel(info.signalStrength) else 0,
                onlineStatus         = info.onlineStatus,
                isOwner              = info.isOwner == 1,
                bluetoothMacAddress  = info.bluetoothMacAddress,
                latitude             = info.latitude,
                longitude            = info.longitude,
                bleConnected         = false,
                bleSystemConnected   = false,
                isLocked             = info.lockStatus == 0,
                isMuteEnabled        = info.silenceLock != 1,
                isAutoSenseEnabled   = settings?.inductionLock == 1,
                gpsLocked            = info.gpsLock == 1,
                timeText             = lastTrip?.startTime ?: "",
                mileageKm            = lastTrip?.totalDistance?.toString() ?: "0",
                durationMin          = lastTrip?.duration ?: 0,
                topSpeedKmh          = 0,
                avgSpeedKmh          = 0,
            )
            AppResult.Success(status)
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "fetchVehicleStatus error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "加载车辆状态失败"))
        }
    }

    override suspend fun fetchDeviceInfo(deviceKey: String): AppResult<DeviceInfoDto> {
        return try {
            val infoResp = apiService.getDeviceInfo(deviceKey)
            if (infoResp.code != 0 || infoResp.data == null) {
                AppResult.Error("API_ERROR", infoResp.message)
            } else {
                AppResult.Success(infoResp.data)
            }
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "fetchDeviceInfo error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "获取车辆详情失败"))
        }
    }

    override suspend fun fetchDeviceTrack(deviceKey: String, interval: Int): AppResult<Boolean> {
        return when (val pointsResult = fetchDeviceTrackPoints(deviceKey, interval)) {
            is AppResult.Success -> {
                if (pointsResult.data.isNotEmpty()) AppResult.Success(true)
                else AppResult.Error("NO_TRACK", "暂无轨迹数据")
            }
            is AppResult.Error -> AppResult.Error(pointsResult.code, pointsResult.message)
        }
    }

    override suspend fun fetchDeviceTrackPoints(deviceKey: String, interval: Int): AppResult<List<TrackPoint>> {
        return try {
            val resp = apiService.getDeviceTrack(deviceKey, interval.toString())
            if (resp.code != 0 || resp.data == null) {
                AppResult.Error("API_ERROR", resp.message)
            } else {
                val points = resp.data["polyline"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("points")
                    ?.jsonArray
                    ?.mapNotNull { element ->
                        val obj = element.jsonObject
                        val lat = obj["latitude"]?.jsonPrimitive?.doubleOrNull
                        val lng = obj["longitude"]?.jsonPrimitive?.doubleOrNull
                        if (lat == null || lng == null) null else TrackPoint(latitude = lat, longitude = lng)
                    }
                    .orEmpty()
                if (points.isEmpty()) AppResult.Error("NO_TRACK", "暂无轨迹数据")
                else AppResult.Success(points)
            }
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "fetchDeviceTrackPoints error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "获取轨迹失败"))
        }
    }

    override suspend fun fetchLocationAddress(latitude: Double, longitude: Double): AppResult<String> {
        return try {
            val resp = apiService.getLocationAddress(latitude, longitude)
            if (resp.code == 0 && resp.data != null) {
                val obj = resp.data
                val formatted = obj["formatted_address"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val province = obj["province"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val city = obj["city"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val district = obj["district"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val mergedPrefix = "$province$city$district".trim()
                val finalAddress = when {
                    formatted.isNotBlank() && mergedPrefix.isNotBlank() && formatted.startsWith(mergedPrefix) -> formatted
                    formatted.isNotBlank() -> if (mergedPrefix.isNotBlank()) "$mergedPrefix $formatted" else formatted
                    mergedPrefix.isNotBlank() -> mergedPrefix
                    else -> "获取地址失败"
                }
                AppResult.Success(finalAddress)
            } else {
                AppResult.Error("API_ERROR", resp.message.ifBlank { "获取地址失败" })
            }
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "fetchLocationAddress error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "获取地址失败"))
        }
    }

    // ─── 锁车 / 解锁（对应 api.js lockDevice / unlockDevice）───
    override suspend fun toggleLock(deviceKey: String, locked: Boolean): AppResult<Unit> {
        return try {
            val action = if (locked) "CLOSE_LOCK" else "OPEN_LOCK"
            val resp = apiService.control(deviceKey, action)
            if (resp.code == 0) AppResult.Success(Unit)
            else AppResult.Error("API_ERROR", resp.message)
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "toggleLock error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "锁车操作失败"))
        }
    }

    // ─── 静音/有声（对应 api.js toggleMute -> /api/setting）───
    override suspend fun toggleMute(deviceKey: String, mute: Boolean): AppResult<Unit> {
        return try {
            val resp = apiService.toggleSilenceLock(
                SilenceLockBody(
                    deviceKey = deviceKey,
                    silenceLock = if (mute) 1 else 0,
                )
            )
            if (resp.code == 0) AppResult.Success(Unit)
            else AppResult.Error("API_ERROR", resp.message)
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "toggleMute error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "静音设置失败"))
        }
    }

    // ─── 感应解锁（对应 api.js enableInductionLock / disableInductionLock）───
    override suspend fun toggleAutoSense(deviceKey: String, enabled: Boolean): AppResult<Unit> {
        return try {
            val body = InductionLockBody(deviceKey = deviceKey, inductionLock = if (enabled) 1 else 0)
            val resp = apiService.toggleInductionLock(body)
            if (resp.code == 0) AppResult.Success(Unit)
            else AppResult.Error("API_ERROR", resp.message)
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "toggleAutoSense error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "感应设置失败"))
        }
    }

    // ─── 寻车（对应 api.js findDevice -> /api/control?action=OPEN_QUERY）───
    override suspend fun findBike(deviceKey: String): AppResult<Unit> {
        return try {
            val resp = apiService.control(deviceKey, "OPEN_QUERY")
            if (resp.code == 0) AppResult.Success(Unit)
            else AppResult.Error("API_ERROR", resp.message)
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "findBike error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "寻车失败"))
        }
    }

    // ─── 获取用户车辆列表（对应 api.js getUserDevices）───
    override suspend fun fetchUserDevices(): AppResult<List<DeviceDto>> {
        return try {
            val resp = apiService.getUserDevices()
            if (resp.code == 0 && resp.data != null) {
                val devices = resp.data.map { DeviceDto(it.shortName, it.deviceKey) }
                AppResult.Success(devices)
            } else {
                AppResult.Error("API_ERROR", resp.message)
            }
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "fetchUserDevices error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "加载车辆列表失败"))
        }
    }

    override suspend fun fetchDeviceSettings(deviceKey: String): AppResult<DeviceSettingsData> {
        return try {
            val resp = apiService.getDeviceSettingsRaw(deviceKey)
            if (resp.code != 0 || resp.data == null) {
                return AppResult.Error("API_ERROR", resp.message)
            }

            val data = resp.data
            val settings = DeviceSettingsData(
                sensitivityDistance = data["sensitivity_distance"]?.jsonPrimitive?.intOrNull ?: 0,
                sensitivityDistance2 = data["sensitivity_distance2"]?.jsonPrimitive?.intOrNull ?: 0,
                alarmSensitivity = data["alarm_sensitivity"]?.jsonPrimitive?.contentOrNull ?: "high",
                autoShutdownTime = data["auto_shutdown_time"]?.jsonPrimitive?.intOrNull ?: 0,
                gpsLock = data["gps_lock"]?.jsonPrimitive?.intOrNull ?: 0,
            )
            AppResult.Success(settings)
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "fetchDeviceSettings error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "获取设置失败"))
        }
    }

    override suspend fun updateDeviceSettings(deviceKey: String, settings: DeviceSettingsData): AppResult<Unit> {
        return try {
            val body = buildJsonObject {
                put("device_key", JsonPrimitive(deviceKey))
                put("sensitivity_distance", JsonPrimitive(settings.sensitivityDistance))
                put("sensitivity_distance2", JsonPrimitive(settings.sensitivityDistance2))
                put("alarm_sensitivity", JsonPrimitive(settings.alarmSensitivity))
                put("auto_shutdown_time", JsonPrimitive(settings.autoShutdownTime))
            }
            val resp = apiService.updateDeviceSettings(body)
            if (resp.code == 0) AppResult.Success(Unit) else AppResult.Error("API_ERROR", resp.message)
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "updateDeviceSettings error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "更新设置失败"))
        }
    }

    override suspend fun setGpsLock(deviceKey: String, gpsLock: Int): AppResult<Unit> {
        return try {
            val body = buildJsonObject {
                put("device_key", JsonPrimitive(deviceKey))
                put("gps_lock", JsonPrimitive(gpsLock))
            }
            val resp = apiService.setGpsLock(body)
            if (resp.code == 0) AppResult.Success(Unit) else AppResult.Error("API_ERROR", resp.message)
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "setGpsLock error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "GPS设置失败"))
        }
    }

    override suspend fun fetchSharedUsers(deviceKey: String): AppResult<List<SharedUserItem>> {
        return try {
            val resp = apiService.getSharedUsers(deviceKey)
            if (resp.code == 0 && resp.data != null) {
                val users = resp.data["users"]
                    ?.jsonArray
                    ?.mapNotNull { element ->
                        val obj = element.jsonObject
                        val memberId = obj["member_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val phone = obj["phone"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (memberId.isBlank() || phone.isBlank()) {
                            null
                        } else {
                            SharedUserItem(
                                memberId = memberId,
                                phone = phone,
                                isOwner = (obj["is_owner"]?.jsonPrimitive?.intOrNull ?: 0) == 1,
                            )
                        }
                    }
                    .orEmpty()
                AppResult.Success(users)
            } else {
                AppResult.Error("API_ERROR", resp.message)
            }
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "fetchSharedUsers error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "获取用车人失败"))
        }
    }

    override suspend fun shareDevice(deviceKey: String, phone: String): AppResult<Unit> {
        return try {
            val resp = apiService.shareDevice(ShareDeviceBody(deviceKey = deviceKey, phone = phone))
            if (resp.code == 0) AppResult.Success(Unit) else AppResult.Error("API_ERROR", resp.message)
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "shareDevice error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "添加用车人失败"))
        }
    }

    override suspend fun removeSharedUser(deviceKey: String, memberId: String): AppResult<Unit> {
        return try {
            val resp = apiService.removeSharedUser(
                buildRemoveSharedUserBody(deviceKey = deviceKey, memberId = memberId)
            )
            if (resp.code == 0) AppResult.Success(Unit) else AppResult.Error("API_ERROR", resp.message)
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "removeSharedUser error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "删除用车人失败"))
        }
    }

    override suspend fun changeOwner(deviceKey: String, newOwnerPhone: String): AppResult<Unit> {
        return try {
            val resp = apiService.changeOwner(
                ChangeOwnerBody(deviceKey = deviceKey, newOwnerPhone = newOwnerPhone)
            )
            if (resp.code == 0) AppResult.Success(Unit) else AppResult.Error("API_ERROR", resp.message)
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "changeOwner error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "更换车主失败"))
        }
    }

    override suspend fun upsertCid(cid: String): AppResult<Unit> {
        return try {
            val resp = apiService.upsertCid(UpsertCidBody(cid = cid))
            if (resp.code == 0) AppResult.Success(Unit) else AppResult.Error("API_ERROR", resp.message)
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "upsertCid error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "上报CID失败"))
        }
    }

    override suspend fun upsertPushDevice(token: String): AppResult<Unit> {
        return try {
            val meta = PushIdentity.currentMeta()
            val resp = apiService.upsertPushDevice(
                UpsertPushDeviceBody(
                    token = token,
                    platform = meta.platform,
                    appInstanceId = meta.appInstanceId,
                    deviceModel = meta.buildModel,
                    appVersion = meta.appVersion,
                )
            )
            if (resp.code == 0) AppResult.Success(Unit) else AppResult.Error("API_ERROR", resp.message)
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "upsertPushDevice error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "上报推送设备失败"))
        }
    }

    override suspend fun createBleLog(deviceKey: String, content: String): AppResult<Unit> {
        return try {
            val resp = apiService.createBleLog(BleLogBody(deviceKey = deviceKey, content = content))
            if (resp.code == 0) AppResult.Success(Unit) else AppResult.Error("API_ERROR", resp.message)
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "createBleLog error: ${e.message}")
            AppResult.Error("NETWORK_ERROR", apiErrorMessage(e, "蓝牙日志上报失败"))
        }
    }
}
