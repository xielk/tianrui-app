package xiaochao.com.domain.auth

import xiaochao.com.data.auth.AuthRepository
import xiaochao.com.data.auth.LoginResponseData

class LoginUseCase(
    private val repository: AuthRepository = AuthRepository()
) {
    suspend operator fun invoke(phone: String, code: String): Result<LoginResponseData> {
        if (phone.isEmpty() || code.isEmpty()) {
            return Result.failure(Exception("手机号和验证码不能为空"))
        }
        return repository.loginWithSms(phone, code)
    }
}
