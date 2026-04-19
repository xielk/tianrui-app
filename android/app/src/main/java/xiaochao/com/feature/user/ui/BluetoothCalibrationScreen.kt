package xiaochao.com.feature.user.ui

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import xiaochao.com.R
import xiaochao.com.core.log.AppLogger
import xiaochao.com.core.result.AppResult
import xiaochao.com.data.api.ApiRepositoryImpl
import xiaochao.com.data.ble.AndroidBleManager
import xiaochao.com.data.ble.BleRepositoryImpl
import xiaochao.com.data.model.ControlCommand
import xiaochao.com.data.session.AppSessionStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon

@Composable
fun BluetoothCalibrationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bleRepo = remember { BleRepositoryImpl(AndroidBleManager.getInstance(AppLogger())) }
    val apiRepo = remember { ApiRepositoryImpl() }
    val deviceKey = AppSessionStore.getLastDeviceKey()

    var showConfirm by remember { mutableStateOf(false) }
    var isCalibrating by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var paired by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (deviceKey.isBlank()) {
            Toast.makeText(context, "请先绑定车辆", Toast.LENGTH_SHORT).show()
            onBack()
            return@LaunchedEffect
        }
        when (val info = apiRepo.fetchDeviceInfo(deviceKey)) {
            is AppResult.Success -> {
                val mac = info.data.bluetoothMacAddress
                if (mac.isNotBlank()) {
                    bleRepo.connectTo(mac)
                    bleRepo.ensureConnectedInBackground()
                    connected = bleRepo.channelAvailability.value.bleAvailable
                    paired = bleRepo.isCurrentDevicePaired()
                    if (!connected || !paired) {
                        Toast.makeText(context, "请先在首页连接并完成蓝牙配对", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                }
            }
            is AppResult.Error -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "返回",
                modifier = Modifier.clickable { onBack() }.size(20.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("蓝牙校准", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text("设置感应距离，让车辆更懂你！", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("每部手机的蓝牙信号都不一样，为了让你的车更聪明地识别你靠近或离开，我们需要先做一次简单的感应校准。", color = Color(0xFF666666), lineHeight = 22.sp)

            Spacer(modifier = Modifier.height(14.dp))
            Text("🚶‍♂️怎么做？", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Step("1️⃣", "走到离车大约 1 米左右的位置", "👣 不用太精确，选一个你觉得刚好能自动解锁的距离就行。")
            Step("2️⃣", "点击【开始校准】按钮", "📲 系统会记录当前距离的蓝牙信号。")
            Step("3️⃣", "听到\"滴滴\"声，表示校准成功啦！ 🎉", "以后靠近或离开车辆时，就能自动解锁 / 上锁。")

            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF8F9FA), RoundedCornerShape(12.dp)).padding(12.dp)) {
                Text("💡温馨提示：", fontWeight = FontWeight.Bold)
                Text("校准时尽量避开人多或信号干扰的地方", color = Color(0xFF666666))
                Text("不满意也没关系，随时可以重新设置哦！", color = Color(0xFF666666))
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = {
                if (isCalibrating || isConnecting) return@Button
                showConfirm = true
            },
            enabled = connected && paired && !isCalibrating && !isConnecting,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (connected && paired) Color(0xFF007AFF) else Color(0xFFBBBBBB)
            ),
            shape = RoundedCornerShape(40.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(
                when {
                    isCalibrating -> "校准中..."
                    isConnecting -> "正在连接蓝牙，请稍后..."
                    else -> "开始校准"
                },
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("校准提示") },
            text = { Text("请走到离车大约1米左右的位置，然后点击确定开始校准") },
            confirmButton = {
                Text(
                    "开始校准",
                    modifier = Modifier.clickable {
                        showConfirm = false
                        if (!connected || !paired) {
                            Toast.makeText(context, "请先在首页连接并完成蓝牙配对", Toast.LENGTH_SHORT).show()
                            return@clickable
                        }
                        isCalibrating = true
                        scope.launch {
                            when (val result = bleRepo.sendCommand(ControlCommand.SensorLevelLocation)) {
                                is AppResult.Success -> {
                                    isCalibrating = false
                                    Toast.makeText(context, "校准成功", Toast.LENGTH_SHORT).show()
                                }
                                is AppResult.Error -> {
                                    isCalibrating = false
                                    Toast.makeText(context, result.message.ifBlank { "校准失败，请重试" }, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    color = Color(0xFF007AFF)
                )
            },
            dismissButton = {
                Text("取消", modifier = Modifier.clickable { showConfirm = false })
            }
        )
    }

    if (isConnecting) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun Step(num: String, title: String, note: String) {
    Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
        Text(num)
        Spacer(modifier = Modifier.size(8.dp))
        Column {
            Text(title)
            Text(note, color = Color(0xFF888888), fontSize = 13.sp)
        }
    }
}
