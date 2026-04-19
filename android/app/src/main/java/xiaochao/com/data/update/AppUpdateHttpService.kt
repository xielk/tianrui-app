package xiaochao.com.data.update

import android.util.Log
import com.xuexiang.xupdate.proxy.IUpdateHttpService
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class AppUpdateHttpService : IUpdateHttpService {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val downloadCalls = ConcurrentHashMap<String, Call>()

    override fun asyncGet(url: String, params: Map<String, Any>, callback: IUpdateHttpService.Callback) {
        val requestUrl = buildGetUrl(url, params)
        Log.d(API_DEBUG_TAG, "app_update http_get url=$requestUrl")
        val request = Request.Builder().url(requestUrl).get().build()
        client.newCall(request).enqueue(SimpleStringCallback(callback))
    }

    override fun asyncPost(url: String, params: Map<String, Any>, callback: IUpdateHttpService.Callback) {
        val form = FormBody.Builder().apply {
            params.forEach { (key, value) -> add(key, value.toString()) }
        }.build()
        Log.d(API_DEBUG_TAG, "app_update http_post url=$url params=$params")
        val request = Request.Builder().url(url).post(form).build()
        client.newCall(request).enqueue(SimpleStringCallback(callback))
    }

    override fun download(url: String, path: String, fileName: String, callback: IUpdateHttpService.DownloadCallback) {
        val request = Request.Builder().url(url).get().build()
        val call = client.newCall(request)
        downloadCalls[url] = call
        call.enqueue(DownloadFileCallback(url, path, fileName, callback))
    }

    override fun cancelDownload(url: String) {
        downloadCalls.remove(url)?.cancel()
        Log.d(API_DEBUG_TAG, "app_update cancel_download url=$url")
    }

    private fun buildGetUrl(url: String, params: Map<String, Any>): String {
        val builder = url.toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("Invalid url: $url")
        params.forEach { (key, value) -> builder.addQueryParameter(key, value.toString()) }
        return builder.build().toString()
    }

    private inner class SimpleStringCallback(
        private val callback: IUpdateHttpService.Callback,
    ) : okhttp3.Callback {
        override fun onFailure(call: Call, e: java.io.IOException) {
            Log.e(API_DEBUG_TAG, "app_update http_error: ${e.message}", e)
            callback.onError(e)
        }

        override fun onResponse(call: Call, response: okhttp3.Response) {
            response.use {
                if (!it.isSuccessful) {
                    val t = IllegalStateException("HTTP ${it.code}: ${it.message}")
                    Log.e(API_DEBUG_TAG, "app_update http_not_successful: ${t.message}")
                    callback.onError(t)
                    return
                }
                val body = it.body?.string().orEmpty()
                callback.onSuccess(body)
            }
        }
    }

    private inner class DownloadFileCallback(
        private val url: String,
        private val path: String,
        private val fileName: String,
        private val callback: IUpdateHttpService.DownloadCallback,
    ) : okhttp3.Callback {
        override fun onFailure(call: Call, e: java.io.IOException) {
            downloadCalls.remove(url)
            Log.e(API_DEBUG_TAG, "app_update download_error: ${e.message}", e)
            callback.onError(e)
        }

        override fun onResponse(call: Call, response: okhttp3.Response) {
            response.use { res ->
                downloadCalls.remove(url)
                if (!res.isSuccessful) {
                    callback.onError(IllegalStateException("HTTP ${res.code}: ${res.message}"))
                    return
                }

                callback.onStart()
                val body = res.body ?: run {
                    callback.onError(IllegalStateException("Empty response body"))
                    return
                }

                val targetDir = File(path)
                if (!targetDir.exists()) targetDir.mkdirs()
                val targetFile = File(targetDir, fileName)

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val total = body.contentLength()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytesCopied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            val progress = if (total > 0L) bytesCopied * 100f / total else 0f
                            callback.onProgress(progress, total)
                        }
                        output.flush()
                    }
                }

                callback.onSuccess(targetFile)
            }
        }
    }

    companion object {
        private const val API_DEBUG_TAG = "API_DEBUG"
    }
}
