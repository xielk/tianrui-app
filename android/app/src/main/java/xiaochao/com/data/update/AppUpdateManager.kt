package xiaochao.com.data.update

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.xuexiang.xupdate.XUpdate
import com.xuexiang.xupdate._XUpdate
import com.xuexiang.xupdate.entity.UpdateEntity
import com.xuexiang.xupdate.listener.IUpdateParseCallback
import com.xuexiang.xupdate.proxy.IUpdatePrompter
import com.xuexiang.xupdate.proxy.IUpdateProxy
import com.xuexiang.xupdate.proxy.IUpdateParser
import org.json.JSONObject
import xiaochao.com.BuildConfig
import xiaochao.com.data.network.ApiConstants
import java.io.File

object AppUpdateManager {
    private const val UPDATE_PATH = "api/app-update/latest"
    private val UPDATE_URL_BASE = "${ApiConstants.BASE_URL}$UPDATE_PATH"
    private const val API_DEBUG_TAG = "API_DEBUG"
    private var appContext: Context? = null
    @Volatile
    private var latestCheckMode: CheckMode = CheckMode.MANUAL
    private var updateDialog: AlertDialog? = null

    private enum class CheckMode {
        MANUAL,
        SILENT,
    }

    private val updatePrompter = IUpdatePrompter { updateEntity, updateProxy, _ ->
        val context = updateProxy.context
        val activity = context as? Activity
        if (activity == null) {
            Log.e(API_DEBUG_TAG, "app_update prompt_failed reason=context_not_activity")
            return@IUpdatePrompter
        }

        Handler(Looper.getMainLooper()).post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            if (latestCheckMode == CheckMode.SILENT) {
                Log.d(
                    API_DEBUG_TAG,
                    "app_update silent_found_update versionCode=${updateEntity.versionCode} versionName=${updateEntity.versionName}"
                )
                return@post
            }

            updateDialog?.dismiss()

            val force = updateEntity.isForce
            val content = updateEntity.updateContent?.takeIf { it.isNotBlank() } ?: "发现新版本，建议立即升级"
            val title = "发现新版本 ${updateEntity.versionName}".trim()
            val builder = AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(content)
                .setCancelable(!force)
                .setPositiveButton("立即更新") { _, _ ->
                    Log.d(API_DEBUG_TAG, "app_update prompt_confirm_update")
                    updateProxy.startDownload(updateEntity, createDownloadListener())
                }

            if (!force) {
                builder.setNegativeButton("稍后") { _, _ ->
                    Log.d(API_DEBUG_TAG, "app_update prompt_skip_once")
                }
            }

            updateDialog = builder.create().also { dialog ->
                dialog.setCanceledOnTouchOutside(!force)
                dialog.show()
            }
            Log.d(API_DEBUG_TAG, "app_update prompt_show force=$force")
        }
    }

    private val updateParser = object : IUpdateParser {
        override fun parseJson(json: String): UpdateEntity {
            Log.d(API_DEBUG_TAG, "app_update raw_response=$json")
            val root = JSONObject(json)
            val code = root.optIntCompat("Code", "code", default = -1)
            val msg = root.optStringCompat("Msg", "message", default = "")
            val updateStatus = root.optIntCompat("UpdateStatus", "updateStatus", default = 0)

            if (code != 0) {
                val errorMsg = "app_update business_error code=$code msg=$msg"
                Log.e(API_DEBUG_TAG, errorMsg)
                showToastIfNeeded(msg.ifBlank { "查询更新失败" })
                throw IllegalStateException(errorMsg)
            }

            if (updateStatus <= 0) {
                Log.d(API_DEBUG_TAG, "app_update no_update msg=$msg updateStatus=$updateStatus")
                showToastIfNeeded(msg.ifBlank { "已是最新版本" })
                return UpdateEntity().setHasUpdate(false)
            }

            val versionCode = root.optIntCompat("VersionCode", "versionCode", default = 0)
            val versionName = root.optStringCompat("VersionName", "versionName", default = "")
            val modifyContent = root.optStringCompat("ModifyContent", "modifyContent", default = "")
            val downloadUrl = root.optStringCompat("DownloadUrl", "downloadUrl", default = "")
            val apkSize = root.optLongCompat("ApkSize", "apkSize", default = 0L)
            val apkMd5 = root.optStringCompat("ApkMd5", "apkMd5", default = "")
            val force = updateStatus == 2

            if (downloadUrl.isBlank()) {
                val errorMsg = "app_update invalid_payload: empty downloadUrl versionCode=$versionCode"
                Log.e(API_DEBUG_TAG, errorMsg)
                throw IllegalStateException(errorMsg)
            }

            val entity = UpdateEntity()
                .setHasUpdate(true)
                .setForce(force)
                .setIsIgnorable(!force)
                .setVersionCode(versionCode)
                .setVersionName(versionName)
                .setUpdateContent(modifyContent)
                .setDownloadUrl(downloadUrl)
                .setSize(apkSize)
                .setMd5(apkMd5)

            Log.d(
                API_DEBUG_TAG,
                "app_update parsed hasUpdate=true force=$force versionCode=$versionCode versionName=$versionName downloadUrl=$downloadUrl"
            )
            Log.d(API_DEBUG_TAG, "app_update decision should_update=true should_download=true")
            return entity
        }

        override fun parseJson(json: String, callback: IUpdateParseCallback) {
            try {
                callback.onParseResult(parseJson(json))
            } catch (t: Throwable) {
                callback.onParseResult(UpdateEntity().setHasUpdate(false))
                throw t
            }
        }

        override fun isAsyncParser(): Boolean = false
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        val channel = updateChannel()
        XUpdate.get()
            .debug(false)
            .isWifiOnly(false)
            .isGet(true)
            .isAutoMode(false)
            .setIUpdateHttpService(AppUpdateHttpService())
            .setIUpdateParser(updateParser)
            .setIUpdatePrompter(updatePrompter)
            .setOnUpdateFailureListener { error ->
                Log.e(
                    API_DEBUG_TAG,
                    "app_update check_failed code=${error.code} detail=${error.detailMsg} message=${error.message}",
                    error
                )
                showToastIfNeeded(error.message ?: "检查更新失败")
            }
            .param("platform", "android")
            .param("channel", channel)
            .init(context.applicationContext as Application)
        Log.d(API_DEBUG_TAG, "app_update init channel=$channel debug=${BuildConfig.DEBUG}")
    }

    fun check(activity: Activity, silent: Boolean = false) {
        if (BuildConfig.DEBUG) {
            Log.d(API_DEBUG_TAG, "app_update skip_check reason=debug_build")
            if (!silent) {
                showToast("当前是调试包，不执行在线升级")
            }
            return
        }
        latestCheckMode = if (silent) CheckMode.SILENT else CheckMode.MANUAL
        val versionCode = currentVersionCode(activity)
        val channel = updateChannel()
        val url = "$UPDATE_URL_BASE?platform=android&channel=$channel&versionCode=$versionCode"
        Log.d(API_DEBUG_TAG, "app_update request_url=$url")
        Log.d(API_DEBUG_TAG, "app_update trigger=${if (silent) "silent_startup" else "manual_click"}")
        _XUpdate.setCheckUrlStatus(url, false)
        _XUpdate.setIsPrompterShow(url, false)
        Log.d(API_DEBUG_TAG, "app_update state=checking")

        XUpdate.newBuild(activity)
            .updateUrl(url)
            .update()
    }

    private fun updateChannel(): String = if (BuildConfig.DEBUG) "debug" else "stable"

    private fun currentVersionCode(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }

    private fun JSONObject.optIntCompat(primary: String, fallback: String, default: Int): Int {
        return when {
            has(primary) -> optInt(primary, default)
            has(fallback) -> optInt(fallback, default)
            else -> default
        }
    }

    private fun JSONObject.optLongCompat(primary: String, fallback: String, default: Long): Long {
        return when {
            has(primary) -> optLong(primary, default)
            has(fallback) -> optLong(fallback, default)
            else -> default
        }
    }

    private fun JSONObject.optStringCompat(primary: String, fallback: String, default: String): String {
        return when {
            has(primary) -> optString(primary, default)
            has(fallback) -> optString(fallback, default)
            else -> default
        }
    }

    private fun showToast(message: String) {
        val ctx = appContext ?: return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showToastIfNeeded(message: String) {
        if (latestCheckMode == CheckMode.SILENT) return
        showToast(message)
    }

    private fun createDownloadListener(): com.xuexiang.xupdate.service.OnFileDownloadListener {
        return object : com.xuexiang.xupdate.service.OnFileDownloadListener {
            override fun onStart() {
                Log.d(API_DEBUG_TAG, "app_update download_start")
                showToastIfNeeded("发现新版本，开始下载")
            }

            override fun onProgress(progress: Float, total: Long) {
                Log.d(API_DEBUG_TAG, "app_update downloading progress=$progress total=$total")
            }

            override fun onCompleted(file: File): Boolean {
                Log.d(API_DEBUG_TAG, "app_update download_completed path=${file.absolutePath}")
                showToastIfNeeded("下载完成，准备安装")
                return true
            }

            override fun onError(t: Throwable) {
                Log.e(API_DEBUG_TAG, "app_update download_error: ${t.message}", t)
                showToastIfNeeded("下载失败")
            }
        }
    }
}
