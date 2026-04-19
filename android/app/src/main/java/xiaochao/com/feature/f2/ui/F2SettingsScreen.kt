package xiaochao.com.feature.f2.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import xiaochao.com.data.api.DeviceSettingsData
import xiaochao.com.data.ble.AndroidBleManager
import xiaochao.com.data.ble.BleRepositoryImpl
import xiaochao.com.data.model.ControlCommand
import xiaochao.com.data.model.DeviceType
import xiaochao.com.data.session.AppSessionStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon

/**
 * 与 Vue 设置一致：**每项变更立即生效**，无「立即保存」。
 * - **F1**：仅蓝牙下发（无卫星定位行）；与 [F1.vue]/[SettingsOverlay.vue] 一致。
 * - **F2**：已连蓝牙时先下发与 F1 相同的 BLE 指令，成功后再 `updateDeviceSettings`；未连蓝牙则只走 API。
 */
@Composable
fun F2SettingsScreen(
    deviceKey: String,
    onBack: () -> Unit,
    deviceType: DeviceType = DeviceType.F2,
    onNavigateBleCalibration: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ApiRepositoryImpl() }
    val bleRepo = remember { BleRepositoryImpl(AndroidBleManager.getInstance(AppLogger())) }

    var loading by remember { mutableIntStateOf(1) }
    var sensitivityDistance by remember { mutableFloatStateOf(0f) }
    var sensitivityDistance2 by remember { mutableFloatStateOf(0f) }
    var alarmSensitivity by remember { mutableIntStateOf(2) }
    var autoShutdownTime by remember { mutableIntStateOf(0) }
    var gpsLock by remember { mutableIntStateOf(0) }
    var alarmSoundVolume by remember { mutableFloatStateOf(10f) }

    val isF1 = deviceType == DeviceType.F1
    val hiddenVolumeEnabled = remember {
        if (deviceType != DeviceType.F2) return@remember false
        val json = AppSessionStore.getUserInfoJson()
        val phone = Regex("\"phone\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.getOrNull(1).orEmpty()
        phone.takeLast(4) == "5533"
    }
    val bg = Brush.verticalGradient(listOf(Color(0xFFEAF4FF), Color(0xFFF6FAFF), Color(0xFFF6FAFF)))
    val lightTrack = Color(0xFFEDEFFC)

    fun toast(message: String) {
        android.widget.Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun buildSettingsPayload(): DeviceSettingsData = DeviceSettingsData(
        sensitivityDistance = sensitivityDistance.toInt().coerceIn(0, 8),
        sensitivityDistance2 = sensitivityDistance2.toInt().coerceIn(0, 8),
        alarmSensitivity = when (alarmSensitivity.coerceIn(0, 2)) {
            0 -> "low"
            1 -> "medium"
            else -> "high"
        },
        autoShutdownTime = autoShutdownTime,
        gpsLock = gpsLock,
    )

    suspend fun applyF1BleOnly(cmd: ControlCommand, successToast: String = "设置成功") {
        when (val r = bleRepo.sendCommand(cmd)) {
            is AppResult.Success -> toast(successToast)
            is AppResult.Error -> toast(r.message.ifBlank { "设置失败" })
        }
    }

    /** F2 且已连蓝牙：BLE 成功后再同步接口；[onBleFail] 在 BLE 失败时回滚 UI */
    suspend fun applyF2BleThenSyncApi(cmd: ControlCommand, onBleFail: (() -> Unit)? = null) {
        when (val ble = bleRepo.sendCommand(cmd)) {
            is AppResult.Success -> {
                when (val api = repo.updateDeviceSettings(deviceKey, buildSettingsPayload())) {
                    is AppResult.Success -> toast("设置成功")
                    is AppResult.Error -> toast("蓝牙已生效，云端：${api.message.ifBlank { "同步失败" }}")
                }
            }
            is AppResult.Error -> {
                onBleFail?.invoke()
                toast(ble.message.ifBlank { "设置失败" })
            }
        }
    }

    suspend fun syncF2SettingsApiOnly(onFailRevert: (() -> Unit)? = null) {
        when (val api = repo.updateDeviceSettings(deviceKey, buildSettingsPayload())) {
            is AppResult.Success -> toast("设置成功")
            is AppResult.Error -> {
                onFailRevert?.invoke()
                toast(api.message.ifBlank { "设置失败" })
            }
        }
    }

    fun requireBleForF1(): Boolean {
        if (!bleRepo.channelAvailability.value.bleAvailable) {
            toast("请先连接蓝牙")
            return false
        }
        return true
    }

    fun requireBleForVolumeOrToast(): Boolean {
        if (!bleRepo.channelAvailability.value.bleAvailable) {
            toast("请先连接蓝牙")
            return false
        }
        return true
    }

    LaunchedEffect(deviceKey) {
        loading = 1
        when (val result = repo.fetchDeviceSettings(deviceKey)) {
            is AppResult.Success -> {
                val s = result.data
                sensitivityDistance = s.sensitivityDistance.coerceIn(0, 8).toFloat()
                sensitivityDistance2 = s.sensitivityDistance2.coerceIn(0, 8).toFloat()
                alarmSensitivity = when (s.alarmSensitivity) {
                    "low" -> 0
                    "medium" -> 1
                    else -> 2
                }
                autoShutdownTime = when (s.autoShutdownTime) {
                    3, 5, 10 -> s.autoShutdownTime
                    else -> 0
                }
                gpsLock = if (s.gpsLock == 1) 1 else 0
                loading = 0
            }

            is AppResult.Error -> {
                loading = 0
                toast(result.message.ifBlank { "获取设置失败" })
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 28.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.clickable { onBack() }.size(20.dp)
                )
                Text(
                    text = "设置",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.size(34.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (!isF1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "设备编号: $deviceKey", fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "卫星定位:", color = Color(0xFF7C8894), fontSize = 14.sp)
                        Switch(
                            checked = gpsLock == 0,
                            onCheckedChange = { checked ->
                                val next = if (checked) 0 else 1
                                val prev = gpsLock
                                gpsLock = next
                                scope.launch {
                                    when (val result = repo.setGpsLock(deviceKey, next)) {
                                        is AppResult.Success -> toast(if (next == 1) "GPS已关闭" else "GPS已开启")
                                        is AppResult.Error -> {
                                            gpsLock = prev
                                            toast(result.message.ifBlank { "GPS设置失败" })
                                        }
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color.Black,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFD8DEE7),
                            )
                        )
                        Text(text = if (gpsLock == 0) "打开" else "关闭", fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (isF1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "设备编号: $deviceKey", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            SettingCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = ">>>> 解防感应距离: ${sensitivityDistance.toInt() + 1}档", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    if (isF1 && onNavigateBleCalibration != null) {
                        Text(
                            text = "感应更精确",
                            fontSize = 14.sp,
                            color = Color(0xFF007AFF),
                            modifier = Modifier.clickable { onNavigateBleCalibration.invoke() }
                        )
                    } else {
                        Text(text = "感应更精确", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Slider(
                    value = sensitivityDistance,
                    onValueChange = { sensitivityDistance = it.coerceIn(0f, 8f) },
                    onValueChangeFinished = {
                        if (loading != 0) return@Slider
                        val level = sensitivityDistance.toInt().coerceIn(0, 8)
                        scope.launch {
                            when (deviceType) {
                                DeviceType.F1 -> {
                                    if (!requireBleForF1()) return@launch
                                    applyF1BleOnly(ControlCommand.SetBleDisarmSensorDistance(level))
                                }
                                DeviceType.F2 -> {
                                    if (bleRepo.channelAvailability.value.bleAvailable) {
                                        applyF2BleThenSyncApi(ControlCommand.SetBleDisarmSensorDistance(level))
                                    } else {
                                        syncF2SettingsApiOnly()
                                    }
                                }
                            }
                        }
                    },
                    valueRange = 0f..8f,
                    steps = 7,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Black,
                        activeTrackColor = Color.Black,
                        inactiveTrackColor = lightTrack,
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("近", color = Color(0xFF9BA6B2))
                    Text("远", color = Color(0xFF9BA6B2))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            SettingCard {
                Text(text = ">>>> 设防感应距离: ${sensitivityDistance2.toInt() + 1}档", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(14.dp))
                Slider(
                    value = sensitivityDistance2,
                    onValueChange = { sensitivityDistance2 = it.coerceIn(0f, 8f) },
                    onValueChangeFinished = {
                        if (loading != 0) return@Slider
                        val level = sensitivityDistance2.toInt().coerceIn(0, 8)
                        scope.launch {
                            when (deviceType) {
                                DeviceType.F1 -> {
                                    if (!requireBleForF1()) return@launch
                                    applyF1BleOnly(ControlCommand.SetBleArmSensorDistance(level))
                                }
                                DeviceType.F2 -> {
                                    if (bleRepo.channelAvailability.value.bleAvailable) {
                                        applyF2BleThenSyncApi(ControlCommand.SetBleArmSensorDistance(level))
                                    } else {
                                        syncF2SettingsApiOnly()
                                    }
                                }
                            }
                        }
                    },
                    valueRange = 0f..8f,
                    steps = 7,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Black,
                        activeTrackColor = Color.Black,
                        inactiveTrackColor = lightTrack,
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("近", color = Color(0xFF9BA6B2))
                    Text("远", color = Color(0xFF9BA6B2))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            SettingCard {
                Text(
                    text = ">>>> 报警灵敏度: ${listOf("低", "中", "高")[alarmSensitivity.coerceIn(0, 2)]}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (index <= alarmSensitivity) Color.Black else lightTrack)
                                .clickable {
                                    if (loading != 0) return@clickable
                                    val prev = alarmSensitivity
                                    alarmSensitivity = index
                                    scope.launch {
                                        when (deviceType) {
                                            DeviceType.F1 -> {
                                                if (!requireBleForF1()) {
                                                    alarmSensitivity = prev
                                                    return@launch
                                                }
                                                when (val res = bleRepo.sendCommand(ControlCommand.SetBleAlarmSensitivity(index))) {
                                                    is AppResult.Success -> toast("设置成功")
                                                    is AppResult.Error -> {
                                                        alarmSensitivity = prev
                                                        toast(res.message.ifBlank { "设置失败" })
                                                    }
                                                }
                                            }
                                            DeviceType.F2 -> {
                                                if (bleRepo.channelAvailability.value.bleAvailable) {
                                                    applyF2BleThenSyncApi(
                                                        ControlCommand.SetBleAlarmSensitivity(index),
                                                        onBleFail = { alarmSensitivity = prev },
                                                    )
                                                } else {
                                                    syncF2SettingsApiOnly(onFailRevert = { alarmSensitivity = prev })
                                                }
                                            }
                                        }
                                    }
                                }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("低", color = Color(0xFF9BA6B2))
                    Text("中", color = Color(0xFF9BA6B2))
                    Text("高", color = Color(0xFF9BA6B2))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            SettingCard {
                Text(text = ">>>> 自动关机时间", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(0 to "禁用", 3 to "3分钟", 5 to "5分钟", 10 to "10分钟").forEach { option ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (autoShutdownTime == option.first) Color(0xFF0E1627) else lightTrack)
                                .clickable {
                                    if (loading != 0) return@clickable
                                    val m = option.first
                                    if (m !in listOf(0, 3, 5, 10)) return@clickable
                                    val prev = autoShutdownTime
                                    autoShutdownTime = m
                                    scope.launch {
                                        when (deviceType) {
                                            DeviceType.F1 -> {
                                                if (!requireBleForF1()) {
                                                    autoShutdownTime = prev
                                                    return@launch
                                                }
                                                when (val res = bleRepo.sendCommand(ControlCommand.SetBleAutoShutdown(m))) {
                                                    is AppResult.Success -> toast("设置成功")
                                                    is AppResult.Error -> {
                                                        autoShutdownTime = prev
                                                        toast(res.message.ifBlank { "设置失败" })
                                                    }
                                                }
                                            }
                                            DeviceType.F2 -> {
                                                if (bleRepo.channelAvailability.value.bleAvailable) {
                                                    applyF2BleThenSyncApi(
                                                        ControlCommand.SetBleAutoShutdown(m),
                                                        onBleFail = { autoShutdownTime = prev },
                                                    )
                                                } else {
                                                    syncF2SettingsApiOnly(onFailRevert = { autoShutdownTime = prev })
                                                }
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.second,
                                color = if (autoShutdownTime == option.first) Color.White else Color(0xFF74808A),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // 隐藏功能：仅 F2 且手机号尾号 5533 显示；仅蓝牙下发生效，不同步 API。
            if (hiddenVolumeEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                SettingCard {
                    Text(
                        text = ">>>> 报警提示音量: ${alarmSoundVolume.toInt().coerceIn(0, 10)}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Slider(
                        value = alarmSoundVolume,
                        onValueChange = { alarmSoundVolume = it.coerceIn(0f, 10f) },
                        onValueChangeFinished = {
                            if (loading != 0) return@Slider
                            val prev = alarmSoundVolume
                            val v = alarmSoundVolume.toInt().coerceIn(0, 10)
                            scope.launch {
                                if (!requireBleForVolumeOrToast()) {
                                    alarmSoundVolume = prev
                                    return@launch
                                }
                                when (val res = bleRepo.sendCommand(ControlCommand.SetBleAlarmSoundVolume(v))) {
                                    is AppResult.Success -> toast("设置成功")
                                    is AppResult.Error -> {
                                        alarmSoundVolume = prev
                                        toast(res.message.ifBlank { "设置失败" })
                                    }
                                }
                            }
                        },
                        valueRange = 0f..10f,
                        steps = 9,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Black,
                            activeTrackColor = Color.Black,
                            inactiveTrackColor = lightTrack,
                        )
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("静音", color = Color(0xFF9BA6B2))
                        Text("最大", color = Color(0xFF9BA6B2))
                    }
                }
            }
        }

    }
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(16.dp),
        content = content
    )
}
