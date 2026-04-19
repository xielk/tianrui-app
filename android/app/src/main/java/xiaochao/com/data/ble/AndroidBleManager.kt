package xiaochao.com.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver
import xiaochao.com.core.result.AppResult
import xiaochao.com.core.log.AppLogger
import xiaochao.com.core.log.LogEntry
import xiaochao.com.data.model.ControlCommand
import xiaochao.com.data.model.ChannelAvailability
import xiaochao.com.data.session.AppSessionStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class AndroidBleManager(
    private val logger: AppLogger,
) {
    companion object {
        @Volatile
        private var _instance: AndroidBleManager? = null

        fun getInstance(logger: AppLogger): AndroidBleManager {
            return _instance ?: synchronized(this) {
                _instance ?: AndroidBleManager(logger).also { _instance = it }
            }
        }
    }
    private val serviceUuid: UUID = UUID.fromString("00001000-0000-1000-8000-00805F9B34FB")
    private val writeUuid: UUID = UUID.fromString("00001001-0000-1000-8000-00805F9B34FB")
    private val notifyUuid: UUID = UUID.fromString("00001002-0000-1000-8000-00805F9B34FB")
    private val encryptKeyHex = "AA9B8F3C8A60DA6C8E583F5C6248954A"
    private val tokenRequestPlainHex = "C35AA5C300112233445566778899AABB"

    private val _availability = MutableStateFlow(ChannelAvailability(bleAvailable = false, networkAvailable = true))
    val availability: StateFlow<ChannelAvailability> = _availability
    private val _realtimeState = MutableStateFlow(BleRealtimeState())
    val realtimeState: StateFlow<BleRealtimeState> = _realtimeState

    private var connecting: Boolean = false
    private var connectingStartAt: Long = 0L
    private var nordicClient: NordicClient? = null
    private var deviceToken: String = "1234"
    private var tokenWaiter: CompletableDeferred<String>? = null
    private var lastNotifyPlainHex: String = ""
    private var lastParsedLockState: Boolean? = null
    private var lastParsedMuteState: Boolean? = null
    private var lastParsedAutoSenseState: Boolean? = null
    /** 与 `BleDeviceMessageParser` 输出对应，按类型去重，避免重复 hex 刷屏 */
    private val lastParsedBleHexByKind = mutableMapOf<String, String>()
    /** 无法识别类型时的 raw 去重 */
    private var lastBleStateGenericSignature: String? = null

    var activeMac: String = ""
        private set

    private fun debug(message: String) {
        Log.d("BLE-DEBUG", message)
    }

    private fun debugParsedBle(kind: String, plainHex: String, message: String) {
        if (lastParsedBleHexByKind[kind] == plainHex) return
        lastParsedBleHexByKind[kind] = plainHex
        debug(message)
    }

    private fun appContext(): Context? = AppSessionStore.getApplicationContextOrNull()

    private fun hasPermission(permission: String): Boolean {
        val ctx = appContext() ?: return false
        return ContextCompat.checkSelfPermission(ctx, permission) == PermissionChecker.PERMISSION_GRANTED
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val ctx = appContext() ?: return null
        val manager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    fun markBleConnected(connected: Boolean) {
        _availability.value = _availability.value.copy(bleAvailable = connected)
        debug("markBleConnected connected=$connected activeMac=$activeMac availability=${_availability.value}")
        logPhase(if (connected) "connected" else "disconnected")
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        debug("disconnect requested activeMac=$activeMac")
        connecting = false
        runCatching { nordicClient?.disconnect()?.enqueue() }
        runCatching { nordicClient?.close() }
        nordicClient = null
        logPhase("disconnect_requested mac=$activeMac")
        activeMac = ""
        lastBleStateGenericSignature = null
        lastParsedBleHexByKind.clear()
        markBleConnected(false)
    }

    suspend fun sendControlCommand(command: ControlCommand): AppResult<Unit> {
        val detail = when (command) {
            is ControlCommand.ToggleLock -> "ToggleLock locked=${command.locked}"
            is ControlCommand.ToggleMute -> "ToggleMute mute=${command.mute}"
            is ControlCommand.ToggleAutoSense -> "ToggleAutoSense enabled=${command.enabled}"
            ControlCommand.FindBike -> "FindBike"
            ControlCommand.SensorLevelLocation -> "SensorLevelLocation"
            is ControlCommand.SetBleDisarmSensorDistance -> "SetBleDisarmSensorDistance level=${command.level0To8}"
            is ControlCommand.SetBleArmSensorDistance -> "SetBleArmSensorDistance level=${command.level0To8}"
            is ControlCommand.SetBleAlarmSensitivity -> "SetBleAlarmSensitivity idx=${command.levelIndex0To2}"
            is ControlCommand.SetBleAutoShutdown -> "SetBleAutoShutdown min=${command.minutes}"
            is ControlCommand.SetBleAlarmSoundVolume -> "SetBleAlarmSoundVolume vol=${command.volume0To10}"
            is ControlCommand.MoveMode -> "MoveMode enabled=${command.enabled}"
            ControlCommand.MoveForward -> "MoveForward"
            ControlCommand.MoveBackward -> "MoveBackward"
        }
        debug("sendControlCommand begin command=$detail activeMac=$activeMac")
        if (!_availability.value.bleAvailable) {
            debug("sendControlCommand reject: ble disconnected")
            return AppResult.Error("BLE_DISCONNECTED", "蓝牙未连接")
        }
        val client = nordicClient
        if (client == null || !client.isReadyToWrite()) {
            debug("sendControlCommand reject: write characteristic unavailable")
            return AppResult.Error("BLE_NOT_READY", "蓝牙通道未就绪")
        }

        val ensured = ensureDeviceToken(client)
        if (ensured is AppResult.Error) {
            return ensured
        }

        val plainHex = when (command) {
            is ControlCommand.ToggleLock -> buildControlHex("03", if (command.locked) "00" else "01")
            is ControlCommand.ToggleMute -> {
                val (param, value) = resolveToggleMuteParamValue(command.mute)
                buildControlHex(param, value)
            }
            is ControlCommand.ToggleAutoSense -> buildControlHex("0A", if (command.enabled) "01" else "00")
            ControlCommand.FindBike -> buildControlHex("02", "01")
            ControlCommand.SensorLevelLocation -> buildControlHex("1A", "00")
            is ControlCommand.SetBleDisarmSensorDistance -> {
                val v = command.level0To8.coerceIn(0, 8)
                buildControlHex("0B", "%02X".format(v))
            }
            is ControlCommand.SetBleArmSensorDistance -> {
                val v = command.level0To8.coerceIn(0, 8)
                buildControlHex("19", "%02X".format(v))
            }
            is ControlCommand.SetBleAlarmSensitivity -> {
                val v = (command.levelIndex0To2.coerceIn(0, 2) + 1)
                buildControlHex("05", "%02X".format(v))
            }
            is ControlCommand.SetBleAutoShutdown -> {
                val hex = when (command.minutes) {
                    0 -> "00"
                    3 -> "03"
                    5 -> "05"
                    10 -> "0A"
                    else -> {
                        debug("sendControlCommand invalid autoShutdown minutes=${command.minutes}")
                        return AppResult.Error("BLE_CMD_INVALID", "无效自动关机时间")
                    }
                }
                buildControlHex("09", hex)
            }
            is ControlCommand.SetBleAlarmSoundVolume -> {
                val v = command.volume0To10.coerceIn(0, 10)
                buildControlHex("0F", "%02X".format(v))
            }
            is ControlCommand.MoveMode -> buildControlHex("1C", resolveMoveModeValue(command.enabled))
            ControlCommand.MoveForward -> buildControlHex("1D", resolveMoveStepValue())
            ControlCommand.MoveBackward -> buildControlHex("1E", resolveMoveStepValue())
        }
        val encrypted = encryptHex(plainHex)
        if (encrypted == null) {
            debug("sendControlCommand encrypt failed plain=$plainHex")
            return AppResult.Error("BLE_ENCRYPT_ERROR", "蓝牙指令加密失败")
        }

        val payload = hexToBytes(encrypted)
        if (command is ControlCommand.MoveMode || command == ControlCommand.MoveForward || command == ControlCommand.MoveBackward) {
            Log.d("move-device", "sendControlCommand detail=$detail plain=$plainHex encrypted=$encrypted")
        }
        debug("sendControlCommand write service=$serviceUuid char=$writeUuid len=${payload.size} plain=$plainHex encrypted=$encrypted")
        return writeBytes(client, payload)
    }

    @SuppressLint("MissingPermission")
    suspend fun ensurePaired(): AppResult<Boolean> {
        val mac = activeMac
        if (mac.isBlank()) {
            return AppResult.Error("NO_MAC", "未配置蓝牙MAC地址")
        }
        val adapter = bluetoothAdapter() ?: return AppResult.Error("BT_UNAVAILABLE", "蓝牙适配器不可用")
        val context = appContext() ?: return AppResult.Error("NO_CONTEXT", "应用上下文不可用")
        val device = try {
            adapter.getRemoteDevice(mac)
        } catch (e: Exception) {
            return AppResult.Error("INVALID_MAC", "蓝牙地址无效")
        }

        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            debug("ensurePaired already bonded mac=$mac")
            return AppResult.Success(true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return AppResult.Error("NO_PERMISSION", "缺少蓝牙连接权限")
        }

        val waiter = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                val changed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                } ?: return
                if (!changed.address.equals(mac, ignoreCase = true)) return
                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                debug("bond state mac=$mac state=$state")
                if (state == BluetoothDevice.BOND_BONDED) {
                    waiter.complete(true)
                } else if (state == BluetoothDevice.BOND_NONE) {
                    waiter.complete(false)
                }
            }
        }

        return try {
            context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
            val started = device.createBond()
            debug("ensurePaired createBond started=$started mac=$mac")
            if (!started && device.bondState != BluetoothDevice.BOND_BONDING) {
                AppResult.Error("PAIR_START_FAIL", "发起配对失败")
            } else {
                val waitResult = withContext(Dispatchers.IO) {
                    withTimeoutOrNull(30000) { waiter.await() }
                }
                when {
                    waitResult == true -> AppResult.Success(true)
                    device.bondState == BluetoothDevice.BOND_BONDING -> AppResult.Success(false)
                    else -> AppResult.Error("PAIR_FAIL", "蓝牙配对失败")
                }
            }
        } catch (e: Exception) {
            AppResult.Error("PAIR_EXCEPTION", e.message ?: "蓝牙配对异常")
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    @SuppressLint("MissingPermission")
    fun isCurrentDevicePaired(): Boolean {
        val mac = activeMac
        if (mac.isBlank()) return false
        val adapter = bluetoothAdapter() ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return false
        }
        val device = runCatching { adapter.getRemoteDevice(mac) }.getOrNull() ?: return false
        return device.bondState == BluetoothDevice.BOND_BONDED
    }

    @SuppressLint("MissingPermission")
    suspend fun removeCurrentPairing(): AppResult<Boolean> {
        val mac = activeMac
        debug("removeCurrentPairing begin mac=$mac")
        if (mac.isBlank()) {
            debug("removeCurrentPairing abort no mac")
            return AppResult.Error("NO_MAC", "未配置蓝牙MAC地址")
        }
        val adapter = bluetoothAdapter() ?: return AppResult.Error("BT_UNAVAILABLE", "蓝牙适配器不可用")
        val context = appContext() ?: return AppResult.Error("NO_CONTEXT", "应用上下文不可用")
        val device = runCatching { adapter.getRemoteDevice(mac) }.getOrNull()
            ?: return AppResult.Error("INVALID_MAC", "蓝牙地址无效")

        debug("removeCurrentPairing current bondState=${device.bondState} mac=$mac")
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            debug("removeCurrentPairing skip not bonded mac=$mac")
            return AppResult.Success(false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return AppResult.Error("NO_PERMISSION", "缺少蓝牙连接权限")
        }

        val waiter = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                val changed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                } ?: return
                if (!changed.address.equals(mac, ignoreCase = true)) return
                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                debug("remove pair bond state mac=$mac state=$state")
                if (state == BluetoothDevice.BOND_NONE) {
                    waiter.complete(true)
                }
            }
        }

        return try {
            context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
            val started = runCatching {
                val method = device.javaClass.getMethod("removeBond")
                method.invoke(device) as? Boolean ?: false
            }.onFailure {
                debug("removeCurrentPairing reflection removeBond exception=${it.message}")
            }.getOrElse { false }

            debug("removeCurrentPairing started=$started mac=$mac")
            if (!started) {
                AppResult.Error("UNPAIR_START_FAIL", "解除蓝牙配对失败")
            } else {
                val ok = withContext(Dispatchers.IO) {
                    withTimeoutOrNull(10000) { waiter.await() } ?: false
                }
                debug("removeCurrentPairing await result ok=$ok finalBondState=${device.bondState} mac=$mac")
                if (ok) AppResult.Success(true)
                else AppResult.Error("UNPAIR_FAIL", "解除蓝牙配对超时")
            }
        } catch (e: Exception) {
            debug("removeCurrentPairing exception=${e.message}")
            AppResult.Error("UNPAIR_EXCEPTION", e.message ?: "解除蓝牙配对异常")
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
            debug("removeCurrentPairing end mac=$mac")
        }
    }

    @SuppressLint("MissingPermission")
    fun startConnect(macAddress: String) {
        debug("startConnect begin mac=$macAddress")
        val ctx = appContext()
        if (ctx == null) {
            debug("startConnect abort: context null")
            return
        }
        val adapter = bluetoothAdapter()
        if (adapter == null) {
            debug("startConnect abort: bluetooth adapter null")
            return
        }
        if (!adapter.isEnabled) {
            debug("startConnect abort: bluetooth disabled")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            debug("startConnect abort: no BLUETOOTH_CONNECT permission")
            return
        }
        // Pre-Android 12: direct GATT connect by known MAC does not require runtime location grant here.
        if (activeMac.equals(macAddress, ignoreCase = true) && connecting) {
            val elapsed = System.currentTimeMillis() - connectingStartAt
            if (elapsed < 6000) {
                debug("startConnect skip: already connecting mac=$macAddress elapsed=${elapsed}ms")
                return
            }
            debug("startConnect stale connecting detected, force restart mac=$macAddress elapsed=${elapsed}ms")
            connecting = false
            runCatching { nordicClient?.disconnect()?.enqueue() }
            runCatching { nordicClient?.close() }
            nordicClient = null
        }

        val device = try {
            adapter.getRemoteDevice(macAddress)
        } catch (e: Exception) {
            debug("startConnect invalid mac=$macAddress error=${e.message}")
            return
        }
        val isBonded = device.bondState == BluetoothDevice.BOND_BONDED
        debug("startConnect device bondState=${device.bondState} bonded=$isBonded mac=$macAddress")

        activeMac = macAddress
        AppSessionStore.setLastBluetoothAddress(macAddress)
        connecting = true
        connectingStartAt = System.currentTimeMillis()
        logPhase("scan_started target=$macAddress")
        logPhase("connect_started mac=$macAddress")

        val client = NordicClient(ctx)
        client.setOnNotify { bytes -> onNotification(bytes) }
        client.setConnectionObserver(object : ConnectionObserver {
            override fun onDeviceConnecting(device: BluetoothDevice) {
                connecting = true
                debug("nordic onDeviceConnecting mac=${device.address}")
            }

            override fun onDeviceConnected(device: BluetoothDevice) {
                debug("nordic onDeviceConnected mac=${device.address}")
            }

            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                connecting = false
                markBleConnected(false)
                debug("nordic onDeviceFailedToConnect mac=${device.address} reason=$reason")
            }

            override fun onDeviceReady(device: BluetoothDevice) {
                connecting = false
                markBleConnected(true)
                debug("nordic onDeviceReady mac=${device.address}")
            }

            override fun onDeviceDisconnecting(device: BluetoothDevice) {
                debug("nordic onDeviceDisconnecting mac=${device.address}")
            }

            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                connecting = false
                markBleConnected(false)
                debug("nordic onDeviceDisconnected mac=${device.address} reason=$reason")
            }
        })
        nordicClient = client
        deviceToken = "1234"
        tokenWaiter = null
        lastNotifyPlainHex = ""
        lastParsedBleHexByKind.clear()
        _realtimeState.value = BleRealtimeState()

        val connectReq = client.connect(device)
            .retry(2, 1000)

        if (isBonded) {
            // 已配对设备在部分机型上直连容易超时，改为 autoConnect 提高成功率。
            connectReq.useAutoConnect(true)
            debug("nordic connect strategy=bonded_auto_connect mac=$macAddress")
        } else {
            connectReq.timeout(15000).useAutoConnect(false)
            debug("nordic connect strategy=direct mac=$macAddress")
        }

        connectReq
            .done {
                connecting = false
                markBleConnected(true)
                debug("nordic connect done mac=$macAddress")
            }
            .fail { _, status ->
                connecting = false
                markBleConnected(false)
                debug("nordic connect fail mac=$macAddress status=$status bonded=$isBonded")
            }
            .enqueue()

        debug("startConnect finish activeMac=$activeMac using=nordic-ble")
    }

    private suspend fun ensureDeviceToken(client: NordicClient): AppResult<Unit> {
        if (deviceToken.length == 4 && deviceToken != "1234") {
            return AppResult.Success(Unit)
        }
        val encrypted = encryptHex(tokenRequestPlainHex)
        if (encrypted == null) {
            return AppResult.Error("BLE_ENCRYPT_ERROR", "获取蓝牙Token失败")
        }
        val payload = hexToBytes(encrypted)
        tokenWaiter = CompletableDeferred()
        val writeRes = writeBytes(client, payload)
        if (writeRes is AppResult.Error) {
            tokenWaiter = null
            return writeRes
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val token = tokenWaiter?.await().orEmpty()
                if (token.length == 4) {
                    deviceToken = token
                    debug("token acquired token=$token")
                    AppResult.Success(Unit)
                } else {
                    AppResult.Error("BLE_TOKEN_INVALID", "蓝牙Token无效")
                }
            }.getOrElse {
                AppResult.Error("BLE_TOKEN_TIMEOUT", "获取蓝牙Token超时")
            }
        }
    }

    private fun buildControlHex(param: String, value: String): String {
        val token = if (deviceToken.length == 4) deviceToken else "1234"
        return "C303C3${param}${value}112233445566778899${token}".uppercase()
    }

    private fun encryptHex(plainHex: String): String? {
        return runCatching {
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            val key = SecretKeySpec(hexToBytes(encryptKeyHex), "AES")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            bytesToHex(cipher.doFinal(hexToBytes(plainHex))).uppercase()
        }.onFailure {
            debug("encryptHex fail=${it.message}")
        }.getOrNull()
    }

    private fun decryptHex(cipherHex: String): String? {
        return runCatching {
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            val key = SecretKeySpec(hexToBytes(encryptKeyHex), "AES")
            cipher.init(Cipher.DECRYPT_MODE, key)
            bytesToHex(cipher.doFinal(hexToBytes(cipherHex))).uppercase()
        }.onFailure {
            debug("decryptHex fail=${it.message}")
        }.getOrNull()
    }

    private suspend fun writeBytes(client: NordicClient, payload: ByteArray): AppResult<Unit> {
        val deferred = CompletableDeferred<AppResult<Unit>>()
        debug("writeBytes begin len=${payload.size} hex=${bytesToHex(payload)}")
        client.writePayload(payload,
            onWriteType = { wt ->
                val name = if (wt == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                    "WRITE_TYPE_NO_RESPONSE"
                } else {
                    "WRITE_TYPE_DEFAULT"
                }
                debug("writeBytes using $name")
            },
            onDone = {
                debug("writeBytes done")
                deferred.complete(AppResult.Success(Unit))
            },
            onFail = { code ->
                debug("writeBytes fail status=$code")
                deferred.complete(AppResult.Error("BLE_WRITE_FAIL", "蓝牙写入失败($code)"))
            }
        )
        return deferred.await()
    }

    private fun onNotification(raw: ByteArray) {
//        debug("BLE_NOTIFY raw_len=${raw.size} hex=${bytesToHex(raw).uppercase()}")
        val encryptedHex = bytesToHex(raw).uppercase()
        val plainHex = decryptHex(encryptedHex)
        if (plainHex == null) {
            debug("BLE_DECRYPT_FAIL hex=$encryptedHex")
            return
        }
//        debug("BLE_PLAIN hex=$plainHex")

        if (plainHex.length >= 4 && plainHex.substring(4, 6) == "A5") {
            val token = plainHex.substring(6, 10)
            if (token.matches(Regex("^[0-9A-F]{4}$"))) {
                deviceToken = token
                tokenWaiter?.complete(token)
                tokenWaiter = null
                debug("BLE_STATE A5 token=$token")
            }
            return
        }

        if (plainHex.length >= 6 && plainHex[4] == 'C') {
            parseCNotification(plainHex)
        }
    }

    private fun parseCNotification(plainHex: String) {
        val prefix = plainHex.substring(0, 6)
        val data = if (plainHex.length > 6) plainHex.substring(6) else ""
        val typeCode = plainHex.substring(4, 6).uppercase()

        when (typeCode) {
            "C4" -> {
                BleDeviceMessageParser.summarizeBleApp(plainHex)?.let { line ->
                    debugParsedBle("C4", plainHex, line)
                }
                applyBleAppRealtimeFields(data, plainHex)
            }
            "C5" -> {
                BleDeviceMessageParser.summarizeData(plainHex)?.let { line ->
                    debugParsedBle("C5", plainHex, line)
                }
            }
            "C6" -> {
                BleDeviceMessageParser.summarizeAlert(plainHex, data)?.let { line ->
                    debugParsedBle("C6", plainHex, line)
                }
                BleDeviceMessageParser.realtimeTripletFromAlertData(data)?.let { (lock, mute, auto) ->
                    lastParsedLockState = lock
                    lastParsedMuteState = mute
                    lastParsedAutoSenseState = auto
                    _realtimeState.value = _realtimeState.value.copy(
                        lastPlainHex = plainHex,
                        isLocked = lock,
                        isMuteEnabled = mute,
                        isAutoSenseEnabled = auto,
                    )
                }
            }
            "C7" -> {
                BleDeviceMessageParser.summarizeStatus(plainHex)?.let { line ->
                    debugParsedBle("C7", plainHex, line)
                }
            }
            else -> {
                val sig = "$prefix|$data"
                if (sig != lastBleStateGenericSignature) {
                    lastBleStateGenericSignature = sig
                    debug("BLE_STATE type=? prefix=$prefix data=$data (expect C4/C5/C6/C7 at plain[4..6], ref deviceMessages.js)")
                }
            }
        }
    }

    /** C4 BLEAPP：与 bleAppParser.js 一致，func 02 电源状态同步 UI 锁（与 F1 电源关=锁车一致） */
    private fun applyBleAppRealtimeFields(data: String, fullHex: String) {
        if (data.length < 4) {
            debug("BLE_MSG C4 BLEAPP data_short len=${data.length} hex=$fullHex")
            return
        }
        val funcCode = data.substring(0, 2)
        val stateCode = data.substring(2, 4)
        if (funcCode == "02") {
            val newLock = stateCode == "00"
            lastParsedLockState = newLock
            _realtimeState.value = _realtimeState.value.copy(
                lastPlainHex = fullHex,
                isLocked = newLock
            )
        }
    }

    /**
     * 帧头 C30A + 第三字节类型码（MESSAGE_TYPES）：C4 BLEAPP、C5 DATA、C6 ALERT、C7 STATUS（见 utils/deviceMessages.js）。
     */
    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim().replace(" ", "")
        val out = ByteArray(clean.length / 2)
        var i = 0
        while (i < clean.length) {
            out[i / 2] = clean.substring(i, i + 2).toInt(16).toByte()
            i += 2
        }
        return out
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val builder = StringBuilder(bytes.size * 2)
        bytes.forEach { b -> builder.append(String.format("%02X", b)) }
        return builder.toString()
    }

    private class NordicClient(context: Context) : BleManager(context) {
        private var writeCharacteristic: BluetoothGattCharacteristic? = null
        private var notifyCharacteristic: BluetoothGattCharacteristic? = null
        private var onNotify: ((ByteArray) -> Unit)? = null

        fun setOnNotify(block: (ByteArray) -> Unit) {
            onNotify = block
        }

        fun isReadyToWrite(): Boolean = writeCharacteristic != null

        fun writePayload(
            payload: ByteArray,
            onWriteType: (Int) -> Unit,
            onDone: () -> Unit,
            onFail: (Int) -> Unit
        ) {
            val writeChar = writeCharacteristic
            if (writeChar == null) {
                onFail(-1)
                return
            }

            val properties = writeChar.properties
            val writeType = if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            onWriteType(writeType)

            writeCharacteristic(writeChar, payload, writeType)
                .done { onDone() }
                .fail { _, status -> onFail(status) }
                .enqueue()
        }

        override fun getGattCallback(): BleManagerGattCallback {
            return object : BleManagerGattCallback() {
                override fun initialize() {
                    notifyCharacteristic?.let { c ->
                        setNotificationCallback(c).with { _, data ->
                            val value = data.value ?: byteArrayOf()
                            if (value.isNotEmpty()) {
                                onNotify?.invoke(value)
                            }
                        }
                        enableNotifications(c).enqueue()
                    }
                }

                override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                    val service = gatt.getService(UUID.fromString("00001000-0000-1000-8000-00805F9B34FB"))
                    writeCharacteristic = service?.getCharacteristic(UUID.fromString("00001001-0000-1000-8000-00805F9B34FB"))
                    notifyCharacteristic = service?.getCharacteristic(UUID.fromString("00001002-0000-1000-8000-00805F9B34FB"))
                    return writeCharacteristic != null && notifyCharacteristic != null
                }

                override fun onServicesInvalidated() {
                    writeCharacteristic = null
                    notifyCharacteristic = null
                }
            }
        }
    }

    fun logPhase(phase: String) {
        debug("phase=$phase activeMac=$activeMac ble=${_availability.value.bleAvailable} net=${_availability.value.networkAvailable}")
        logger.info(LogEntry(tag = "ble_phase", message = "BLE lifecycle", payload = mapOf("phase" to phase)))
    }
}

internal fun resolveToggleMuteParamValue(mute: Boolean): Pair<String, String> {
    return if (mute) {
        "0F" to "00"
    } else {
        "0F" to "0A"
    }
}

internal fun resolveMoveModeValue(enabled: Boolean): String = if (enabled) "01" else "00"

internal fun resolveMoveStepValue(): String = "01"
