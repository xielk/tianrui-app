package xiaochao.com

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.amap.api.maps2d.MapsInitializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xiaochao.com.app.navigation.AppNavGraph
import xiaochao.com.data.auth.NumberAuthManager
import xiaochao.com.data.push.TpnsManager
import xiaochao.com.data.session.AppSessionStore
import xiaochao.com.data.update.AppUpdateManager
import xiaochao.com.ui.theme.XiaochaoTheme

class MainActivity : ComponentActivity() {
    private var checkedUpdateInThisLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Xiaochao) // 恢复正常主题，移除启动页背景
        super.onCreate(savedInstanceState)
        // 2D SDK 6.0.0 可能不支持隐私合规接口，如果编译失败请注释
        // MapsInitializer.updatePrivacyShow(this, true, true)
        // MapsInitializer.updatePrivacyAgree(this, true)
        AppSessionStore.init(applicationContext)
        NumberAuthManager.prepare(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) {
            TpnsManager.init(applicationContext)
        }
        AppUpdateManager.init(applicationContext)
        setContent {
            XiaochaoTheme {
                AppNavGraph()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!checkedUpdateInThisLaunch && AppSessionStore.getUuid().isNotBlank()) {
            checkedUpdateInThisLaunch = true
            lifecycleScope.launch {
                delay(1500)
                runCatching { AppUpdateManager.check(this@MainActivity, silent = true) }
            }
        }
    }
}
