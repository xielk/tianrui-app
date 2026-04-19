package xiaochao.com.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xiaochao.com.domain.auth.LoginUseCase
import xiaochao.com.domain.auth.OneKeyLoginUseCase
import xiaochao.com.domain.auth.SendCodeUseCase
import xiaochao.com.data.session.AppSessionStore

class AuthViewModel(
    private val loginUseCase: LoginUseCase = LoginUseCase(),
    private val sendCodeUseCase: SendCodeUseCase = SendCodeUseCase(),
    private val oneKeyLoginUseCase: OneKeyLoginUseCase = OneKeyLoginUseCase(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        _uiState.update { it.copy(isAgree = AppSessionStore.isAgreedToTerms()) }
    }

    fun processIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.PhoneChanged -> _uiState.update { it.copy(phone = intent.phone) }
            is AuthIntent.CodeChanged -> _uiState.update { it.copy(code = intent.code) }
            AuthIntent.ToggleAgreement -> _uiState.update {
                val next = !it.isAgree
                AppSessionStore.setAgreedToTerms(next)
                it.copy(isAgree = next)
            }
            is AuthIntent.ShowAgreement -> {
                val title = if (intent.type == AgreementType.SERVICE) "用户协议" else "隐私政策"
                val url = if (intent.type == AgreementType.SERVICE) "https://cdn.tr.sheyutech.com/service.html" else "https://cdn.tr.sheyutech.com/privacy.html"
                _uiState.update { it.copy(showAgreementLayer = true, agreementTitle = title, agreementUrl = url) }
            }
            AuthIntent.CloseAgreement -> _uiState.update { it.copy(showAgreementLayer = false) }
            AuthIntent.SendCodeClicked -> sendCode()
            AuthIntent.SubmitClicked -> login()
            is AuthIntent.OneKeyTokenReceived -> loginWithOneKey(intent.token)
            AuthIntent.ErrorConsumed -> _uiState.update { it.copy(errorString = null) }
        }
    }

    private fun sendCode() {
        if (_uiState.value.countdown > 0) return

        viewModelScope.launch {
            val phone = _uiState.value.phone
            if (phone.isEmpty()) {
                _uiState.update { it.copy(errorString = "请先输入手机号") }
                return@launch
            }

            val result = sendCodeUseCase(phone)
            if (result.isSuccess) {
                startCountdown()
            } else {
                _uiState.update { it.copy(errorString = result.exceptionOrNull()?.message ?: "发送验证码失败") }
            }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        _uiState.update { it.copy(countdown = 60) }
        countdownJob = viewModelScope.launch {
            while (_uiState.value.countdown > 0) {
                delay(1000)
                _uiState.update { it.copy(countdown = it.countdown - 1) }
            }
        }
    }

    private fun login() {
        if (_uiState.value.isLoading) return

        val state = _uiState.value
        if (!state.isAgree) {
            _uiState.update { it.copy(errorString = "请先阅读并同意协议") }
            return
        }
        if (state.phone.isEmpty() || state.code.isEmpty()) {
            _uiState.update { it.copy(errorString = "手机号和验证码不能为空") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = loginUseCase(state.phone, state.code)
            
            if (result.isSuccess) {
                val loginData = result.getOrNull()
                if (loginData != null) {
                    xiaochao.com.core.AppConfig.deviceUuid = loginData.uuid
                }
                _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
            } else {
                _uiState.update { it.copy(isLoading = false, errorString = result.exceptionOrNull()?.message ?: "登录失败") }
            }
        }
    }

    private fun loginWithOneKey(token: String) {
        if (_uiState.value.isLoading) return
        if (!_uiState.value.isAgree) {
            _uiState.update { it.copy(errorString = "请先阅读并同意协议") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = oneKeyLoginUseCase(token)
            if (result.isSuccess) {
                val loginData = result.getOrNull()
                if (loginData != null) {
                    xiaochao.com.core.AppConfig.deviceUuid = loginData.uuid
                }
                _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorString = result.exceptionOrNull()?.message ?: "号码认证登录失败"
                    )
                }
            }
        }
    }
}
