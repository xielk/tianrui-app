package xiaochao.com.data.push

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.tencent.android.tpush.XGPushManager
import com.tencent.tpns.baseapi.core.net.HttpRequestCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import xiaochao.com.MainActivity
import xiaochao.com.core.AppConfig
import xiaochao.com.data.session.AppSessionStore

object TpnsCommandDispatcher {
    private const val TAG = "TPNS"

    fun handleRaw(context: Context, content: String?, customContent: String?) {
        val payload = when {
            !customContent.isNullOrBlank() -> customContent
            !content.isNullOrBlank() -> content
            else -> null
        } ?: return

        val cmd = parseCmd(payload)
        if (cmd.isBlank()) {
            Log.d(TAG, "tpns cmd ignored: no cmd payload=$payload")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            when (cmd.lowercase()) {
                "logout" -> handleLogout(context.applicationContext)
                "uploadlog", "uploadfile" -> handleUploadLog(context.applicationContext)
                else -> Log.d(TAG, "tpns cmd ignored: unsupported cmd=$cmd")
            }
        }
    }

    private fun parseCmd(payload: String): String {
        return runCatching {
            JSONObject(payload).optString("cmd")
        }.getOrDefault(payload.trim())
    }

    private fun handleLogout(context: Context) {
        Log.w(TAG, "tpns cmd execute logout")
        AppSessionStore.clearLoginSession()
        AppConfig.deviceUuid = ""

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "账号在其他设备登录，已退出", Toast.LENGTH_SHORT).show()
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("force_logout", true)
            }
            context.startActivity(intent)
        }
    }

    private fun handleUploadLog(context: Context) {
        Log.i(TAG, "tpns cmd execute uploadlog")
        runCatching {
            XGPushManager.uploadLogFile(context, object : HttpRequestCallback {
                override fun onSuccess(data: String?) {
                    Log.i(TAG, "tpns uploadLog success data=$data")
                }

                override fun onFailure(code: Int, message: String?) {
                    Log.e(TAG, "tpns uploadLog failed code=$code msg=$message")
                }
            })
        }.onFailure {
            Log.e(TAG, "tpns uploadLog execute error: ${it.message}", it)
        }
    }
}
