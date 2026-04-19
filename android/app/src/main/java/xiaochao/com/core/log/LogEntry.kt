package xiaochao.com.core.log

data class LogEntry(
    val tag: String,
    val message: String,
    val payload: Map<String, Any?> = emptyMap(),
)
