package xiaochao.com.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

// ─────────── 登录请求体 ───────────
@Serializable
data class LoginOrRegisterRequest(
    val phone: String? = null,
    @SerialName("UUID") val uuid: String? = null,
    val code: String,
    val cid: String? = null,
    val token: String? = null,
    val platform: String? = null,
    @SerialName("app_instance_id") val appInstanceId: String? = null,
    @SerialName("build_model") val buildModel: String? = null,
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
)

@Serializable
data class SendCodeRequest(val phone: String)

// ─────────── 登录响应 ───────────
@Serializable
data class LoginResponseDto(
    val uuid: String = "",
    val token: String = "",
    @SerialName("default_device_key") val defaultDeviceKey: String? = null,
)

@Serializable
data class LoginWithPasswordRequest(
    val phone: String? = null,
    @SerialName("UUID") val uuid: String? = null,
    val password: String,
    val cid: String? = null,
    val token: String? = null,
    val platform: String? = null,
    @SerialName("app_instance_id") val appInstanceId: String? = null,
    @SerialName("build_model") val buildModel: String? = null,
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
)

@Serializable
data class SetPasswordRequest(
    @SerialName("UUID") val uuid: String,
    val password: String,
)

@Serializable
data class SmsVerifyResponse(
    val success: Boolean = false,
)

@Serializable
data class TokenToMobileRequest(
    @SerialName("access_token") val accessToken: String,
    val cid: String? = null,
    val token: String? = null,
    val platform: String? = null,
    @SerialName("app_instance_id") val appInstanceId: String? = null,
    @SerialName("build_model") val buildModel: String? = null,
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
)

// ─────────── Auth Service ───────────
interface AppAuthApiService {

    // POST /api/sms/send?phone=xxx
    @POST("api/sms/send")
    suspend fun sendVerificationCode(
        @Query("phone") phone: String
    ): ApiResponse<Unit>

    // POST /api/login-or-register
    @POST("api/login-or-register")
    suspend fun loginOrRegister(@Body request: LoginOrRegisterRequest): ApiResponse<LoginResponseDto>

    // POST /api/sms/verify?phone=xxx&code=xxx
    @POST("api/sms/verify")
    suspend fun verifyCode(
        @Query("phone") phone: String,
        @Query("code") code: String
    ): ApiResponse<SmsVerifyResponse>

    // POST /api/login-with-password
    @POST("api/login-with-password")
    suspend fun loginWithPassword(@Body request: LoginWithPasswordRequest): ApiResponse<LoginResponseDto>

    // POST /api/token2mobile
    @POST("api/token2mobile")
    suspend fun token2mobile(@Body request: TokenToMobileRequest): ApiResponse<LoginResponseDto>

    // POST /api/set-password
    @POST("api/set-password")
    suspend fun setPassword(@Body request: SetPasswordRequest): ApiResponse<Unit>
}
