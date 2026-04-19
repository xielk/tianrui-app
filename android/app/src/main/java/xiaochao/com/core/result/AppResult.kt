package xiaochao.com.core.result

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Error(val code: String, val message: String) : AppResult<Nothing>
}
