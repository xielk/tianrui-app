package xiaochao.com.feature.user.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import xiaochao.com.core.log.AppLogger
import xiaochao.com.data.ble.AndroidBleManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import xiaochao.com.data.session.AppSessionStore
import xiaochao.com.R
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private data class OtaPackageOption(
    val label: String,
    val modelPrefix: String,
    val version: String,
    val url: String,
)

private const val OTA_UI_TAG = "OTA-FR8010-UI"

@Composable
fun OtaUpdateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bluetoothMac = AppSessionStore.getLastBluetoothAddress()
    val otaRunner = remember { Fr8010OtaRunner(context) }
    val bleManager = remember { AndroidBleManager.getInstance(AppLogger()) }
    val localModelType = AppSessionStore.getModelType().uppercase()
    val otaOptions = remember {
        listOf(
            OtaPackageOption(
                label = "F1 固件 v1.2.0",
                modelPrefix = "F1",
                version = "1.2.0",
                url = "https://cdn.tr.sheyutech.com/upload/device_firmware/TR_H810x_F1_ota_v1.2.0.bin",
            ),
            OtaPackageOption(
                label = "F1 固件 v1.3.0",
                modelPrefix = "F1",
                version = "1.3.0",
                url = "https://cdn.tr.sheyutech.com/upload/device_firmware/TR_H810x_F1_ota_v1.3.0.bin",
            ),
            OtaPackageOption(
                label = "F1 固件 v1.3.1",
                modelPrefix = "F1",
                version = "1.3.1",
                url = "https://cdn.tr.sheyutech.com/upload/device_firmware/TR_H810x_F1_ota_v1.3.1.bin",
            ),
            OtaPackageOption(
                label = "F2 固件 v1.3.0",
                modelPrefix = "F2",
                version = "1.3.0",
                url = "http://cdn.tr.sheyutech.com/upload/device_firmware/TR_H810x_F2_ota_v1.3.0.bin",
            ),
        )
    }
    val defaultIndex = remember(localModelType) {
        otaOptions.indexOfFirst { localModelType.startsWith(it.modelPrefix) }.takeIf { it >= 0 } ?: 0
    }
    var selectedOptionIndex by remember { mutableIntStateOf(defaultIndex) }

    var currentVersion by remember { mutableStateOf("-") }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var progressText by remember { mutableStateOf("等待开始") }
    var showConsent by remember { mutableStateOf(false) }
    var bleConnected by remember { mutableStateOf(false) }
    var hasBlePermission by remember { mutableStateOf(false) }

    fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED
    }
    fun requiredBlePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }
    }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasBlePermission = result.values.all { it }
        if (!hasBlePermission) {
            Toast.makeText(context, "蓝牙权限未授权，请在系统设置开启", Toast.LENGTH_SHORT).show()
        }
    }

    val selectedOption = otaOptions[selectedOptionIndex]
    val bleIcon = when {
        loading -> R.drawable.ble_connecting
        bleConnected -> R.drawable.ble_successful
        else -> R.drawable.ble_wait
    }

    LaunchedEffect(Unit) {
        hasBlePermission = requiredBlePermissions().all { isGranted(it) }
        if (!hasBlePermission) {
            val needs = requiredBlePermissions().filterNot { isGranted(it) }
            if (needs.isNotEmpty()) {
                blePermissionLauncher.launch(needs.toTypedArray())
            }
        }
        scope.launch {
            bleManager.availability.collectLatest { availability ->
                bleConnected = availability.bleAvailable
            }
        }
        // 进入 OTA 页不主动断开、不主动重连。
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.clickable { onBack() }.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("OTA 更新", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = bleIcon),
                contentDescription = "蓝牙状态",
                modifier = Modifier
                    .size(28.dp)
                    .clickable {
                        val btManager = context.getSystemService(BluetoothManager::class.java)
                        val adapter = btManager?.adapter
                        if (adapter == null) {
                            Toast.makeText(context, "当前设备不支持蓝牙", Toast.LENGTH_SHORT).show()
                            return@clickable
                        }
                        if (!adapter.isEnabled) {
                            Toast.makeText(context, "请先打开蓝牙", Toast.LENGTH_SHORT).show()
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                            return@clickable
                        }
                        val needs = requiredBlePermissions().filterNot { isGranted(it) }
                        if (needs.isNotEmpty()) {
                            blePermissionLauncher.launch(needs.toTypedArray())
                            return@clickable
                        }
                        if (bluetoothMac.isBlank()) {
                            Toast.makeText(context, "未找到蓝牙 MAC 地址", Toast.LENGTH_SHORT).show()
                            return@clickable
                        }
                        scope.launch {
                            loading = true
                            runCatching {
                                currentVersion = otaRunner.readFirmwareVersion(bluetoothMac)
                                bleConnected = true
                            }.onFailure {
                                bleConnected = false
                                Log.e(OTA_UI_TAG, "readFirmwareVersion failed, mac=$bluetoothMac", it)
                                Toast.makeText(context, it.message ?: "蓝牙连接失败", Toast.LENGTH_SHORT).show()
                            }
                            loading = false
                        }
                    },
                tint = Color.Unspecified
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("蓝牙 MAC: ${if (bluetoothMac.isBlank()) "-" else bluetoothMac}", fontSize = 14.sp)
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("读取设备版本中...", fontSize = 14.sp)
                }
            } else {
                Text("当前设备版本(0x180A/0x2A26): $currentVersion", fontSize = 14.sp)
            }
            Text("本地车型: ${if (localModelType.isBlank()) "-" else localModelType}", fontSize = 14.sp)
            Text("目标升级版本: ${selectedOption.version}", fontSize = 14.sp, color = Color(0xFF0E8D53))
        }

        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Text("升级文件选择", fontWeight = FontWeight.SemiBold)
            otaOptions.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedOptionIndex = index }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedOptionIndex == index,
                        onClick = { selectedOptionIndex = index }
                    )
                    Column {
                        Text(item.label, fontSize = 14.sp)
                        Text(item.url, fontSize = 11.sp, color = Color(0xFF666666))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = { showConsent = true },
            enabled = !loading && bluetoothMac.isNotBlank() && hasBlePermission && bleConnected,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
            shape = RoundedCornerShape(40.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(if (loading) "处理中..." else "开始升级", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text("进度: $progress%", fontSize = 14.sp)
        Text(progressText, fontSize = 13.sp, color = Color(0xFF666666))
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = {
                scope.launch {
                    loading = true
                    try {
                        if (bluetoothMac.isBlank()) {
                            throw IllegalStateException("未找到蓝牙 MAC 地址")
                        }
                        currentVersion = otaRunner.readFirmwareVersion(bluetoothMac)
                        Toast.makeText(context, "当前版本: $currentVersion", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(OTA_UI_TAG, "manual readFirmwareVersion failed, mac=$bluetoothMac", e)
                        Toast.makeText(context, e.message ?: "读取版本失败", Toast.LENGTH_SHORT).show()
                    } finally {
                        loading = false
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E8D53)),
            shape = RoundedCornerShape(40.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("升级完成后刷新版本", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    if (showConsent) {
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text("升级确认") },
            text = {
                Text(
                    "当前版本: $currentVersion\n目标版本: ${selectedOption.version}\n\n将直接在 App 内下载并执行 OTA，完成后自动删除本地 bin。升级过程中请勿退出应用。"
                )
            },
            confirmButton = {
                Text(
                    "同意并开始",
                    modifier = Modifier.clickable {
                        showConsent = false
                        scope.launch {
                            loading = true
                            progress = 0
                            progressText = "开始 OTA"
                            val needs = requiredBlePermissions().filterNot { isGranted(it) }
                            if (needs.isNotEmpty()) {
                                blePermissionLauncher.launch(needs.toTypedArray())
                                loading = false
                                return@launch
                            }
                            runCatching {
                                otaRunner.runOta(
                                    mac = bluetoothMac,
                                    url = selectedOption.url,
                                    onProgress = { p, text ->
                                        progress = p
                                        progressText = text
                                    }
                                )
                                currentVersion = otaRunner.readFirmwareVersion(bluetoothMac)
                                bleConnected = true
                                Toast.makeText(context, "升级完成，当前版本: $currentVersion", Toast.LENGTH_LONG).show()
                            }.onFailure {
                                bleConnected = false
                                Log.e(OTA_UI_TAG, "runOta failed, mac=$bluetoothMac", it)
                                Toast.makeText(context, it.message ?: "OTA失败", Toast.LENGTH_LONG).show()
                            }
                            loading = false
                        }
                    },
                    color = Color(0xFF007AFF)
                )
            },
            dismissButton = {
                Text("取消", modifier = Modifier.clickable { showConsent = false })
            }
        )
    }
}

private class Fr8010OtaRunner(private val context: Context) {
    private val otaTag = "OTA-FR8010"
    private val otaServiceUuid = UUID.fromString("02f00000-0000-0000-0000-00000000fe00")
    private val otaWriteUuid = UUID.fromString("02f00000-0000-0000-0000-00000000ff01")
    private val otaNotifyUuid = UUID.fromString("02f00000-0000-0000-0000-00000000ff02")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val disServiceUuid = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    private val fwRevUuid = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")

    private val cmdGetStrBase = 1
    private val cmdPageErase = 3
    private val cmdWriteData = 5
    private val cmdReboot = 9

    private var gatt: BluetoothGatt? = null
    private var otaWriteChar: BluetoothGattCharacteristic? = null
    private var otaNotifyChar: BluetoothGattCharacteristic? = null
    private var fwChar: BluetoothGattCharacteristic? = null
    private var mtuSize: Int = 23
    private var notifyPacket: ByteArray? = null
    private var pendingAckOpcode: Int? = null
    private var pendingDescriptorWriteContinuation: ((Int) -> Unit)? = null

    private fun otaLog(message: String) {
        Log.d(otaTag, message)
    }

    private fun otaError(message: String, throwable: Throwable? = null) {
        Log.e(otaTag, message, throwable)
    }

    @SuppressLint("MissingPermission")
    suspend fun connectAndReadFirmwareVersion(mac: String): String {
        return readFirmwareVersion(mac)
    }

    @SuppressLint("MissingPermission")
    suspend fun readFirmwareVersion(mac: String): String {
        otaLog("readFirmwareVersion start mac=$mac")
        ensureOtaChannelReady(mac)
        val version = readFirmwareVersionOnly()
        otaLog("readFirmwareVersion done mac=$mac version=$version")
        return version
    }

    @SuppressLint("MissingPermission")
    suspend fun readFirmwareVersionOnly(): String {
        val g = gatt ?: throw IllegalStateException("蓝牙未连接")
        val char = fwChar ?: throw IllegalStateException("未找到 0x2A26 特征")
        suspendCancellableCoroutine<Unit> { cont ->
            val ok = g.readCharacteristic(char)
            if (!ok) cont.resumeWithException(IllegalStateException("读取版本失败"))
            else pendingReadVersion = { bytes ->
                if (cont.isActive) cont.resume(Unit)
                val text = bytes.toString(Charsets.UTF_8).trim('\u0000', ' ')
                lastFirmwareVersion = if (text.isBlank()) "-" else text
            }
        }
        otaLog("readFirmwareVersionOnly value=$lastFirmwareVersion")
        return lastFirmwareVersion
    }

    private var pendingReadVersion: ((ByteArray) -> Unit)? = null
    private var lastFirmwareVersion: String = "-"

    @SuppressLint("MissingPermission")
    suspend fun runOta(mac: String, url: String, onProgress: (Int, String) -> Unit) {
        otaLog("runOta start mac=$mac url=$url")
        ensureOtaChannelReady(mac)
        val g = gatt ?: throw IllegalStateException("蓝牙未连接")
        otaWriteChar ?: throw IllegalStateException("未找到 OTA 写特征")
        val file = downloadBin(url, onProgress)
        try {
            val data = withContext(Dispatchers.IO) { file.readBytes() }
            if (data.size < 100) throw IllegalStateException("bin 文件无效")
            val fileCrc = calcLegacyCrc(data)
            otaLog("ota file loaded size=${data.size} crc=0x${Integer.toHexString(fileCrc)}")

            requestMtu(g, 247)
            val packageSize = (mtuSize - 3 - 9).coerceAtLeast(20)
            otaLog("mtu ready mtu=$mtuSize packageSize=$packageSize")

            onProgress(10, "获取基地址")
            sendAndWaitAck(buildCmd(cmdGetStrBase, 0, 0, null))
            val baseAddr = parseAddr(notifyPacket ?: throw IllegalStateException("未收到基地址应答"))
            otaLog("base address=0x${Integer.toHexString(baseAddr)}")

            onProgress(15, "擦除扇区")
            erasePages(baseAddr, data.size, onProgress)

            onProgress(25, "写入固件")
            var offset = 0
            var addr = baseAddr
            while (offset < data.size) {
                val len = minOf(packageSize, data.size - offset)
                val chunk = data.copyOfRange(offset, offset + len)
                sendAndWaitAck(buildCmd(cmdWriteData, addr, len, chunk))
                offset += len
                addr += len
                val p = 25 + (offset * 70 / data.size)
                onProgress(p, "写入中 $offset/${data.size}")
                if (offset == data.size || offset % (packageSize * 40) == 0) {
                    otaLog("write progress offset=$offset/${data.size} addr=0x${Integer.toHexString(addr)}")
                }
            }

            onProgress(98, "发送重启命令")
            sendNoAck(buildRebootCmd(fileCrc, data.size.toLong()))
            otaLog("reboot command sent fileLen=${data.size} crc=0x${Integer.toHexString(fileCrc)}")
            onProgress(100, "OTA 完成")
            otaLog("runOta success mac=$mac")
        } catch (e: Exception) {
            otaError("runOta failed mac=$mac", e)
            throw e
        } finally {
            runCatching { file.delete() }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun ensureOtaChannelReady(mac: String) {
        otaLog("ensureOtaChannelReady start mac=$mac")
        ensureConnected(mac)
        discoverAndBindCharacteristics()
        otaLog("ensureOtaChannelReady done mac=$mac")
    }

    @SuppressLint("MissingPermission")
    private suspend fun ensureConnected(mac: String) {
        val manager = context.getSystemService(BluetoothManager::class.java)
            ?: throw IllegalStateException("蓝牙不可用")
        val adapter = manager.adapter ?: throw IllegalStateException("蓝牙适配器不可用")
        val device = adapter.getRemoteDevice(mac)

        val existingGatt = gatt
        if (existingGatt != null) {
            val state = manager.getConnectionState(existingGatt.device, BluetoothProfile.GATT)
            if (state == BluetoothProfile.STATE_CONNECTED) {
                otaLog("reuse existing gatt mac=${existingGatt.device.address}")
                return
            }
            otaLog("close stale gatt mac=${existingGatt.device.address} state=$state")
            runCatching { existingGatt.close() }
            gatt = null
            otaWriteChar = null
            otaNotifyChar = null
            fwChar = null
        }

        suspendCancellableCoroutine<Unit> { cont ->
            val callback = object : BluetoothGattCallback() {
                private fun handleNotify(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                    if (characteristic.uuid == otaNotifyUuid) {
                        otaLog("rx notify len=${value.size} wait=${pendingAckOpcode?.let { otaOpcodeName(it) } ?: "-"} data=${otaHexPreview(value, 24)}")
                        notifyPacket = value
                        pendingNotifyContinuation?.invoke()
                        pendingNotifyContinuation = null
                    }
                }

                private fun handleRead(characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                    if (characteristic.uuid == fwRevUuid && status == BluetoothGatt.GATT_SUCCESS) {
                        pendingReadVersion?.invoke(value)
                        pendingReadVersion = null
                    }
                }

                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    otaLog("onConnectionStateChange status=$status newState=$newState mac=${g.device.address}")
                    if (newState == BluetoothGatt.STATE_CONNECTED) {
                        gatt = g
                        if (cont.isActive) cont.resume(Unit)
                    } else if (newState == BluetoothGatt.STATE_DISCONNECTED && cont.isActive) {
                        cont.resumeWithException(IllegalStateException("蓝牙连接断开"))
                    }
                }

                override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                    handleNotify(characteristic, value)
                }

                @Suppress("DEPRECATION")
                override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    handleNotify(characteristic, characteristic.value ?: byteArrayOf())
                }

                override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                    handleRead(characteristic, value, status)
                }

                @Suppress("DEPRECATION")
                override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                    handleRead(characteristic, characteristic.value ?: byteArrayOf(), status)
                }

                override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                    otaLog("onMtuChanged status=$status mtu=$mtu")
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        mtuSize = mtu
                    }
                    pendingMtuContinuation?.invoke()
                    pendingMtuContinuation = null
                }

                override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                    otaLog("onDescriptorWrite status=$status uuid=${descriptor.uuid}")
                    pendingDescriptorWriteContinuation?.invoke(status)
                    pendingDescriptorWriteContinuation = null
                }
            }
            if (shouldCheckBluetoothConnectPermission(Build.VERSION.SDK_INT) &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                cont.resumeWithException(IllegalStateException("缺少蓝牙连接权限"))
                return@suspendCancellableCoroutine
            }
            otaLog("connectGatt start mac=$mac")
            device.connectGatt(context, false, callback)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun discoverAndBindCharacteristics() {
        val g = gatt ?: throw IllegalStateException("蓝牙未连接")
        if (otaWriteChar != null && otaNotifyChar != null && fwChar != null) {
            otaLog("discover skipped, characteristics already cached")
            return
        }
        otaLog("discover services start")
        val discoverStarted = g.discoverServices()
        if (!discoverStarted) throw IllegalStateException("发现服务失败")
        try {
            withTimeout(6000) {
                while (true) {
                    val otaService: BluetoothGattService? = g.getService(otaServiceUuid)
                    val disService: BluetoothGattService? = g.getService(disServiceUuid)
                    if (otaService != null && disService != null) {
                        otaWriteChar = otaService.getCharacteristic(otaWriteUuid)
                        otaNotifyChar = otaService.getCharacteristic(otaNotifyUuid)
                        fwChar = disService.getCharacteristic(fwRevUuid)
                        val notify = otaNotifyChar
                        if (notify != null) {
                            g.setCharacteristicNotification(notify, true)
                            val descriptor = notify.getDescriptor(cccdUuid)
                            if (descriptor != null) {
                                try {
                                    withTimeout(3000) {
                                        suspendCancellableCoroutine<Unit> { descriptorCont ->
                                            pendingDescriptorWriteContinuation = { status ->
                                                if (descriptorCont.isActive) {
                                                    if (status == BluetoothGatt.GATT_SUCCESS) {
                                                        descriptorCont.resume(Unit)
                                                    } else {
                                                        descriptorCont.resumeWithException(
                                                            IllegalStateException("通知描述符写入失败($status)")
                                                        )
                                                    }
                                                }
                                            }
                                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                            val writeOk = g.writeDescriptor(descriptor)
                                            if (!writeOk) {
                                                pendingDescriptorWriteContinuation = null
                                                if (descriptorCont.isActive) {
                                                    descriptorCont.resumeWithException(IllegalStateException("通知描述符写入启动失败"))
                                                }
                                            }
                                        }
                                    }
                                } catch (e: TimeoutCancellationException) {
                                    pendingDescriptorWriteContinuation = null
                                    throw IllegalStateException("通知描述符写入超时", e)
                                }
                            } else {
                                otaLog("cccd descriptor missing on notify characteristic")
                            }
                        }
                        otaLog("discover success write=${otaWriteChar != null} notify=${otaNotifyChar != null} fw=${fwChar != null}")
                        return@withTimeout
                    }
                    delay(200)
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("未找到 OTA/版本服务", e)
        }
    }

    private var pendingNotifyContinuation: (() -> Unit)? = null
    private suspend fun sendAndWaitAck(payload: ByteArray) {
        val opcode = payload.firstOrNull()?.toInt()?.and(0xFF) ?: -1
        pendingAckOpcode = opcode
        notifyPacket = null
        otaLog("tx ${otaOpcodeName(opcode)} len=${payload.size} data=${otaHexPreview(payload, 24)}")
        try {
            withTimeout(6000) {
                suspendCancellableCoroutine<Unit> { cont ->
                    pendingNotifyContinuation = {
                        if (cont.isActive) cont.resume(Unit)
                    }
                    try {
                        sendNoAck(payload)
                    } catch (e: Exception) {
                        pendingNotifyContinuation = null
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("等待ACK超时: ${otaOpcodeName(opcode)}", e)
        } finally {
            pendingAckOpcode = null
            pendingNotifyContinuation = null
        }
        val ack = notifyPacket ?: throw IllegalStateException("未收到ACK: ${otaOpcodeName(opcode)}")
        otaLog("ack ${otaOpcodeName(opcode)} len=${ack.size} data=${otaHexPreview(ack, 24)}")
    }

    @SuppressLint("MissingPermission")
    private fun sendNoAck(payload: ByteArray) {
        val g = gatt ?: throw IllegalStateException("蓝牙未连接")
        val write = otaWriteChar ?: throw IllegalStateException("写特征值不存在")
        val writeType = resolveOtaWriteType(write.properties)
        write.writeType = writeType
        write.value = payload
        otaLog(
            "write char properties=0x${Integer.toHexString(write.properties)} writeType=" +
                if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) "NO_RESPONSE" else "DEFAULT"
        )
        val ok = g.writeCharacteristic(write)
        if (!ok) throw IllegalStateException("写入失败")
    }

    private var pendingMtuContinuation: (() -> Unit)? = null
    @SuppressLint("MissingPermission")
    private suspend fun requestMtu(gatt: BluetoothGatt, mtu: Int) {
        try {
            withTimeout(5000) {
                suspendCancellableCoroutine<Unit> { cont ->
                    pendingMtuContinuation = { if (cont.isActive) cont.resume(Unit) }
                    if (!gatt.requestMtu(mtu)) {
                        pendingMtuContinuation = null
                        cont.resume(Unit)
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            otaLog("requestMtu timeout, keep default mtu=$mtuSize")
        } finally {
            pendingMtuContinuation = null
        }
    }

    private suspend fun erasePages(baseAddr: Int, fileSize: Int, onProgress: (Int, String) -> Unit) {
        val count = (fileSize + 0xFFF) / 0x1000
        otaLog("erase pages count=$count base=0x${Integer.toHexString(baseAddr)} fileSize=$fileSize")
        for (i in 0 until count) {
            val addr = baseAddr + i * 0x1000
            sendAndWaitAck(buildPageEraseCmd(addr))
            val p = 15 + ((i + 1) * 10 / count)
            onProgress(p, "擦除扇区 ${i + 1}/$count")
            if (i == count - 1 || i % 16 == 0) {
                otaLog("erase progress ${i + 1}/$count addr=0x${Integer.toHexString(addr)}")
            }
        }
    }

    private fun buildCmd(opcode: Int, addr: Int, dataLength: Int, data: ByteArray?): ByteArray {
        val headerLen = if (opcode == cmdPageErase) 7 else 9
        val body = data ?: byteArrayOf()
        val out = ByteArray(headerLen + body.size)
        out[0] = (opcode and 0xFF).toByte()
        val lengthField = if (opcode == cmdPageErase) 7 else if (opcode == cmdGetStrBase) 3 else 9
        out[1] = (lengthField and 0xFF).toByte()
        out[2] = ((lengthField shr 8) and 0xFF).toByte()
        out[3] = (addr and 0xFF).toByte()
        out[4] = ((addr shr 8) and 0xFF).toByte()
        out[5] = ((addr shr 16) and 0xFF).toByte()
        out[6] = ((addr shr 24) and 0xFF).toByte()
        if (headerLen > 7) {
            out[7] = (dataLength and 0xFF).toByte()
            out[8] = ((dataLength shr 8) and 0xFF).toByte()
        }
        if (body.isNotEmpty()) {
            System.arraycopy(body, 0, out, headerLen, body.size)
        }
        return out
    }

    private fun buildPageEraseCmd(addr: Int): ByteArray = buildCmd(cmdPageErase, addr, 0, null)

    private fun buildRebootCmd(crc: Int, fileLength: Long): ByteArray {
        val out = ByteArray(11)
        out[0] = cmdReboot.toByte()
        out[1] = 0x0A
        out[2] = 0x00
        out[3] = (fileLength and 0xFF).toByte()
        out[4] = ((fileLength shr 8) and 0xFF).toByte()
        out[5] = ((fileLength shr 16) and 0xFF).toByte()
        out[6] = ((fileLength shr 24) and 0xFF).toByte()
        out[7] = (crc and 0xFF).toByte()
        out[8] = ((crc shr 8) and 0xFF).toByte()
        out[9] = ((crc shr 16) and 0xFF).toByte()
        out[10] = ((crc shr 24) and 0xFF).toByte()
        return out
    }

    private fun parseAddr(packet: ByteArray): Int {
        if (packet.size < 8) return 0
        return (packet[4].toInt() and 0xFF) or
            ((packet[5].toInt() and 0xFF) shl 8) or
            ((packet[6].toInt() and 0xFF) shl 16) or
            ((packet[7].toInt() and 0xFF) shl 24)
    }

    private suspend fun downloadBin(url: String, onProgress: (Int, String) -> Unit): File {
        val target = File(context.cacheDir, "fr8010_ota.bin")
        return withContext(Dispatchers.IO) {
            URL(url).openConnection().getInputStream().use { input ->
                FileOutputStream(target).use { out ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var total = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        total += read
                        if (total % (64 * 1024) == 0L) {
                            onProgress(5, "下载升级包 ${total / 1024}KB")
                        }
                    }
                }
            }
            target
        }
    }

    private fun calcLegacyCrc(fileBytes: ByteArray): Int {
        return calcFr8010LegacyCrc(fileBytes)
    }
}

internal fun shouldCheckBluetoothConnectPermission(apiLevel: Int): Boolean {
    return apiLevel >= Build.VERSION_CODES.S
}

internal fun otaOpcodeName(opcode: Int): String {
    return when (opcode) {
        1 -> "GET_STR_BASE"
        3 -> "PAGE_ERASE"
        5 -> "WRITE_DATA"
        9 -> "REBOOT"
        else -> "UNKNOWN($opcode)"
    }
}

internal fun otaHexPreview(bytes: ByteArray?, maxBytes: Int): String {
    if (bytes == null) return "null"
    if (bytes.isEmpty()) return "empty"
    val safeMax = maxBytes.coerceAtLeast(1)
    val shown = minOf(safeMax, bytes.size)
    val prefix = bytes.take(shown).joinToString(" ") { "%02X".format(it) }
    return if (bytes.size > shown) {
        "$prefix...(+${bytes.size - shown})"
    } else {
        prefix
    }
}

internal fun resolveOtaWriteType(characteristicProperties: Int): Int {
    return if ((characteristicProperties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
    } else {
        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    }
}

private val FR8010_CRC_TABLE: IntArray = IntArray(256).also { table ->
    for (i in 0 until 256) {
        var c = i
        repeat(8) {
            c = if ((c and 1) != 0) {
                0xEDB88320.toInt() xor (c ushr 1)
            } else {
                c ushr 1
            }
        }
        table[i] = c
    }
}

internal fun calcFr8010LegacyCrc(fileBytes: ByteArray): Int {
    if (fileBytes.size <= 256) return 0
    var crc = 0
    var index = 256
    while (index < fileBytes.size) {
        val high = crc / 256
        crc = crc shl 8
        crc = crc xor FR8010_CRC_TABLE[(high xor (fileBytes[index].toInt() and 0xFF)) and 0xFF]
        index++
    }
    return crc
}
