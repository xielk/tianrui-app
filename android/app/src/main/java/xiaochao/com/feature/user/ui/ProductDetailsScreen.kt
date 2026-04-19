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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import xiaochao.com.R
import xiaochao.com.core.result.AppResult
import xiaochao.com.data.api.ApiRepositoryImpl
import xiaochao.com.data.network.DeviceInfoDto
import xiaochao.com.data.session.AppSessionStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon

@Composable
fun ProductDetailsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ApiRepositoryImpl() }
    val deviceKey = AppSessionStore.getLastDeviceKey()

    var info by remember { mutableStateOf<DeviceInfoDto?>(null) }

    LaunchedEffect(deviceKey) {
        if (deviceKey.isBlank()) {
            Toast.makeText(context, "请先绑定设备", Toast.LENGTH_SHORT).show()
            onBack()
            return@LaunchedEffect
        }
        when (val res = repo.fetchDeviceInfo(deviceKey)) {
            is AppResult.Success -> info = res.data
            is AppResult.Error -> Toast.makeText(context, res.message.ifBlank { "加载车辆信息失败" }, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEAF4FF))
            .verticalScroll(rememberScrollState())
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "返回",
                modifier = Modifier.clickable { onBack() }.size(24.dp)
            )
            Text("车辆详情", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
        }

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(id = R.drawable.user_about), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" 车辆参数", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Param(info?.deviceModel?.length, "CM", "长")
                    Param(info?.deviceModel?.width, "CM", "宽")
                    Param(info?.deviceModel?.height, "CM", "高")
                    Param(info?.deviceModel?.weight, "KG", "重")
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(info?.deviceModel?.motorPower, "W", "电机功率")
                    Stat(info?.deviceModel?.range, "KM", "续航里程")
                    Stat(info?.deviceModel?.batteryCapacity, "Ah", "电池容量")
                }
            }
            Image(
                painter = painterResource(id = R.drawable.f2_bike),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(220.dp)
                    .padding(end = 8.dp, top = 14.dp)
                    .background(Color.Transparent)
            )
        }

        Card(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
        ) {
            SectionTitle("产品信息")
            InfoRow("车架号", info?.frameNo?.ifBlank { "--" } ?: "--")
            InfoRow("车型", info?.deviceModel?.modelName?.ifBlank { info?.deviceModel?.controlModel } ?: "--")

            SectionTitle("中控信息")
            InfoRow("硬件版本号", info?.iotVersion?.ifBlank { "--" } ?: "--")
            InfoRow("软件版本号", AppSessionStore.getAppVersion().ifBlank { "--" })
            InfoRow("IMEI", info?.imei?.ifBlank { "--" } ?: "--")
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painter = painterResource(id = R.drawable.user_about), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(" $title", fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFF6D7D8B), fontSize = 16.sp)
        Text(value, color = Color(0xFF040407), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
private fun Param(value: String?, unit: String, title: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(74.dp)
            .background(Color(0x192E64D1), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(title, color = Color(0xFF6D7D8B), fontSize = 14.sp)
        Text("${asDisplayNumber(value)}$unit", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun Stat(value: String?, unit: String, title: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        Text("${asDisplayNumber(value)}$unit", color = Color(0xFF991AB5), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            title,
            color = Color(0xFF6D7D8B),
            fontSize = 13.sp,
            modifier = Modifier
                .background(Color(0x192E64D1), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

private fun asDisplayNumber(raw: String?): String {
    val v = raw?.trim().orEmpty()
    if (v.isEmpty()) return "--"
    val d = v.toDoubleOrNull() ?: return v
    return if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()
}
