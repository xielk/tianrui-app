package xiaochao.com.data.auth

import kotlinx.serialization.Serializable

// 这些数据类被 AuthRepository 和 AuthViewModel 使用
@Serializable
data class LoginResponseData(
    val token: String,
    val uuid: String,
    val userInfo: UserInfo
)

@Serializable
data class UserInfo(
    val id: String,
    val phone: String
)
