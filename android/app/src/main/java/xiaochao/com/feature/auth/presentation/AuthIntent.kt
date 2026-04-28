package xiaochao.com.feature.auth.presentation

sealed class AuthIntent {
    data class PhoneChanged(val phone: String) : AuthIntent()
    data class CodeChanged(val code: String) : AuthIntent()
    object ToggleAgreement : AuthIntent()
    object SendCodeClicked : AuthIntent()
    object SubmitClicked : AuthIntent()
    data class ShowAgreement(val type: AgreementType) : AuthIntent()
    object CloseAgreement : AuthIntent()
    object ErrorConsumed : AuthIntent()
}

enum class AgreementType {
    SERVICE, PRIVACY
}
