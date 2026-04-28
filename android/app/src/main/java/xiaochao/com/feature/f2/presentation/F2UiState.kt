package xiaochao.com.feature.f2.presentation

import xiaochao.com.data.model.DeviceType

data class DeviceOption(
    val name: String,
    val key: String
)

data class F2UiState(
    val deviceType: DeviceType = DeviceType.F2,
    /** 是否有任意可用通道（F1=BLE 可用；F2=BLE 或 4G 任一可用） */
    val hasAnyChannel: Boolean = false,
    val deviceKey: String = "",
    val deviceMacAddress: String = "",
    val frameNumber: String = "--",
    val controlModel: String = "",
    val historyMileKm: Int = 0,
    val sosPhoneMasked: String = "--",
    val isOwner: Boolean = false,
    val onlineStatus: Int = 0,
    val batteryPercent: Int = 0,
    val showLightning: Boolean = false,
    val voltageText: String = "0.0V",
    val signalStrengthLevel: Int = 0,
    val bleConnected: Boolean = false,
    val bleSystemConnected: Boolean = false,
    val bleConnecting: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isLocked: Boolean = true,
    val isMuteEnabled: Boolean = false,
    val isAutoSenseEnabled: Boolean = false,
    val gpsLocked: Boolean = false,
    val currentLocationAddress: String = "获取地址中",
    val trackLocationAddress: String = "获取地址中",
    val trackLatitude: Double = 0.0,
    val trackLongitude: Double = 0.0,
    val timeText: String = "00:00",
    val mileageText: String = "0/km",
    val durationText: String = "--",
    val topSpeedText: String = "0km/h",
    val avgSpeedText: String = "0km/h",
    val vehicleOptions: List<DeviceOption> = emptyList(),
    val navigateToAddVehicle: Boolean = false,
    val navigateToSettings: Boolean = false,
    val navigateToShareUsers: Boolean = false,
    val navigateToM1B: Boolean = false,
    val tipMessage: String? = null,
    val isLoading: Boolean = false,
)
