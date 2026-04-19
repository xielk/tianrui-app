package xiaochao.com.data.ble

/**
 * 与 UniApp 侧 `utils/deviceMessages.js` + 各 parser 对齐的明文 hex 解析（解密后的 plainHex）。
 *
 *类型码取 plainHex.substring(4, 6)，对应 MESSAGE_TYPES：C4 BLEAPP、C5 DATA、C6 ALERT、C7 STATUS。
 */
object BleDeviceMessageParser {

    private fun String.hexPairs(): List<String>? {
        val clean = this.trim().replace(" ", "")
        if (clean.length % 2 != 0) return null
        return clean.chunked(2)
    }

    /** 与 F1 `handledAlertData`：byte5 电源 bit7、byte8 感应 bit7、byte10 报警音量高 4 bit（与 alertParser 一致） */
    fun realtimeTripletFromAlertData(dataAfterHeader: String): Triple<Boolean, Boolean, Boolean>? {
        if (dataAfterHeader.length < 18) return null
        val b5 = dataAfterHeader.substring(6, 8).toIntOrNull(16) ?: return null
        val b8 = dataAfterHeader.substring(12, 14).toIntOrNull(16) ?: return null
        val b10 = dataAfterHeader.substring(16, 18).toIntOrNull(16) ?: return null
        val powerOn = (b5 and 0x80) != 0
        val isLocked = !powerOn
        val autoSenseEnabled = (b8 and 0x80) != 0
        val volNibble = (b10 shr 4) and 0x0F
        val alarmVol = if (volNibble <= 10) volNibble else 0
        val isMuted = alarmVol == 0
        return Triple(isLocked, isMuted, autoSenseEnabled)
    }

    fun summarizeBleApp(plainHex: String): String? {
        if (plainHex.length < 10) return null
        val func = plainHex.substring(6, 8).uppercase()
        val state = plainHex.substring(8, 10).uppercase()
        val fname = BLEAPP_NAMES[func] ?: return "BLE_MSG C4 BLEAPP func=$func state=$state raw=$plainHex"
        val desc = BLEAPP_STATE_DESC[func]?.get(state)
            ?: return "BLE_MSG C4 BLEAPP ${fname} state=$state (未知状态) raw=$plainHex"
        return "BLE_MSG C4 BLEAPP ${fname}[$func/$state] $desc raw=$plainHex"
    }

    fun summarizeData(plainHex: String): String? {
        val pairs = plainHex.hexPairs() ?: return null
        if (pairs.size < 13) return null
        val bytes = pairs.subList(2, 13)
        if (bytes[0].uppercase() != "C5") return null
        val b2 = bytes[2].toIntOrNull(16) ?: return null
        val lithium = (b2 and 0x80) != 0
        val soc = b2 and 0x7F
        val speed = bytes[3].toIntOrNull(16) ?: 0
        val curB = bytes[4].toIntOrNull(16) ?: 0
        val currentSign = if ((curB and 0x80) != 0) "正" else "负"
        val currentVal = curB and 0x7F
        val hi = bytes[6].toIntOrNull(16) ?: 0
        val lo = bytes[7].toIntOrNull(16) ?: 0
        val v = ((hi shl 8) or lo) * 0.1
        val voltageStr = if (v >= 1000.0) "无效" else String.format("%.1fV", v)
        val mh = bytes[8].toIntOrNull(16) ?: 0
        val mm = bytes[9].toIntOrNull(16) ?: 0
        val ml = bytes[10].toIntOrNull(16) ?: 0
        val odo = ((mh shl 16) or (mm shl 8) or ml) * 0.1
        val odoStr = if (odo >= 1677721.5) "无效" else String.format("%.1fkm", odo)
        val bat = if (lithium) "锂电" else "铅酸"
        return "BLE_MSG C5 DATA bat=$bat soc=$soc speed=$speed current=${currentSign}${currentVal} U=$voltageStr odo=$odoStr raw=$plainHex"
    }

    fun summarizeAlert(plainHex: String, data: String): String? {
        if (data.length < 18) return "BLE_MSG C6 ALERT data_short len=${data.length} raw=$plainHex"
        val trip = realtimeTripletFromAlertData(data) ?: return null
        val (locked, muted, auto) = trip
        val b2 = data.substring(0, 2)
        val b5 = data.substring(6, 8)
        val b8 = data.substring(12, 14)
        val b10 = data.substring(16, 18)
        return "BLE_MSG C6 ALERT lock=$locked mute=$muted autoSense=$auto byte2=$b2 byte5=$b5 byte8=$b8 byte10=$b10 raw=$plainHex"
    }

    fun summarizeStatus(plainHex: String): String? {
        val pairs = plainHex.hexPairs() ?: return null
        if (pairs.size < 11) return null
        val bytes = pairs.subList(2, 12)
        if (bytes[0].uppercase() != "C7") return null
        val lockBits = (bytes[1].toIntOrNull(16) ?: 0) shr 6 and 3
        val lockStr = when (lockBits) {
            0 -> "UNLOCKED"
            1 -> "LOCKED"
            3 -> "INVALID"
            else -> "UNKNOWN($lockBits)"
        }
        val defDist = (bytes[3].toIntOrNull(16) ?: 0) and 0x0F
        val bh = bytes[4].toIntOrNull(16) ?: 0
        val bl = bytes[5].toIntOrNull(16) ?: 0
        val ah = ((bh shl 8) or bl) * 0.1
        val curMi = (((bytes[6].toIntOrNull(16) ?: 0) shl 8) or (bytes[7].toIntOrNull(16) ?: 0)) * 0.1
        val curMiStr = if (curMi >= 6553.5) "无效" else String.format("%.2fkm", curMi)
        return "BLE_MSG C7 STATUS lock=$lockStr defenseDist=$defDist capacity=${String.format("%.1f", ah)}Ah trip=$curMiStr raw=$plainHex"
    }

    private val BLEAPP_NAMES: Map<String, String> = mapOf(
        "01" to "设防状态",
        "02" to "电源状态",
        "03" to "坐垫锁状态",
        "04" to "防盗报警",
        "05" to "防盗抢状态",
        "06" to "电子龙头锁状态",
        "07" to "大灯控制状态",
        "08" to "欠压保护",
        "09" to "刹车故障",
        "0A" to "转把故障",
        "0B" to "电机故障",
        "0C" to "控制器故障",
        "0D" to "控制器保护",
        "0E" to "防飞车保护",
        "0F" to "过流保护",
        "10" to "堵转保护",
        "11" to "车电分离报警",
        "12" to "车辆倾倒报警",
        "13" to "非法上电报警",
        "14" to "非法移车报警",
    )

    private val BLEAPP_STATE_DESC: Map<String, Map<String, String>> = mapOf(
        "01" to mapOf("00" to "设防状态已解除", "01" to "已设防（超出设定距离）"),
        "02" to mapOf("00" to "电源已切断", "01" to "电源已接通"),
        "03" to mapOf("00" to "坐垫锁已关闭", "01" to "坐垫锁已打开"),
        "04" to mapOf("01" to "检测到震动/移动"),
        "05" to mapOf("00" to "防盗抢解除", "01" to "防盗抢启动（已锁电机）"),
        "06" to mapOf("00" to "龙头锁已关闭", "01" to "龙头锁已打开"),
        "07" to mapOf("00" to "大灯已关闭", "01" to "大灯已开启"),
        "08" to mapOf("01" to "已欠压保护，请充电"),
        "09" to mapOf("01" to "刹车电路故障"),
        "0A" to mapOf("01" to "转把故障"),
        "0B" to mapOf("01" to "电机故障"),
        "0C" to mapOf("01" to "控制器故障"),
        "0D" to mapOf("01" to "控制器保护"),
        "0E" to mapOf("01" to "防飞车保护"),
        "0F" to mapOf("01" to "控制器过流保护"),
        "10" to mapOf("01" to "控制器堵转保护"),
        "11" to mapOf("01" to "电池分离报警"),
        "12" to mapOf("01" to "车辆倾倒报警"),
        "13" to mapOf("01" to "未解锁强制上电"),
        "14" to mapOf("01" to "检测到未授权移动"),
    )
}
