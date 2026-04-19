package xiaochao.com.data.push

import android.content.Context
import android.util.Log
import com.tencent.android.tpush.XGIOperateCallback
import com.tencent.android.tpush.XGPushConfig
import com.tencent.android.tpush.XGPushManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xiaochao.com.BuildConfig
import xiaochao.com.core.result.AppResult
import xiaochao.com.data.api.ApiRepositoryImpl
import xiaochao.com.data.session.AppSessionStore

object TpnsManager {
    private const val TAG = "TPNS"
    private const val TPNS_FIX_PREF = "tpns_fix"
    private const val KEY_CACHE_RESET_DONE = "cache_reset_done_v1447"
    private val repo = ApiRepositoryImpl()
    @Volatile
    private var lastReportedCid: String = ""

    fun currentToken(): String {
        val appContext = AppSessionStore.getApplicationContextOrNull() ?: return ""
        return runCatching { XGPushConfig.getToken(appContext) }.getOrDefault("")
    }

    fun reportCurrentTokenIfPossible() {
        val token = currentToken()
        if (token.isNotBlank()) {
            reportCidIfPossible(token)
        }
    }

    fun init(context: Context) {
        val appContext = context.applicationContext
        val accessId = BuildConfig.TPNS_ACCESS_ID.trim()
        val accessKey = BuildConfig.TPNS_ACCESS_KEY.trim()
        val secretKey = BuildConfig.TPNS_SECRET_KEY.trim()

        if (accessId.isBlank() || accessKey.isBlank()) {
            Log.w(TAG, "tpns skipped: TPNS_ACCESS_ID or TPNS_ACCESS_KEY empty")
            return
        }

        val accessIdLong = accessId.toLongOrNull()
        if (accessIdLong == null) {
            Log.e(TAG, "tpns skipped: TPNS_ACCESS_ID is not a valid number")
            return
        }

        runCatching {
            fixGuidExpiredSecondsTypeIfNeeded(appContext)
            resetTpnsCacheIfNeeded(appContext)
            XGPushConfig.enableDebug(appContext, true)
            XGPushConfig.setAccessId(appContext, accessIdLong)
            XGPushConfig.setAccessKey(appContext, accessKey)
            Log.d(
                TAG,
                "tpns init start accessId=$accessIdLong accessKey=${mask(accessKey)} secretConfigured=${secretKey.isNotBlank()}"
            )

            XGPushManager.registerPush(appContext, object : XGIOperateCallback {
                override fun onSuccess(data: Any?, flag: Int) {
                    val token = data?.toString().orEmpty()
                    Log.d(TAG, "tpns register success token=$token flag=$flag")
                    if (token.isNotBlank()) {
                        reportCidIfPossible(token)
                    }
                }

                override fun onFail(data: Any?, errCode: Int, msg: String?) {
                    Log.e(TAG, "tpns register fail code=$errCode msg=$msg data=$data")
                }
            })

            val cachedToken = runCatching { XGPushConfig.getToken(appContext) }.getOrNull().orEmpty()
            if (cachedToken.isNotBlank()) {
                Log.d(TAG, "tpns cached token=$cachedToken")
                reportCidIfPossible(cachedToken)
            }
        }.onFailure {
            Log.e(TAG, "tpns init error: ${it.message}", it)
        }
    }

    private fun fixGuidExpiredSecondsTypeIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(".xg.vip.settings.xml", Context.MODE_PRIVATE)
        val value = prefs.all["XG_GUID_EXPIRED_SECONDS"] ?: return
        when (value) {
            is Long -> Unit
            is Int -> {
                prefs.edit().putLong("XG_GUID_EXPIRED_SECONDS", value.toLong()).apply()
                Log.w(TAG, "tpns migrate XG_GUID_EXPIRED_SECONDS int->long")
            }
            is String -> {
                val longValue = value.toLongOrNull()
                if (longValue != null) {
                    prefs.edit().putLong("XG_GUID_EXPIRED_SECONDS", longValue).apply()
                    Log.w(TAG, "tpns migrate XG_GUID_EXPIRED_SECONDS string->long")
                }
            }
            else -> {
                prefs.edit().remove("XG_GUID_EXPIRED_SECONDS").apply()
                Log.w(TAG, "tpns remove invalid XG_GUID_EXPIRED_SECONDS type=${value::class.java.simpleName}")
            }
        }
    }

    private fun reportCidIfPossible(cid: String) {
        if (cid.isBlank() || cid == lastReportedCid) return
        if (AppSessionStore.getUuid().isBlank()) {
            Log.d(TAG, "tpns cid not uploaded: user not logged in")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            when (val result = repo.upsertPushDevice(cid)) {
                is AppResult.Success -> {
                    lastReportedCid = cid
                    Log.d(TAG, "tpns push_device uploaded success token=$cid")
                }
                is AppResult.Error -> {
                    Log.e(TAG, "tpns push_device upload failed code=${result.code} msg=${result.message}")
                }
            }
        }
    }

    private fun resetTpnsCacheIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(TPNS_FIX_PREF, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_CACHE_RESET_DONE, false)) return

        runCatching {
            XGPushConfig.clearAllCache(context)
            prefs.edit().putBoolean(KEY_CACHE_RESET_DONE, true).apply()
            Log.w(TAG, "tpns cache cleared once for v1.4.4.7 type-migration fix")
        }.onFailure {
            Log.e(TAG, "tpns cache clear failed: ${it.message}", it)
        }
    }

    private fun mask(value: String): String {
        if (value.length <= 8) return "****"
        return "${value.take(4)}****${value.takeLast(4)}"
    }
}
