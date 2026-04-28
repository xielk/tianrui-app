package xiaochao.com.feature.user.ui

import android.app.Activity
import android.util.Log
import android.widget.Toast
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import xiaochao.com.R
import xiaochao.com.data.auth.AuthRepository
import xiaochao.com.data.session.AppSessionStore
import xiaochao.com.data.update.AppUpdateManager

@Composable
fun UserCenterScreen(
    onGoHome: () -> Unit,
    onGoAddVehicle: () -> Unit,
    onGoProductDetails: () -> Unit,
    onGoBleCalibration: () -> Unit,
    onGoOtaUpdate: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authRepository = remember { AuthRepository() }
    var loading by remember { mutableStateOf(false) }

    val phone = extractPhone(AppSessionStore.getUserInfoJson()).ifBlank { "未登录" }
    val modelType = AppSessionStore.getModelType().uppercase()
    val isM1 = modelType.startsWith("M1")

    val menuItems = if (isM1) {
        listOf(
            MenuItem("产品详情", R.drawable.user_about),
            MenuItem("检查APP更新", R.drawable.user_about),
        )
    } else {
        listOf(
            MenuItem("绑定车辆", R.drawable.user_link),
            MenuItem("选择电池类型", R.drawable.user_about),
            MenuItem("产品详情", R.drawable.user_about),
            MenuItem("检查APP更新", R.drawable.user_about),
//            MenuItem("检查OTA更新", R.drawable.user_about),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.user_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 88.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x3394A8C6))
                        .clickable(enabled = !loading) {
                            if (loading) return@clickable
                            loading = true
                            scope.launch {
                                authRepository.logout()
                                loading = false
                                Toast.makeText(context, "退出登录成功", Toast.LENGTH_SHORT).show()
                                onLoggedOut()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.user_logout),
                        contentDescription = "logout",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.user_phone),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = phone,
                    color = Color(0xFF12A887),
                    style = TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                    fontSize = 14.sp,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            menuItems.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .clickable {
                            when (item.title) {
                                "绑定车辆" -> onGoAddVehicle()
                                "产品详情" -> onGoProductDetails()
                                "蓝牙感应距离校准" -> {
                                    if (isBlePaired(context, AppSessionStore.getLastBluetoothAddress())) {
                                        onGoBleCalibration()
                                    } else {
                                        Toast.makeText(context, "请先在首页连接并完成蓝牙配对", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                "检查OTA更新" -> onGoOtaUpdate()
                                "检查APP更新" -> {
                                    val activity = context as? Activity
                                    if (activity == null) {
                                        Toast.makeText(context, "当前页面无法检查更新", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "正在检查更新", Toast.LENGTH_SHORT).show()
                                        runCatching { AppUpdateManager.check(activity) }
                                            .onFailure {
                                                Log.e("API_DEBUG", "app_update click_check_failed: ${it.message}", it)
                                                Toast.makeText(context, "检查更新失败", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                }
                                else -> Toast.makeText(context, "${item.title} 开发中", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(item.title, fontSize = 15.sp, color = Color(0xFF0B1324), modifier = Modifier.weight(1f))
                    Image(
                        painter = painterResource(id = R.drawable.user_arrow),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        NavigationBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            containerColor = Color.White,
            tonalElevation = 0.dp,
        ) {
            NavigationBarItem(
                selected = false,
                onClick = onGoHome,
                icon = {
                    Image(painter = painterResource(id = R.drawable.nav_home), contentDescription = null, modifier = Modifier.size(22.dp))
                },
                label = { Text("首页") },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )
            NavigationBarItem(
                selected = true,
                onClick = {},
                icon = {
                    Image(painter = painterResource(id = R.drawable.nav_mine_active), contentDescription = null, modifier = Modifier.size(22.dp))
                },
                label = { Text("我的", fontWeight = FontWeight.SemiBold) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )
        }
    }
}

private data class MenuItem(val title: String, val iconRes: Int)

private fun extractPhone(userInfoJson: String): String {
    val regex = Regex("\"phone\"\\s*:\\s*\"([^\"]+)\"")
    return regex.find(userInfoJson)?.groupValues?.getOrNull(1).orEmpty()
}

private fun isBlePaired(context: android.content.Context, macAddress: String): Boolean {
    if (macAddress.isBlank()) return false
    val manager = context.getSystemService(BluetoothManager::class.java) ?: return false
    val adapter = manager.adapter ?: return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return false
    }
    val device = runCatching { adapter.getRemoteDevice(macAddress) }.getOrNull() ?: return false
    return device.bondState == BluetoothDevice.BOND_BONDED
}
