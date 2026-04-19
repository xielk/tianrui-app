package xiaochao.com.feature.auth.presentation

data class AuthUiState(
    val phone: String = "",
    val code: String = "",
    val isAgree: Boolean = false,
    val countdown: Int = 0,
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val showAgreementLayer: Boolean = false,
    val agreementTitle: String = "",
    val agreementUrl: String = "",
    val errorString: String? = null
)
