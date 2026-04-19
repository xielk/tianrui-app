package xiaochao.com.data.network

import xiaochao.com.core.AppConfig
import java.security.MessageDigest

/**
 * 完全对齐 api.js 的签名算法：
 * 1. 收集参数：timestamp + URL查询参数 + (POST时的 body=JSON.stringify(data))
 * 2. 按key字典排序，拼成 key1=val1&key2=val2&...&key=API_KEY
 * 3. MD5 取大写
 */
object SignatureHelper {

    fun generate(timestamp: String, method: String, path: String, body: String = ""): String {
        val params = mutableMapOf<String, String>()
        params["timestamp"] = timestamp

        // 解析 URL 中的查询参数 (对齐 js 的 parseQueryString)
        val qIndex = path.indexOf('?')
        if (qIndex != -1) {
            val query = path.substring(qIndex + 1)
            query.split("&").forEach { pair ->
                val eq = pair.indexOf('=')
                if (eq != -1) {
                    val k = pair.substring(0, eq)
                    val v = pair.substring(eq + 1)
                    if (k.isNotEmpty()) params[k] = v
                }
            }
        }

        // POST / PUT 时加 body 参数 (对齐 js 的 params.body = JSON.stringify(data))
        val upperMethod = method.uppercase()
        if ((upperMethod == "POST" || upperMethod == "PUT") && body.isNotEmpty()) {
            params["body"] = body
        }

        // 字典排序 (对齐 js 的 Object.keys(params).sort())
        val sortedKeys = params.keys.sorted()
        val sb = StringBuilder()
        sortedKeys.forEach { k -> sb.append("$k=${params[k]}&") }
        sb.append("key=${ApiConstants.API_KEY}")

        return md5(sb.toString()).uppercase()
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
