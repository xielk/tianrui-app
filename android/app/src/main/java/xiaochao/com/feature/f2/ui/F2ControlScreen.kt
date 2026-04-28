package xiaochao.com.feature.f2.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import xiaochao.com.R
import xiaochao.com.feature.f2.presentation.F2Intent
import xiaochao.com.feature.f2.presentation.F2ViewModel
import xiaochao.com.feature.f2.ui.components.F2MainControlRow
import xiaochao.com.feature.f2.ui.components.F1MapCard
import xiaochao.com.feature.f2.ui.components.F2MapAndMetrics
import xiaochao.com.feature.f2.ui.components.F2StatusIconRow
import xiaochao.com.feature.f2.ui.components.F2TopHeader

@Composable
fun F2ControlScreen(
    vm: F2ViewModel = viewModel(),
    isF1Layout: Boolean = false,
    onNavigateAddVehicle: () -> Unit = {},
    onNavigateSettings: (String) -> Unit = {},
    onNavigateShareUsers: (String) -> Unit = {},
    onNavigateCurrentLocation: (String) -> Unit = {},
    onNavigateTrack: (String) -> Unit = {},
    onNavigateUserCenter: () -> Unit = {},
    onNavigateF1: () -> Unit = {},
    onNavigateF2: () -> Unit = {},
    onNavigateM1B: () -> Unit = {},
    onNavigateBleCalibration: () -> Unit = {},
    onNavigateMoveDevice: () -> Unit = {},
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                vm.dispatch(F2Intent.OnScreenActive)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED
    }

    fun requiredBlePermissions(): Array<String> {
        return requiredControlPermissions(Build.VERSION.SDK_INT, isF1Layout)
    }

    var showPermissionIntroDialog by remember { mutableStateOf(false) }
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }
    var pendingBleConnect by remember { mutableStateOf(false) }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        if (allGranted) {
            if (pendingBleConnect) {
                vm.dispatch(F2Intent.OnBluetoothClick)
            }
            pendingBleConnect = false
        } else {
            pendingBleConnect = false
            val denied = requiredBlePermissions().filterNot { isGranted(it) }
            val hostActivity = context.findActivity()
            val needOpenSettings = denied.any { permission ->
                hostActivity != null && !ActivityCompat.shouldShowRequestPermissionRationale(hostActivity, permission)
            }
            showPermissionDeniedDialog = needOpenSettings
            Toast.makeText(context, "权限未授权，相关服务不可用", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestBleThenConnect() {
        val btManager = context.getSystemService(BluetoothManager::class.java)
        val adapter = btManager?.adapter
        if (adapter == null) {
            Toast.makeText(context, "当前设备不支持蓝牙", Toast.LENGTH_SHORT).show()
            return
        }
        if (!adapter.isEnabled) {
            Toast.makeText(context, "请先打开蓝牙", Toast.LENGTH_SHORT).show()
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }

        val needs = requiredBlePermissions().filterNot { isGranted(it) }
        if (needs.isEmpty()) {
            vm.dispatch(F2Intent.OnBluetoothClick)
        } else {
            pendingBleConnect = true
            showPermissionIntroDialog = true
        }
    }

    LaunchedEffect(isF1Layout) {
        val needs = requiredBlePermissions().filterNot { isGranted(it) }
        if (needs.isNotEmpty()) {
            showPermissionIntroDialog = true
        }
    }

    var vehicleMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var showSosEditor by remember { mutableStateOf(false) }
    var sosInput by remember { mutableStateOf("") }

    LaunchedEffect(uiState.navigateToAddVehicle) {
        if (uiState.navigateToAddVehicle) {
            onNavigateAddVehicle()
            vm.dispatch(F2Intent.OnAddVehicleNavigationConsumed)
        }
    }

    LaunchedEffect(uiState.navigateToSettings) {
        if (uiState.navigateToSettings) {
            onNavigateSettings(uiState.deviceKey)
            vm.dispatch(F2Intent.OnSettingsNavigationConsumed)
        }
    }

    LaunchedEffect(uiState.navigateToShareUsers) {
        if (uiState.navigateToShareUsers) {
            onNavigateShareUsers(uiState.deviceKey)
            vm.dispatch(F2Intent.OnShareNavigationConsumed)
        }
    }

    LaunchedEffect(uiState.navigateToM1B) {
        if (uiState.navigateToM1B) {
            onNavigateM1B()
            vm.dispatch(F2Intent.OnM1BNavigationConsumed)
        }
    }

    LaunchedEffect(uiState.controlModel, isF1Layout) {
        val model = uiState.controlModel.uppercase()
        if (model.isBlank()) return@LaunchedEffect
        when {
            model.contains("M1") -> onNavigateM1B()
            model.contains("F1") && !isF1Layout -> onNavigateF1()
            model.contains("F2") && isF1Layout -> onNavigateF2()
        }
    }

    LaunchedEffect(uiState.tipMessage) {
        uiState.tipMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.dispatch(F2Intent.OnTipConsumed)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFFBFD0EE), Color(0xFFC2D9F1), Color(0xFFB7D3F0))
                )
            )
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
                F2TopHeader(
                    frameNumber = uiState.frameNumber,
                    historyMileKm = uiState.historyMileKm,
                    sosPhone = uiState.sosPhoneMasked,
                    showSos = !isF1Layout,
                    onVehicleClick = { vehicleMenuExpanded = !vehicleMenuExpanded },
                    onSosClick = {
                        sosInput = uiState.sosPhoneMasked.takeIf { it != "--" } ?: ""
                        showSosEditor = true
                    }
                )
                F2StatusIconRow(
                    signalStrengthLevel = uiState.signalStrengthLevel,
                    bleConnected = uiState.bleConnected,
                    bleSystemConnected = uiState.bleSystemConnected,
                    bleConnecting = uiState.bleConnecting,
                    show4gIcon = !isF1Layout,
                    onScanClick = { vm.dispatch(F2Intent.OnScanClick) },
                    onBluetoothClick = { requestBleThenConnect() },
                    onBlePairedClick = onNavigateBleCalibration,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(214.dp)
            ) {
                BatteryPill(
                    percent = uiState.batteryPercent,
                    showLightning = uiState.showLightning,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 2.dp, y = 26.dp)
                )
                if (!isF1Layout) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(y = 22.dp)
                            .alpha(0.95f)
                            .clickable { onNavigateMoveDevice() }
                    ) {
                        Column(
                            modifier = Modifier
                                .background(Brush.horizontalGradient(listOf(Color(0xFF2C315D), Color(0xFF1A1E3A))))
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "挪车",
                                color = Color(0xFF00E94F),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                            Text(
                                text = "电压：${uiState.voltageText}",
                                color = Color(0xFFD7DEEC),
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
                Image(
                    painter = painterResource(id = R.drawable.f2_bike),
                    contentDescription = "bike",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(236.dp)
                        .align(Alignment.BottomCenter)
                        .offset(y = 10.dp)
                )
            }
            F2MainControlRow(
                isLocked = uiState.isLocked,
                isMuteEnabled = uiState.isMuteEnabled,
                isAutoSenseEnabled = uiState.isAutoSenseEnabled,
                hasAnyChannel = uiState.hasAnyChannel,
                isF1Mode = isF1Layout,
                onAutoSenseClick = { vm.dispatch(F2Intent.OnAutoSenseClick) },
                onLockChanged = { locked -> vm.dispatch(F2Intent.OnLockSliderChange(locked)) },
                onMuteClick = { vm.dispatch(F2Intent.OnMuteClick) },
                onFindBikeClick = { vm.dispatch(F2Intent.OnFindBikeClick) },
                onSettingsClick = { vm.dispatch(F2Intent.OnSettingsClick) },
                onShareClick = { vm.dispatch(F2Intent.OnShareClick) },
            )
            if (isF1Layout) {
                F1MapCard(
                    latitude = uiState.latitude,
                    longitude = uiState.longitude,
                    address = uiState.currentLocationAddress,
                    onMapClick = {
                        if (uiState.deviceKey.isNotBlank()) {
                            onNavigateCurrentLocation(uiState.deviceKey)
                        }
                    }
                )
            }
            F2MapAndMetrics(
                gpsLocked = uiState.gpsLocked,
                currentLocationAddress = uiState.currentLocationAddress,
                trackLocationAddress = uiState.trackLocationAddress,
                timeText = uiState.timeText,
                mileageText = uiState.mileageText,
                durationText = uiState.durationText,
                topSpeedText = uiState.topSpeedText,
                avgSpeedText = uiState.avgSpeedText,
                latitude = uiState.latitude,
                longitude = uiState.longitude,
                trackLatitude = uiState.trackLatitude,
                trackLongitude = uiState.trackLongitude,
                showLocationCards = !isF1Layout,
                onCurrentLocationClick = {
                    if (uiState.gpsLocked) {
                        Toast.makeText(context, "GPS 锁定中", Toast.LENGTH_SHORT).show()
                    } else if (uiState.deviceKey.isBlank()) {
                        Toast.makeText(context, "设备不存在", Toast.LENGTH_SHORT).show()
                    } else {
                        onNavigateCurrentLocation(uiState.deviceKey)
                    }
                },
                onHistoryTrackClick = {
                    if (uiState.gpsLocked) {
                        Toast.makeText(context, "GPS 锁定中", Toast.LENGTH_SHORT).show()
                    } else if (uiState.deviceKey.isBlank()) {
                        Toast.makeText(context, "设备不存在", Toast.LENGTH_SHORT).show()
                    } else {
                        onNavigateTrack(uiState.deviceKey)
                    }
                },
            )
        }

        BottomNavMock(
            modifier = Modifier.align(Alignment.BottomCenter),
            onHomeClick = {},
            onMyClick = onNavigateUserCenter,
        )

        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (vehicleMenuExpanded) {
            VehicleDropdownOverlay(
                selectedKey = uiState.deviceKey,
                vehicles = uiState.vehicleOptions,
                onVehicleSelect = { newOption ->
                    vehicleMenuExpanded = false
                    val deviceType = if (newOption.name.startsWith("F1", ignoreCase = true))
                        xiaochao.com.data.model.DeviceType.F1
                    else
                        xiaochao.com.data.model.DeviceType.F2
                    vm.dispatch(
                        F2Intent.OnDeviceSwitch(
                            deviceKey  = newOption.key,
                            deviceType = deviceType,
                        )
                    )
                },
                onAddVehicle = {
                    vehicleMenuExpanded = false
                    vm.dispatch(F2Intent.OnScanClick)
                },
                onDismiss = { vehicleMenuExpanded = false },
            )
        }

    if (showSosEditor) {
            AlertDialog(
                onDismissRequest = { showSosEditor = false },
                title = { Text("设置 SOS 联系人") },
                text = {
                    OutlinedTextField(
                        value = sosInput,
                        onValueChange = { sosInput = it },
                        label = { Text("请输入手机号") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showSosEditor = false
                        vm.dispatch(F2Intent.OnSosSave(sosInput))
                    }) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { showSosEditor = false }) { Text("取消") }
                }
            )
        }
    }

    if (showPermissionIntroDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionIntroDialog = false },
            title = { Text("权限申请") },
            text = {
                val msg = if (isF1Layout) {
                    "F1 页面需要蓝牙与设备定位权限，用于蓝牙连接和定位能力。授权后将弹出系统权限申请。"
                } else {
                    "F2 页面需要蓝牙权限（低版本系统需蓝牙定位权限）用于蓝牙连接能力。授权后将弹出系统权限申请。"
                }
                Text(msg)
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionIntroDialog = false
                    val needs = requiredBlePermissions().filterNot { isGranted(it) }
                    if (needs.isNotEmpty()) {
                        blePermissionLauncher.launch(needs.toTypedArray())
                    }
                }) {
                    Text("继续")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionIntroDialog = false
                    pendingBleConnect = false
                    Toast.makeText(context, "未授权，相关服务不可用", Toast.LENGTH_SHORT).show()
                }) {
                    Text("取消")
                }
            }
        )
    }

    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            title = { Text("权限被拒绝") },
            text = { Text("检测到权限被拒绝且不再询问，请前往系统设置手动开启权限，否则无法使用相关服务。") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDeniedDialog = false
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDeniedDialog = false }) {
                    Text("我知道了")
                }
            }
        )
    }
}

internal fun requiredControlPermissions(apiLevel: Int, isF1Layout: Boolean): Array<String> {
    val permissions = mutableListOf<String>()
    if (apiLevel >= Build.VERSION_CODES.S) {
        permissions += Manifest.permission.BLUETOOTH_SCAN
        permissions += Manifest.permission.BLUETOOTH_CONNECT
    } else {
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
    }
    if (isF1Layout && !permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
    }
    return permissions.toTypedArray()
}

private tailrec fun android.content.Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

@Composable
private fun VehicleDropdownOverlay(
    selectedKey: String,
    vehicles: List<xiaochao.com.feature.f2.presentation.DeviceOption>,
    onVehicleSelect: (xiaochao.com.feature.f2.presentation.DeviceOption) -> Unit,
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
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
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
private fun BatteryPill(percent: Int, showLightning: Boolean, modifier: Modifier = Modifier) {
    val level = percent.coerceIn(0, 100)
    val totalHeight = 92.dp
    val fillHeight = totalHeight * (level / 100f)

    Box(
        modifier = modifier
            .size(width = 30.dp, height = totalHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(listOf(Color(0xFF2B2F59), Color(0xFF080B18))),
                    shape = RoundedCornerShape(22.dp)
                )
        )
        if (level > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 2.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .height(fillHeight.coerceAtLeast(10.dp))
                    .background(
                        brush = Brush.verticalGradient(listOf(Color(0xFFD315F6), Color(0xFF6B0EC8))),
                        shape = RoundedCornerShape(18.dp)
                    )
            )
        }
        if (showLightning) {
            Text(
                text = "⚡",
                color = Color(0xFF8C96AE),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
            )
        }
        Text(
            text = "${level}%",
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
        )
    }
}

@Composable
private fun BottomNavMock(
    modifier: Modifier = Modifier,
    onHomeClick: () -> Unit,
    onMyClick: () -> Unit,
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        containerColor = Color.White,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = true,
            onClick = onHomeClick,
            icon = { BottomTabIcon(selected = true, mine = false) },
            label = { Text("首页", fontSize = 12.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF121A2A),
                selectedTextColor = Color(0xFF3B4B63),
                unselectedIconColor = Color(0xFFC5CFDC),
                unselectedTextColor = Color(0xFFA9B4C4),
                indicatorColor = Color.Transparent,
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = onMyClick,
            icon = { BottomTabIcon(selected = false, mine = true) },
            label = { Text("我的", fontSize = 12.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF121A2A),
                selectedTextColor = Color(0xFF3B4B63),
                unselectedIconColor = Color(0xFFC5CFDC),
                unselectedTextColor = Color(0xFFA9B4C4),
                indicatorColor = Color.Transparent,
            )
        )
    }
}

@Composable
private fun BottomTabIcon(selected: Boolean, mine: Boolean) {
    Image(
        painter = painterResource(
            id = when {
                mine && selected -> R.drawable.nav_mine_active
                mine && !selected -> R.drawable.nav_mine
                !mine && selected -> R.drawable.nav_home_active
                else -> R.drawable.nav_home
            }
        ),
        contentDescription = null,
        modifier = Modifier.size(22.dp)
    )
}
