package xiaochao.com.domain.auth

import xiaochao.com.data.auth.AuthRepository

class SendCodeUseCase(
    private val repository: AuthRepository = AuthRepository()
) {
    suspend operator fun invoke(phone: String): Result<Unit> {
        if (!phone.matches(Regex("^1[3-9]\\d{9}$"))) {
            return Result.failure(Exception("请输入正确的手机号"))
        }
        return repository.sendVerificationCode(phone)
    }
}
