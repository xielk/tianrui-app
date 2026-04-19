package xiaochao.com.data.session

import android.content.Context
import org.json.JSONObject

/** 本机最近一次从蓝牙通知或用户操作得到的控制类 UI状态（按设备），用于冷启动时纠正尚未同步到服务端的 lock /静音 / 感应。 */
data class BleControlUiSnapshot(
    val isLocked: Boolean,
    val isMuteEnabled: Boolean,
    val isAutoSenseEnabled: Boolean,
)

object AppSessionStore : SessionStore {
    private const val PREFS_NAME = "AppPrefs"
    private const val KEY_UUID = "uuid"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_INFO = "userInfo"
    private const val KEY_LAST_DEVICE_KEY = "lastDeviceKey"
    private const val KEY_LAST_BLUETOOTH_ADDRESS = "lastBluetoothAddress"
    private const val KEY_MODEL_TYPE = "modelTypes"
    private const val KEY_AGREED_TO_TERMS = "agreedToTerms"
    private const val KEY_BLE_CONTROL_BY_DEVICE = "bleControlSnapshotByDevice"

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getUuid(): String = if (::appContext.isInitialized) {
        prefs().getString(KEY_UUID, "") ?: ""
    } else {
        ""
    }

    override fun setUuid(uuid: String) {
        if (!::appContext.isInitialized) return
        prefs().edit().putString(KEY_UUID, uuid).apply()
    }

    override fun getToken(): String = if (::appContext.isInitialized) {
        prefs().getString(KEY_TOKEN, "") ?: ""
    } else {
        ""
    }

    override fun setToken(token: String) {
        if (!::appContext.isInitialized) return
        prefs().edit().putString(KEY_TOKEN, token).apply()
    }

    override fun getUserInfoJson(): String = if (::appContext.isInitialized) {
        prefs().getString(KEY_USER_INFO, "") ?: ""
    } else {
        ""
    }

    override fun setUserInfoJson(json: String) {
        if (!::appContext.isInitialized) return
        prefs().edit().putString(KEY_USER_INFO, json).apply()
    }

    override fun getLastDeviceKey(): String = if (::appContext.isInitialized) {
        prefs().getString(KEY_LAST_DEVICE_KEY, "") ?: ""
    } else {
        ""
    }

    override fun setLastDeviceKey(deviceKey: String) {
        if (!::appContext.isInitialized) return
        prefs().edit().putString(KEY_LAST_DEVICE_KEY, deviceKey).apply()
    }

    override fun getLastBluetoothAddress(): String = if (::appContext.isInitialized) {
        prefs().getString(KEY_LAST_BLUETOOTH_ADDRESS, "") ?: ""
    } else {
        ""
    }

    override fun setLastBluetoothAddress(address: String) {
        if (!::appContext.isInitialized) return
        prefs().edit().putString(KEY_LAST_BLUETOOTH_ADDRESS, address).apply()
    }

    override fun isAgreedToTerms(): Boolean = if (::appContext.isInitialized) {
        prefs().getBoolean(KEY_AGREED_TO_TERMS, false)
    } else {
        false
    }

    override fun setAgreedToTerms(agreed: Boolean) {
        if (!::appContext.isInitialized) return
        prefs().edit().putBoolean(KEY_AGREED_TO_TERMS, agreed).apply()
    }

    fun getAppVersion(): String {
        if (!::appContext.isInitialized) return ""
        return try {
            val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            "${packageInfo.versionName}-${packageInfo.longVersionCode}"
        } catch (_: Exception) {
            ""
        }
    }

    fun getApplicationContextOrNull(): Context? {
        return if (::appContext.isInitialized) appContext else null
    }

    fun getModelType(): String = if (::appContext.isInitialized) {
        prefs().getString(KEY_MODEL_TYPE, "") ?: ""
    } else {
        ""
    }

    fun setModelType(modelType: String) {
        if (!::appContext.isInitialized) return
        prefs().edit().putString(KEY_MODEL_TYPE, modelType).apply()
    }

    fun clearLoginSession() {
        if (!::appContext.isInitialized) return
        prefs().edit()
            .putString(KEY_UUID, "")
            .putString(KEY_TOKEN, "")
            .putString(KEY_USER_INFO, "")
            .putString(KEY_LAST_DEVICE_KEY, "")
            .putString(KEY_LAST_BLUETOOTH_ADDRESS, "")
            .putString(KEY_MODEL_TYPE, "")
            .remove(KEY_BLE_CONTROL_BY_DEVICE)
            .apply()
    }

    fun getBleControlSnapshot(deviceKey: String): BleControlUiSnapshot? {
        if (!::appContext.isInitialized || deviceKey.isBlank()) return null
        return try {
            val root = JSONObject(prefs().getString(KEY_BLE_CONTROL_BY_DEVICE, null) ?: return null)
            if (!root.has(deviceKey)) return null
            val o = root.getJSONObject(deviceKey)
            BleControlUiSnapshot(
                isLocked = o.getBoolean("l"),
                isMuteEnabled = o.getBoolean("m"),
                isAutoSenseEnabled = o.getBoolean("a"),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun putBleControlSnapshot(deviceKey: String, locked: Boolean, mute: Boolean, autoSense: Boolean) {
        if (!::appContext.isInitialized || deviceKey.isBlank()) return
        try {
            val raw = prefs().getString(KEY_BLE_CONTROL_BY_DEVICE, null)
            val root = if (raw.isNullOrEmpty()) JSONObject() else JSONObject(raw)
            val o = JSONObject()
            o.put("l", locked)
            o.put("m", mute)
            o.put("a", autoSense)
            root.put(deviceKey, o)
            prefs().edit().putString(KEY_BLE_CONTROL_BY_DEVICE, root.toString()).apply()
        } catch (_: Exception) {
            // ignore corrupt json
        }
    }
}
