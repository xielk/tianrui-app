package xiaochao.com.data.push

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log

class TpnsPreInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        runCatching {
            val prefs = ctx.getSharedPreferences(".xg.vip.settings.xml", 0)
            val key = "XG_GUID_EXPIRED_SECONDS"
            when (val value = prefs.all[key]) {
                null -> Unit
                is Long -> Unit
                is Int -> {
                    prefs.edit().putLong(key, value.toLong()).apply()
                    Log.w("TPNS", "tpns preinit migrate XG_GUID_EXPIRED_SECONDS int->long")
                }
                is String -> {
                    val longValue = value.toLongOrNull()
                    if (longValue != null) {
                        prefs.edit().putLong(key, longValue).apply()
                        Log.w("TPNS", "tpns preinit migrate XG_GUID_EXPIRED_SECONDS string->long")
                    } else {
                        prefs.edit().remove(key).apply()
                        Log.w("TPNS", "tpns preinit remove invalid XG_GUID_EXPIRED_SECONDS string")
                    }
                }
                else -> {
                    prefs.edit().remove(key).apply()
                    Log.w("TPNS", "tpns preinit remove invalid XG_GUID_EXPIRED_SECONDS type")
                }
            }
        }.onFailure {
            Log.e("TPNS", "tpns preinit failed: ${it.message}", it)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
