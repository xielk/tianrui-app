package xiaochao.com.feature.f2.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import xiaochao.com.R
import xiaochao.com.data.model.DeviceType
import xiaochao.com.feature.f2.presentation.F2Intent
import xiaochao.com.feature.f2.presentation.F2ViewModel
import xiaochao.com.feature.f2.presentation.DeviceOption
import xiaochao.com.feature.f2.ui.components.TianDiTuCurrentLocationWebMap

@Composable
fun M1BControlScreen(
    vm: F2ViewModel = viewModel(),
    onNavigateUserCenter: () -> Unit = {},
    onNavigateCurrentLocation: (String) -> Unit = {},
    onNavigateTrack: (String) -> Unit = {},
    onNavigateF1: () -> Unit = {},
    onNavigateF2: () -> Unit = {},
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    var vehicleMenuExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.dispatch(F2Intent.OnRefresh)
    }

    LaunchedEffect(uiState.controlModel) {
        val model = uiState.controlModel.uppercase()
        if (model.isBlank()) return@LaunchedEffect
        when {
            model.contains("F1") -> onNavigateF1()
            model.contains("F2") -> onNavigateF2()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFBFD0EE), Color(0xFFC2D9F1), Color(0xFFB7D3F0))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = 74.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vehicleMenuExpanded = !vehicleMenuExpanded }) {
                        Text(uiState.frameNumber, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0C1222))
                        Image(painter = painterResource(id = R.drawable.f2_arrow_down), contentDescription = null, modifier = Modifier.size(10.dp))
                    }
                    Text("历史总里程：${uiState.historyMileKm}KM", color = Color(0xFF2D3A53), fontSize = 16.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    TopIcon(R.drawable.f2_scan) { vm.dispatch(F2Intent.OnScanClick) }
                    TopIcon(signalRes(uiState.signalStrengthLevel), iconSize = 28.dp) {}
                    TopIcon(R.drawable.f2_info, onClick = onNavigateUserCenter)
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(214.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.f2_bike),
                    contentDescription = "bike",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(236.dp)
                        .align(Alignment.BottomCenter)
                        .offset(y = 10.dp)
                        .alpha(0.8f)
                )
            }

            Card(
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clickable { if (uiState.deviceKey.isNotBlank()) onNavigateCurrentLocation(uiState.deviceKey) }
                        ) {
                            TianDiTuCurrentLocationWebMap(
                                latitude = uiState.latitude,
                                longitude = uiState.longitude,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(Brush.horizontalGradient(listOf(Color(0xFFD500FF), Color(0xFF6D00D6))), RoundedCornerShape(bottomStart = 18.dp))
                                .clickable { if (uiState.deviceKey.isNotBlank()) onNavigateTrack(uiState.deviceKey) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("历史轨迹 >", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("您的当前位置:", color = Color(0xFF8A95A8), fontSize = 14.sp)
                        Text("  ${uiState.currentLocationAddress}", color = Color(0xFF121A2A), fontSize = 14.sp)
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    Metric("${uiState.mileageText}", "里程")
                    Metric(uiState.durationText, "耗时")
                    Metric(uiState.topSpeedText, "极速")
                    Metric(uiState.avgSpeedText, "匀速")
                }
            }
        }

        if (vehicleMenuExpanded) {
            M1BVehicleDropdownOverlay(
                selectedKey = uiState.deviceKey,
                vehicles = uiState.vehicleOptions,
                onVehicleSelect = { newOption ->
                    vehicleMenuExpanded = false
                    val isM1 = newOption.name.uppercase().startsWith("M1")
                    vm.dispatch(F2Intent.OnDeviceSwitch(deviceKey = newOption.key, deviceType = DeviceType.F2))
                    if (!isM1) onNavigateF2()
                },
                onAddVehicle = {
                    vehicleMenuExpanded = false
                    vm.dispatch(F2Intent.OnScanClick)
                },
                onDismiss = { vehicleMenuExpanded = false },
            )
        }

        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            containerColor = Color.White,
            tonalElevation = 0.dp
        ) {
            NavigationBarItem(
                selected = true,
                onClick = {},
                icon = { Image(painter = painterResource(id = R.drawable.nav_home_active), contentDescription = null, modifier = Modifier.size(22.dp)) },
                label = { Text("首页", fontWeight = FontWeight.SemiBold) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )
            NavigationBarItem(
                selected = false,
                onClick = onNavigateUserCenter,
                icon = { Image(painter = painterResource(id = R.drawable.nav_mine), contentDescription = null, modifier = Modifier.size(22.dp)) },
                label = { Text("我的") },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )
        }

        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun M1BVehicleDropdownOverlay(
    selectedKey: String,
    vehicles: List<DeviceOption>,
    onVehicleSelect: (DeviceOption) -> Unit,
    onAddVehicle: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss)
    ) {
        Card(
            modifier = Modifier
                .padding(start = 10.dp, top = 84.dp)
                .width(136.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                if (vehicles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Text("暂无设备", color = Color(0xFF121A2A), fontSize = 16.sp)
                    }
                    HorizontalDivider(color = Color(0xFFF0F2F6))
                } else {
                    vehicles.forEachIndexed { index, item ->
                        val selected = item.key == selectedKey
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onVehicleSelect(item) }
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = item.name.ifBlank { item.key },
                                color = if (selected) Color(0xFFB62CE8) else Color(0xFF121A2A),
                                fontSize = 16.sp,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                        if (index != vehicles.lastIndex) {
                            HorizontalDivider(color = Color(0xFFF0F2F6))
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF0F2F6))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddVehicle)
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text(text = "+ 添加车辆", color = Color(0xFF73859F), fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun TopIcon(iconRes: Int, iconSize: androidx.compose.ui.unit.Dp = 22.dp, onClick: () -> Unit) {
    Box(modifier = Modifier.size(30.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Image(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun Metric(value: String, title: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, color = Color(0xFF141D2F))
        Text(title, color = Color(0xFF707C90))
    }
}

private fun signalRes(level: Int): Int {
    return when (level.coerceIn(0, 5)) {
        0 -> R.drawable.f2_signal0
        1 -> R.drawable.f2_signal1
        2 -> R.drawable.f2_signal2
        3 -> R.drawable.f2_signal3
        4 -> R.drawable.f2_signal4
        else -> R.drawable.f2_signal5
    }
}
