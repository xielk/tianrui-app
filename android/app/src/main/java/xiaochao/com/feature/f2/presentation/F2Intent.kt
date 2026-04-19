package xiaochao.com.feature.f2.presentation

import xiaochao.com.data.model.DeviceType

sealed interface F2Intent {
    data object OnScanClick : F2Intent
    data object OnBluetoothClick : F2Intent
    data class OnLockSliderChange(val locked: Boolean) : F2Intent
    data object OnMuteClick : F2Intent
    data object OnAutoSenseClick : F2Intent
    data object OnFindBikeClick : F2Intent
    data object OnSettingsClick : F2Intent
    data object OnShareClick : F2Intent
    data object OnCurrentLocationClick : F2Intent
    data object OnHistoryTrackClick : F2Intent
    data object OnRefresh : F2Intent
    /** 页面可见时同步 BLE 连接状态 */
    data object OnScreenActive : F2Intent
    /** 切换设备：断开旧 BLE，连接新设备 BLE，重新拉取数据 */
    data class OnDeviceSwitch(
        val deviceKey: String,
        val deviceType: DeviceType,
    ) : F2Intent
    data object OnAddVehicleNavigationConsumed : F2Intent
    data object OnSettingsNavigationConsumed : F2Intent
    data object OnShareNavigationConsumed : F2Intent
    data object OnM1BNavigationConsumed : F2Intent
    data object OnTipConsumed : F2Intent
    data class OnSosSave(val phone: String) : F2Intent
}
