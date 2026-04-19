package xiaochao.com.domain.auth

import xiaochao.com.data.auth.AuthRepository
import xiaochao.com.data.auth.LoginResponseData

class OneKeyLoginUseCase(
    private val repository: AuthRepository = AuthRepository()
) {
    suspend operator fun invoke(accessToken: String): Result<LoginResponseData> {
        if (accessToken.isBlank()) {
            return Result.failure(Exception("号码认证失败"))
        }
        return repository.loginWithOneKeyToken(accessToken)
    }
}
