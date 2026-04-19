package xiaochao.com.data.push

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import xiaochao.com.data.session.AppSessionStore

data class PushDeviceMeta(
    val appInstanceId: String,
    val buildModel: String,
    val osVersion: String,
    val deviceName: String,
    val appVersion: String,
    val platform: String,
)

object PushIdentity {
    fun currentMeta(): PushDeviceMeta {
        val context = AppSessionStore.getApplicationContextOrNull()
        val buildModel = Build.MODEL ?: ""
        val deviceName = Build.BRAND ?: ""
        val osVersion = "Android ${Build.VERSION.RELEASE ?: ""}".trim()
        val appVersion = AppSessionStore.getAppVersion()
        val appInstanceId = buildAppInstanceId(context)
        return PushDeviceMeta(
            appInstanceId = appInstanceId,
            buildModel = buildModel,
            osVersion = osVersion,
            deviceName = deviceName,
            appVersion = appVersion,
            platform = "android",
        )
    }

    private fun buildAppInstanceId(context: Context?): String {
        val androidId = context?.let {
            runCatching {
                Settings.Secure.getString(it.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrNull()
        }.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val model = Build.MODEL.orEmpty()
        val board = Build.BOARD.orEmpty()
        val cpuAbi = Build.SUPPORTED_ABIS.firstOrNull()
            ?: runCatching { Build.CPU_ABI }.getOrDefault("")
        val resolution = context?.resources?.displayMetrics?.let { dm ->
            "${dm.widthPixels}x${dm.heightPixels}"
        }.orEmpty()
        val raw = listOf(androidId, brand, model, board, cpuAbi, resolution).joinToString("|")
        return md5(raw)
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
