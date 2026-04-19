package xiaochao.com.data.network

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import xiaochao.com.core.AppConfig
import xiaochao.com.data.session.AppSessionStore

/**
 * OkHttp 拦截器，对齐 api.js request() 函数的 header 注入逻辑：
 *   UUID       -> uni.getStorageSync('uuid')
 *   X-Timestamp -> Math.floor(Date.now() / 1000).toString()
 *   X-Sign     -> generateSignature(timestamp, method, url, data)
 *   Content-Type -> application/json
 */
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val timestamp = (System.currentTimeMillis() / 1000).toString()

        // 提取相对路径 (含查询参数) 用于签名
        val url = original.url
        val path = buildString {
            append(url.encodedPath)
            if (url.encodedQuery != null) append("?${url.encodedQuery}")
        }

        // 读取请求体 JSON 字符串 (POST 时)
        val bodyJson = readBodyAsString(original.body)

        val signature = SignatureHelper.generate(timestamp, original.method, path, bodyJson)

        val requestUuid = AppSessionStore.getUuid().ifEmpty { AppConfig.deviceUuid }

        android.util.Log.i(
            "API_DEBUG",
            "--> ${original.method} ${url.toString()}\n" +
            "    UUID=$requestUuid\n" +
            "    X-Timestamp=$timestamp  X-Sign=$signature" +
            if (bodyJson.isNotEmpty()) "\n    Body=$bodyJson" else ""
        )

        val newRequest: Request = original.newBuilder()
            .header("Content-Type", "application/json")
            .header("UUID", requestUuid)
            .header("X-Timestamp", timestamp)
            .header("X-Sign", signature)
            .build()

        val response = chain.proceed(newRequest)

        android.util.Log.i(
            "API_DEBUG",
            "<-- ${response.code} ${url.toString()}"
        )

        if (response.code >= 400) {
            val errBody = runCatching { response.peekBody(1024 * 1024).string() }.getOrElse { "" }
            if (errBody.isNotBlank()) {
                android.util.Log.i("API_DEBUG", "<-- ERROR BODY ${url.toString()}\n$errBody")
            }
        }

        if (url.encodedPath.contains("mine/device-info") || url.encodedPath.contains("mine/upsert-push-device")) {
            val body = runCatching { response.peekBody(1024 * 1024).string() }.getOrElse { "" }
            if (body.isNotEmpty()) {
                android.util.Log.i("API_DEBUG", "<-- BODY ${url.toString()}\n$body")
            }
        }

        return response
    }

    private fun readBodyAsString(body: RequestBody?): String {
        if (body == null) return ""
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        } catch (e: Exception) {
            ""
        }
    }
}
