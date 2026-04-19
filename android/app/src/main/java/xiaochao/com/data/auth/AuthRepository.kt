package xiaochao.com.data.auth

import android.os.Build
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import xiaochao.com.core.AppConfig
import xiaochao.com.data.network.AppApiService
import xiaochao.com.data.network.AppAuthApiService
import xiaochao.com.data.network.LoginOrRegisterRequest
import xiaochao.com.data.network.LoginResponseDto
import xiaochao.com.data.network.RetrofitClient
import xiaochao.com.data.network.TokenToMobileRequest
import xiaochao.com.data.push.PushIdentity
import xiaochao.com.data.push.TpnsManager
import xiaochao.com.data.session.AppSessionStore

class AuthRepository(
    private val authApiService: AppAuthApiService = RetrofitClient.authApiService,
    private val appApiService: AppApiService = RetrofitClient.appApiService,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun apiErrorMessage(e: Exception, fallback: String): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string().orEmpty()
            if (body.isNotEmpty()) {
                runCatching {
                    val obj = json.parseToJsonElement(body).jsonObject
                    val message = obj["message"]?.toString()?.trim('"').orEmpty()
                    if (message.isNotEmpty()) return message
                    val error = obj["error"]?.toString()?.trim('"').orEmpty()
                    if (error.isNotEmpty()) return error
                    val msg = obj["msg"]?.toString()?.trim('"').orEmpty()
                    if (msg.isNotEmpty()) return msg
                }
            }
            return "请求失败(${e.code()})"
        }
        return e.message?.takeIf { it.isNotBlank() } ?: fallback
    }

    private data class DeviceInfo(
        val cid: String? = null,
        val token: String? = null,
        val platform: String,
        val appInstanceId: String,
        val buildModel: String,
        val osVersion: String,
        val deviceName: String,
        val appVersion: String,
    )

    private fun buildDeviceInfo(): DeviceInfo {
        val meta = PushIdentity.currentMeta()
        val tpnsToken = TpnsManager.currentToken().takeIf { it.isNotBlank() }
        return DeviceInfo(
            cid = tpnsToken,
            token = tpnsToken,
            platform = meta.platform,
            appInstanceId = meta.appInstanceId,
            buildModel = meta.buildModel,
            osVersion = meta.osVersion,
            deviceName = meta.deviceName,
            appVersion = meta.appVersion,
        )
    }

    suspend fun logout(): Result<Unit> {
        return try {
            val resp = appApiService.logout()
            AppSessionStore.clearLoginSession()
            AppConfig.deviceUuid = ""
            if (resp.code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(resp.message.ifBlank { "退出登录失败" }))
            }
        } catch (e: Exception) {
            AppSessionStore.clearLoginSession()
            AppConfig.deviceUuid = ""
            Result.failure(Exception(apiErrorMessage(e, "退出登录失败")))
        }
    }

    suspend fun sendVerificationCode(phone: String): Result<Unit> {
        return try {
            val response = authApiService.sendVerificationCode(phone)
            if (response.code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "发送失败" }))
            }
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "sendVerificationCode error: ${e.message}")
            Result.failure(Exception(apiErrorMessage(e, "发送失败")))
        }
    }

    suspend fun loginWithSms(phone: String, code: String): Result<LoginResponseData> {
        return try {
            // 对齐 api.js: 如果本地已有 UUID，优先用 UUID 登录，否则用 phone
            val existingUuid = AppSessionStore.getUuid().ifEmpty { AppConfig.deviceUuid.ifEmpty { "" } }
                .ifEmpty { null }
            val deviceInfo = buildDeviceInfo()
            val request = LoginOrRegisterRequest(
                phone = if (existingUuid == null) phone else null,
                uuid = existingUuid,
                code = code,
                cid = deviceInfo.cid,
                token = deviceInfo.token,
                platform = deviceInfo.platform,
                appInstanceId = deviceInfo.appInstanceId,
                buildModel = deviceInfo.buildModel,
                osVersion = deviceInfo.osVersion,
                deviceName = deviceInfo.deviceName,
                appVersion = deviceInfo.appVersion,
            )
            val response = authApiService.loginOrRegister(request)
            if (response.code == 0 && response.data != null) {
                completeLogin(response.data, fallbackPhone = phone)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "登录失败" }))
            }
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "loginWithSms error: ${e.message}")
            Result.failure(Exception(apiErrorMessage(e, "登录失败")))
        }
    }

    suspend fun loginWithOneKeyToken(accessToken: String): Result<LoginResponseData> {
        return try {
            val deviceInfo = buildDeviceInfo()
            val request = TokenToMobileRequest(
                accessToken = accessToken,
                cid = deviceInfo.cid,
                token = deviceInfo.token,
                platform = deviceInfo.platform,
                appInstanceId = deviceInfo.appInstanceId,
                buildModel = deviceInfo.buildModel,
                osVersion = deviceInfo.osVersion,
                deviceName = deviceInfo.deviceName,
                appVersion = deviceInfo.appVersion,
            )
            val response = authApiService.token2mobile(request)
            if (response.code == 0 && response.data != null) {
                completeLogin(response.data, fallbackPhone = "")
            } else {
                Result.failure(Exception(response.message.ifEmpty { "号码认证登录失败" }))
            }
        } catch (e: Exception) {
            android.util.Log.e("API_DEBUG", "loginWithOneKeyToken error: ${e.message}")
            val message = apiErrorMessage(e, "号码认证登录失败")
            Result.failure(Exception("$message，请使用短信登录"))
        }
    }

    private suspend fun completeLogin(dto: LoginResponseDto, fallbackPhone: String): Result<LoginResponseData> {
        val uuid = dto.uuid
        val token = dto.token
        AppConfig.deviceUuid = uuid
        AppSessionStore.setUuid(uuid)
        AppSessionStore.setToken(token)
        dto.defaultDeviceKey?.takeIf { it.isNotEmpty() }?.let {
            AppSessionStore.setLastDeviceKey(it)
        }
        TpnsManager.reportCurrentTokenIfPossible()

        val profileResp = appApiService.getUserProfile()
        if (profileResp.code == 0 && profileResp.data != null) {
            val userInfoJson = json.encodeToString(profileResp.data)
            AppSessionStore.setUserInfoJson(userInfoJson)
            val profilePhone = profileResp.data["phone"]?.toString()?.trim('"') ?: fallbackPhone
            return Result.success(LoginResponseData(token, uuid, UserInfo("", profilePhone)))
        }
        return Result.failure(Exception(profileResp.message.ifEmpty { "获取用户信息失败" }))
    }
}
