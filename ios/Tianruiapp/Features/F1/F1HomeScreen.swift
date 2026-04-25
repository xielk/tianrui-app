import SwiftUI
import AVFoundation
import UIKit
import CoreLocation
import WebKit

enum F1Layout {
    static let mainCardRadius: CGFloat = 34
    static let pageHorizontalPadding: CGFloat = 16
}

private enum F1BottomTab {
    case home
    case mine
}

struct F1HomeScreen: View {
    @ObservedObject var viewModel: F1ViewModel
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    let onLogout: () -> Void
    private let data = F1MockData.sample
    @State private var vehicleMenuExpanded = false
    @State private var selectedTab: F1BottomTab = .home

    init(viewModel: F1ViewModel, onLogout: @escaping () -> Void = {}) {
        self.viewModel = viewModel
        self.onLogout = onLogout
    }

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                AppColor.f1Background
                    .ignoresSafeArea()

                Group {
                    if selectedTab == .mine {
                        F1MineScreen(viewModel: viewModel, onLogout: onLogout)
                    } else if viewModel.currentLayoutMode == .m1b {
                        M1BHomeContent(viewModel: viewModel, data: data, onVehicleTap: { vehicleMenuExpanded.toggle() })
                    } else {
                        ScrollView(showsIndicators: false) {
                            VStack(spacing: 6) {
                                F1HeaderView(
                                    deviceName: viewModel.selectedDeviceName,
                                    showVehicleSelector: viewModel.shouldShowVehicleSelector,
                                    totalMileage: data.totalMileage,
                                    bleIconName: viewModel.bleIconName,
                                    bleIconBlinking: viewModel.bleIconBlinking,
                                    bleSystemConnected: viewModel.bleSystemConnected,
                                    show4GIcon: viewModel.currentLayoutMode == .f2,
                                    signalStrengthLevel: viewModel.signalStrengthLevel,
                                    onScanTap: viewModel.onScanTap,
                                    onBluetoothTap: viewModel.onBluetoothTap,
                                    onBlePairedTap: viewModel.onBlePairedTap,
                                    onVehicleTap: { vehicleMenuExpanded.toggle() }
                                )
                                F1HeroView(voltage: data.voltage, batteryPercent: data.batteryPercent)
                                F1MainControlCardView(viewModel: viewModel)
                                if F1MapSectionMode.forLayout(viewModel.currentLayoutMode) == .split {
                                    F1SplitMapCardsView(
                                        onCurrentTap: openCurrentLocation,
                                        onTrackTap: viewModel.onTrackTap
                                    )
                                } else {
                                    F1MapCardView(
                                        latitude: viewModel.latitude,
                                        longitude: viewModel.longitude,
                                        address: viewModel.currentLocationAddress,
                                        onMapTap: openCurrentLocation
                                    )
                                }
                                F1TripMetricsCardView(
                                    timeText: viewModel.currentLayoutMode == .f2 ? viewModel.metricsTimeText : nil,
                                    mileageText: data.totalMileage,
                                    durationText: "--",
                                    topSpeedText: "--",
                                    averageSpeedText: "--"
                                )
                            }
                            .padding(.horizontal, F1Layout.pageHorizontalPadding)
                            .padding(.top, 4)
                            .padding(.bottom, 92)
                        }
                    }
                }

                F1BottomTabView(
                    isHomeSelected: selectedTab == .home,
                    onHomeTap: { selectedTab = .home },
                    onMineTap: { selectedTab = .mine }
                )

                if let tip = viewModel.statusTip, !tip.isEmpty {
                    Text(tip)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(
                            Capsule(style: .continuous)
                                .fill(Color.black.opacity(0.72))
                        )
                        .padding(.bottom, 68)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }

                if vehicleMenuExpanded && viewModel.shouldShowVehicleSelector {
                    F1VehicleDropdownOverlay(
                        selectedKey: UserDefaults.standard.string(forKey: "last_device_key") ?? "",
                        vehicles: viewModel.vehicleOptions,
                        onVehicleSelect: { option in
                            vehicleMenuExpanded = false
                            viewModel.selectVehicle(option)
                        },
                        onAddVehicle: {
                            vehicleMenuExpanded = false
                            viewModel.onAddVehicleTap()
                        },
                        onDismiss: { vehicleMenuExpanded = false }
                    )
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(isPresented: $viewModel.isShareUsersPresented) {
                F1ShareUsersScreen(deviceKey: viewModel.selectedDeviceKey)
            }
        }
        .animation(.easeOut(duration: 0.2), value: viewModel.statusTip)
        .task {
            viewModel.start()
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                viewModel.onAppBecameActive()
            }
        }
        .onChange(of: viewModel.shouldOpenBluetoothSettings) { shouldOpen in
            guard shouldOpen else { return }
            openBluetoothSettings()
            viewModel.onBluetoothSettingsOpened()
        }
        .fullScreenCover(isPresented: $viewModel.isSettingsPresented) {
            F1SettingsScreen(viewModel: viewModel, onBack: viewModel.onSettingsDismiss)
        }
        .fullScreenCover(isPresented: $viewModel.isAddVehiclePresented) {
            AddVehicleScreen(viewModel: viewModel)
        }
        .fullScreenCover(isPresented: $viewModel.isBleCalibrationPresented) {
            BleCalibrationScreen(viewModel: viewModel, onBack: viewModel.onBleCalibrationDismiss)
        }
        .fullScreenCover(isPresented: $viewModel.isTrackMapPresented) {
            F1TrackMapScreen(
                points: viewModel.trackMapPoints,
                showNoDataHint: viewModel.trackMapShouldShowNoDataHint,
                onBack: viewModel.onTrackMapDismiss
            )
        }
        .fullScreenCover(isPresented: $viewModel.isProductDetailsPresented) {
            F1ProductDetailsScreen(
                deviceKey: viewModel.selectedDeviceKey,
                onBack: viewModel.onProductDetailsDismiss
            )
        }
        .fullScreenCover(isPresented: $viewModel.isOtaPresented) {
            F1OtaUpdateScreen(viewModel: viewModel.makeOtaViewModel(), onBack: viewModel.onOtaDismiss)
        }
        .alert("关闭感应解锁", isPresented: $viewModel.showAutoSenseDisableConfirm) {
            Button("取消", role: .cancel) {
                viewModel.onDisableAutoSenseCancel()
            }
            Button("确定") {
                viewModel.confirmDisableAutoSense()
            }
        } message: {
            Text("确定后将发送感应解锁关闭指令，并跳转到系统蓝牙-我的设备。")
        }
    }

    private func openCurrentLocation() {
        guard let url = viewModel.currentLocationURL else {
            viewModel.onMapLocationUnavailable()
            return
        }
        openURL(url)
    }

    private func openBluetoothSettings() {
        if let bluetoothURL = URL(string: "App-Prefs:root=Bluetooth"), UIApplication.shared.canOpenURL(bluetoothURL) {
            UIApplication.shared.open(bluetoothURL)
            return
        }

        if let fallback = URL(string: UIApplication.openSettingsURLString) {
            openURL(fallback)
        }
    }
}

private struct F1MineScreen: View {
    @ObservedObject var viewModel: F1ViewModel
    let onLogout: () -> Void

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 12) {
                HStack {
                    Spacer()
                    Button("退出登录") {
                        onLogout()
                    }
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(AppColor.titleBlack)
                }

                Circle()
                    .fill(Color.white)
                    .frame(width: 72, height: 72)

                Text("我的")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(AppColor.titleBlack)

                mineMenuItem(title: "绑定车辆") {
                    viewModel.onAddVehicleTap()
                }
                mineMenuItem(title: "蓝牙感应距离校准") {
                    if viewModel.bleSystemConnected {
                        viewModel.onBlePairedTap()
                    } else {
                        viewModel.showTip("请先在首页连接并完成蓝牙配对")
                    }
                }
                mineMenuItem(title: "产品详情") {
                    viewModel.onProductDetailsTap()
                }
                mineMenuItem(title: "检查OTA更新") {
                    viewModel.onOtaTap()
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 16)
            .padding(.bottom, 100)
        }
    }

    private func mineMenuItem(title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(title)
                    .font(.system(size: 15, weight: .regular))
                    .foregroundStyle(AppColor.titleBlack)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .regular))
                    .foregroundStyle(Color(red: 0.65, green: 0.69, blue: 0.76))
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 20)
            .background(.white)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

private struct F1VehicleDropdownOverlay: View {
    let selectedKey: String
    let vehicles: [F1ViewModel.VehicleOption]
    let onVehicleSelect: (F1ViewModel.VehicleOption) -> Void
    let onAddVehicle: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        ZStack(alignment: .topLeading) {
            Color.black.opacity(0.001)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)

            VStack(spacing: 0) {
                if vehicles.isEmpty {
                    Text("暂无设备")
                        .font(.system(size: 16, weight: .regular))
                        .foregroundStyle(Color(red: 0.07, green: 0.10, blue: 0.16))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 14)
                    Divider()
                } else {
                    ForEach(Array(vehicles.enumerated()), id: \.element.id) { index, item in
                        Button(action: { onVehicleSelect(item) }) {
                            Text(item.name)
                                .font(.system(size: 16, weight: item.key == selectedKey ? .medium : .regular))
                                .foregroundStyle(item.key == selectedKey
                                                 ? Color(red: 0.71, green: 0.17, blue: 0.91)
                                                 : Color(red: 0.07, green: 0.10, blue: 0.16))
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 18)
                                .padding(.vertical, 14)
                        }
                        .buttonStyle(.plain)

                        if index != vehicles.count - 1 {
                            Divider()
                        }
                    }
                    Divider()
                }

                Button(action: onAddVehicle) {
                    Text("+ 添加车辆")
                        .font(.system(size: 16, weight: .regular))
                        .foregroundStyle(Color(red: 0.45, green: 0.52, blue: 0.62))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.plain)
            }
            .frame(width: 156)
            .background(.white)
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .shadow(color: .black.opacity(0.12), radius: 18, x: 0, y: 8)
            .padding(.leading, 12)
            .padding(.top, 84)
        }
    }
}

private struct M1BHomeContent: View {
    @ObservedObject var viewModel: F1ViewModel
    let data: F1MockData
    let onVehicleTap: () -> Void

    var body: some View {
        GeometryReader { proxy in
            let contentWidth = max(0, proxy.size.width - (F1Layout.pageHorizontalPadding * 2))
            ScrollView(showsIndicators: false) {
                VStack(spacing: 16) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 6) {
                        if viewModel.shouldShowVehicleSelector {
                            Button(action: onVehicleTap) {
                                HStack(spacing: 4) {
                                    Text(m1bDisplayName)
                                        .font(.system(size: 24, weight: .bold))
                                        .foregroundStyle(AppColor.titleBlack)
                                        .lineLimit(1)
                                        .truncationMode(.tail)
                                    Image(systemName: "chevron.down")
                                        .font(.system(size: 10, weight: .semibold))
                                        .foregroundStyle(Color(red: 0.26, green: 0.33, blue: 0.45))
                                }
                                .frame(maxWidth: 220, alignment: .leading)
                            }
                            .buttonStyle(.plain)
                        }
                        Text("历史总里程：\(data.totalMileage)")
                            .font(.system(size: 16, weight: .regular))
                            .foregroundStyle(Color(red: 0.18, green: 0.23, blue: 0.33))
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .layoutPriority(1)

                    Spacer(minLength: 10)

                    HStack(spacing: 18) {
                        Button(action: viewModel.onScanTap) {
                            Image("f2_scan")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 28, height: 28)
                        }
                        .buttonStyle(.plain)

                        F1SignalBadgeView(level: viewModel.signalStrengthLevel)
                            .frame(width: 40, height: 40)
                    }
                    .frame(width: 86, alignment: .trailing)
                    .padding(.top, 10)
                }

                ZStack(alignment: .bottom) {
                    Color.clear
                    Image("f2_bike")
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: .infinity)
                        .frame(height: 236)
                        .offset(y: 10)
                        .opacity(0.8)
                }
                .frame(height: 214)

                ZStack(alignment: .topTrailing) {
                    VStack(spacing: 12) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(AppColor.mapPlaceholder)
                            if let url = F1MapURLBuilder.staticMapURL(latitude: viewModel.latitude, longitude: viewModel.longitude) {
                                AsyncImage(url: url) { phase in
                                    switch phase {
                                    case .empty:
                                        Text("加载地图中...")
                                            .font(.system(size: 14, weight: .regular))
                                            .foregroundStyle(Color(red: 0.43, green: 0.50, blue: 0.59))
                                    case .success(let image):
                                        image
                                            .resizable()
                                            .scaledToFill()
                                    case .failure:
                                        Text("地图加载失败")
                                            .font(.system(size: 14, weight: .regular))
                                            .foregroundStyle(Color(red: 0.43, green: 0.50, blue: 0.59))
                                    @unknown default:
                                        EmptyView()
                                    }
                                }
                            } else {
                                Text("暂无位置信息")
                                    .font(.system(size: 14, weight: .regular))
                                    .foregroundStyle(Color(red: 0.43, green: 0.50, blue: 0.59))
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 206)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .clipped()
                        .onTapGesture {
                            guard let url = viewModel.currentLocationURL else {
                                viewModel.onMapLocationUnavailable()
                                return
                            }
                            UIApplication.shared.open(url)
                        }

                        HStack(spacing: 6) {
                            Text("位置:")
                                .font(.system(size: 14, weight: .regular))
                                .foregroundStyle(Color(red: 0.54, green: 0.58, blue: 0.66))
                                .fixedSize(horizontal: true, vertical: false)
                                .frame(width: 30, alignment: .leading)
                            Text(F1MapAddressResolver.displayAddress(address: viewModel.currentLocationAddress, latitude: viewModel.latitude, longitude: viewModel.longitude))
                                .font(.system(size: 12, weight: .regular))
                                .foregroundStyle(AppColor.titleBlack)
                                .lineLimit(1)
                                .truncationMode(.tail)
//                                .frame(maxWidth: .infinity, alignment: .leading)
                                
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.top, 18)
                    .padding(.bottom, 14)

                    Button(action: viewModel.onTrackTap) {
                        Text("历史轨迹 >")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 22)
                            .padding(.vertical, 10)
                            .background(
                                LinearGradient(colors: [Color(red: 0.84, green: 0.00, blue: 1.00), Color(red: 0.43, green: 0.00, blue: 0.84)], startPoint: .leading, endPoint: .trailing)
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .padding(.top, 18)
                            .padding(.trailing, 18)
                    }
                    .buttonStyle(.plain)
                }
                .frame(width: contentWidth)
                .frame(height: 332)
                .background(.white)
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))

                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(.white)
                    .frame(width: contentWidth)
                    .frame(height: 118)
                    .overlay {
                        HStack(spacing: 0) {
                            m1Metric(value: data.totalMileage, title: "里程")
                            m1Metric(value: "--", title: "耗时")
                            m1Metric(value: "--", title: "极速")
                            m1Metric(value: "--", title: "匀速")
                        }
                        .padding(.horizontal, 18)
                        .padding(.vertical, 16)
                    }
            }
            .frame(width: contentWidth, alignment: .center)
            .padding(.top, 12)
            .padding(.bottom, 160)
            .frame(maxWidth: .infinity)
        }
        }
    }

    private var m1bDisplayName: String {
        if let option = viewModel.vehicleOptions.first(where: { $0.key == viewModel.selectedDeviceKey }) {
            return option.name
        }
        return viewModel.selectedDeviceName
    }

    private func m1Metric(value: String, title: String) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.system(size: 22, weight: .regular))
                .foregroundStyle(Color(red: 0.08, green: 0.11, blue: 0.18))
                .lineLimit(1)
                .minimumScaleFactor(0.75)
            Text(title)
                .font(.system(size: 13, weight: .regular))
                .foregroundStyle(Color(red: 0.44, green: 0.49, blue: 0.56))
        }
        .frame(maxWidth: .infinity)
    }

}

private struct M1BSignalBadge: View {
    let level: Int

    var body: some View {
        ZStack {
            Circle()
                .fill(Color.white.opacity(0.34))
                .frame(width: 38, height: 38)

            HStack(alignment: .bottom, spacing: 1.2) {
                ForEach(0..<5, id: \.self) { index in
                    RoundedRectangle(cornerRadius: 0.8, style: .continuous)
                        .fill(index <= activeIndex ? Color(red: 0.35, green: 0.40, blue: 0.50) : Color(red: 0.73, green: 0.77, blue: 0.83))
                        .frame(width: 2.6, height: CGFloat(5 + index * 2))
                }
            }
        }
    }

    private var activeIndex: Int {
        max(0, min(4, level))
    }
}

private struct AddVehicleScreen: View {
    @ObservedObject var viewModel: F1ViewModel
    @State private var deviceKey: String = ""
    @State private var isScannerPresented = false
    @State private var autoLaunched = false
    @State private var showPermissionIntro = false
    @State private var showPermissionDenied = false

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Button("返回") { viewModel.onAddVehicleDismiss() }
                    .foregroundStyle(AppColor.titleBlack)
                Spacer()
            }

            Text("添加车辆")
                .font(.system(size: 24, weight: .bold))
                .foregroundStyle(AppColor.deepBlue)
                .frame(maxWidth: .infinity, alignment: .leading)

            Text("请输入设备号进行绑定")
                .font(.system(size: 13, weight: .regular))
                .foregroundStyle(Color(red: 0.40, green: 0.44, blue: 0.52))
                .frame(maxWidth: .infinity, alignment: .leading)

            TextField("请输入设备号", text: $deviceKey)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(.white)
                .foregroundStyle(AppColor.secondaryText)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

            Button("扫码添加") {
                launchScannerWithPermission()
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(Color(red: 0.06, green: 0.10, blue: 0.16))
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

            Button(action: {
                viewModel.addVehicle(deviceKey: deviceKey)
            }) {
                Text(viewModel.isAddingVehicle ? "绑定中..." : "立即添加")
                    .frame(maxWidth: .infinity)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(Color(red: 0.00, green: 0.48, blue: 1.00))
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .disabled(viewModel.isAddingVehicle)
            .opacity(viewModel.isAddingVehicle ? 0.72 : 1)

            if let tip = viewModel.statusTip, !tip.isEmpty {
                Text(tip)
                    .font(.system(size: 13, weight: .regular))
                    .foregroundStyle(Color(red: 0.36, green: 0.40, blue: 0.46))
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.top, 24)
        .background(AppColor.f1Background.ignoresSafeArea())
        .task {
            if !autoLaunched {
                autoLaunched = true
                launchScannerWithPermission()
            }
        }
        .fullScreenCover(isPresented: $isScannerPresented) {
            QRScannerScreen(
                onResult: { value in
                    deviceKey = value.trimmingCharacters(in: .whitespacesAndNewlines)
                    isScannerPresented = false
                    viewModel.addVehicle(deviceKey: deviceKey)
                },
                onClose: {
                    isScannerPresented = false
                    viewModel.onAddVehicleScanCancelled()
                }
            )
        }
        .alert("需要摄像头权限", isPresented: $showPermissionIntro) {
            Button("继续") {
                isScannerPresented = true
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("扫码添加车辆需要摄像头权限。确认后将弹出系统权限申请。")
        }
        .alert("摄像头权限被拒绝", isPresented: $showPermissionDenied) {
            Button("去设置") {
                guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                UIApplication.shared.open(url)
            }
            Button("知道了", role: .cancel) {}
        } message: {
            Text("未授予摄像头权限，扫码功能不可用。可前往系统设置手动开启权限。")
        }
    }

    private func launchScannerWithPermission() {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        switch status {
        case .authorized:
            isScannerPresented = true
        case .notDetermined:
            showPermissionIntro = true
        default:
            showPermissionDenied = true
        }
    }
}

private struct BleCalibrationScreen: View {
    @ObservedObject var viewModel: F1ViewModel
    let onBack: () -> Void
    @State private var showConfirm = false
    @State private var showResult = false
    @State private var resultTitle = ""
    @State private var resultMessage = ""

    var body: some View {
        ZStack {
            Color(red: 0.96, green: 0.96, blue: 0.96).ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 8) {
                        Button(action: onBack) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundStyle(AppColor.titleBlack)
                        }
                        Text("蓝牙校准")
                            .font(.system(size: 22, weight: .bold))
                            .foregroundStyle(AppColor.titleBlack)
                    }

                    VStack(alignment: .leading, spacing: 10) {
                        Text("设置感应距离，让车辆更懂你！")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundStyle(AppColor.titleBlack)

                        Text("每部手机的蓝牙信号都不一样，为了让车辆更准确识别靠近或离开，需要先做一次感应校准。")
                            .font(.system(size: 15, weight: .regular))
                            .foregroundStyle(Color(red: 0.40, green: 0.40, blue: 0.40))
                            .lineSpacing(5)

                        Text("怎么做？")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundStyle(AppColor.titleBlack)

                        calibrationStep("1", "走到离车约 1 米的位置", "不用太精确，选一个你觉得刚好能自动解锁的距离。")
                        calibrationStep("2", "点击开始校准", "系统会记录当前距离的蓝牙信号。")
                        calibrationStep("3", "听到滴滴声即成功", "之后靠近或离开车辆时会更稳定触发。")

                        VStack(alignment: .leading, spacing: 6) {
                            Text("温馨提示")
                                .font(.system(size: 15, weight: .bold))
                            Text("校准时尽量避开人多或信号干扰强的位置。")
                                .font(.system(size: 14, weight: .regular))
                                .foregroundStyle(Color(red: 0.43, green: 0.43, blue: 0.43))
                            Text("不满意可以随时重新校准。")
                                .font(.system(size: 14, weight: .regular))
                                .foregroundStyle(Color(red: 0.43, green: 0.43, blue: 0.43))
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(red: 0.97, green: 0.98, blue: 0.98))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

                    Button(action: { showConfirm = true }) {
                        Text(calibrationButtonTitle)
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Color(red: 0.00, green: 0.48, blue: 1.00))
                            .clipShape(Capsule(style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .disabled(isConnecting || viewModel.isCalibratingBle)
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 30)
            }
        }
        .alert("校准提示", isPresented: $showConfirm) {
            Button("取消", role: .cancel) {}
            Button("开始校准") {
                Task {
                    let result = await viewModel.calibrateBleDistance()
                    switch result {
                    case .success:
                        resultTitle = "校准指令已发送"
                        resultMessage = "已向车辆发送校准命令，请留意车辆提示音；如无反应可重试。"
                    case .error(let err):
                        resultTitle = "校准失败"
                        resultMessage = err.message
                    }
                    showResult = true
                }
            }
        } message: {
            Text("请走到离车约1米的位置，然后点击确定开始校准")
        }
        .alert(resultTitle, isPresented: $showResult) {
            Button("确定", role: .cancel) {}
        } message: {
            Text(resultMessage)
        }
    }

    private var isConnecting: Bool {
        switch viewModel.connectionState {
        case .scanning, .connecting, .discovering, .reconnecting:
            return true
        default:
            return false
        }
    }

    private var calibrationButtonTitle: String {
        if viewModel.isCalibratingBle {
            return "校准中..."
        }
        if isConnecting {
            return "正在连接蓝牙，请稍后..."
        }
        if viewModel.connectionState == .ready {
            return "开始校准"
        }
        return "蓝牙未连接"
    }

    private func calibrationStep(_ number: String, _ title: String, _ note: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text(number)
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(AppColor.titleBlack)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 15, weight: .regular))
                    .foregroundStyle(AppColor.titleBlack)
                Text(note)
                    .font(.system(size: 13, weight: .regular))
                    .foregroundStyle(Color(red: 0.53, green: 0.53, blue: 0.53))
            }
        }
    }
}

enum F1SharePhoneFormatter {
    static func mask(_ phone: String) -> String {
        guard phone.count >= 11 else { return phone }
        let prefix = phone.prefix(3)
        let suffix = phone.suffix(4)
        return "\(prefix)****\(suffix)"
    }

    static func isValidPhone(_ value: String) -> Bool {
        let pattern = "^1[3-9]\\d{9}$"
        return value.range(of: pattern, options: .regularExpression) != nil
    }
}

private struct F1ShareUsersScreen: View {
    let deviceKey: String
    @Environment(\.dismiss) private var dismiss

    @State private var users: [SharedUserItem] = []
    @State private var isLoading = false
    @State private var addPhone = ""
    @State private var showOwnerDialog = false
    @State private var newOwnerPhone = ""
    @State private var tipMessage: String?

    var body: some View {
        ZStack {
            Color(red: 0.96, green: 0.96, blue: 0.97).ignoresSafeArea()

            VStack(spacing: 0) {
                HStack {
                    Button(action: { dismiss() }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundStyle(AppColor.titleBlack)
                    }
                    Spacer()
                    Text("用车人列表")
                        .font(.system(size: 18, weight: .medium))
                    Spacer()
                    Color.clear.frame(width: 18, height: 18)
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 12)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("添加用车用户")
                                .font(.system(size: 18, weight: .semibold))

                            HStack(spacing: 10) {
                                TextField("请输入手机号码", text: $addPhone)
                                    .keyboardType(.numberPad)
                                    .textInputAutocapitalization(.never)
                                    .autocorrectionDisabled(true)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 12)
                                    .background(Color.white)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                                            .stroke(Color(red: 0.89, green: 0.90, blue: 0.92), lineWidth: 1)
                                    )

                                Button("添加"){
                                    Task { await addUser() }
                                }
                                .font(.system(size: 16, weight: .regular))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 12)
                                .background(Color(red: 0.12, green: 0.55, blue: 1.00))
                                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                                .disabled(isLoading)
                            }
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                        VStack(alignment: .leading, spacing: 8) {
                            Text("用车人")
                                .font(.system(size: 18, weight: .semibold))

                            if isLoading {
                                Text("加载中...")
                                    .font(.system(size: 14, weight: .regular))
                                    .foregroundStyle(Color(red: 0.56, green: 0.57, blue: 0.60))
                                    .padding(.vertical, 12)
                            } else if users.isEmpty {
                                Text("暂无用车人")
                                    .font(.system(size: 14, weight: .regular))
                                    .foregroundStyle(Color(red: 0.56, green: 0.57, blue: 0.60))
                                    .padding(.vertical, 12)
                            } else {
                                ForEach(Array(users.enumerated()), id: \.element.id) { index, user in
                                    HStack(spacing: 14) {
                                        Circle()
                                            .fill(Color(red: 0.08, green: 0.52, blue: 1.00))
                                            .frame(width: 54, height: 54)
                                            .overlay(
                                                Text(user.isOwner ? "主" : "用")
                                                    .font(.system(size: 28, weight: .bold))
                                                    .foregroundStyle(.white)
                                            )

                                        Text(user.isOwner ? F1SharePhoneFormatter.mask(user.phone) : user.phone)
                                            .font(.system(size: 16, weight: .regular))
                                            .foregroundStyle(AppColor.titleBlack)
                                            .frame(maxWidth: .infinity, alignment: .leading)

                                        if user.isOwner {
                                            Button("更换车主") {
                                                showOwnerDialog = true
                                            }
                                            .font(.system(size: 14, weight: .regular))
                                            .foregroundStyle(.white)
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 8)
                                            .background(Color(red: 0.12, green: 0.55, blue: 1.00))
                                            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                                        } else {
                                            Button("删除") {
                                                Task { await removeUser(memberId: user.memberId) }
                                            }
                                            .font(.system(size: 14, weight: .regular))
                                            .foregroundStyle(.white)
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 8)
                                            .background(Color(red: 1.00, green: 0.32, blue: 0.32))
                                            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                                        }
                                    }
                                    .padding(.vertical, 8)

                                    if index != users.count - 1 {
                                        Divider()
                                    }
                                }
                            }
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                        Text("分享后，对方可在APP中查看和控制此车辆")
                            .font(.system(size: 14, weight: .regular))
                            .foregroundStyle(Color(red: 0.61, green: 0.64, blue: 0.68))
                            .padding(.top, 8)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    .padding(.bottom, 20)
                }
            }

            if let tipMessage, !tipMessage.isEmpty {
                Text(tipMessage)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(
                        Capsule(style: .continuous)
                            .fill(Color.black.opacity(0.72))
                    )
                    .padding(.bottom, 24)
                    .frame(maxHeight: .infinity, alignment: .bottom)
            }
        }
        .navigationBarBackButtonHidden(true)
        .task {
            await loadUsers()
        }
        .alert("更换车主", isPresented: $showOwnerDialog) {
            TextField("请输入新车主手机号", text: $newOwnerPhone)
                .keyboardType(.numberPad)
            Button("取消", role: .cancel) {
                newOwnerPhone = ""
            }
            Button("确定") {
                Task { await changeOwner() }
            }
        }
    }

    private func loadUsers() async {
        guard !deviceKey.isEmpty else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            users = try await AuthAPIClient.shared.fetchSharedUsers(deviceKey: deviceKey)
        } catch {
            showTip(error.localizedDescription)
        }
    }

    private func addUser() async {
        let phone = addPhone.trimmingCharacters(in: .whitespacesAndNewlines)
        guard F1SharePhoneFormatter.isValidPhone(phone) else {
            showTip("请输入正确的手机号码")
            return
        }

        do {
            try await AuthAPIClient.shared.shareDevice(deviceKey: deviceKey, phone: phone)
            addPhone = ""
            showTip("添加成功,请对方重新登录")
            await loadUsers()
        } catch {
            showTip(error.localizedDescription)
        }
    }

    private func removeUser(memberId: String) async {
        do {
            try await AuthAPIClient.shared.removeSharedUser(deviceKey: deviceKey, memberId: memberId)
            showTip("删除成功")
            await loadUsers()
        } catch {
            showTip(error.localizedDescription)
        }
    }

    private func changeOwner() async {
        let phone = newOwnerPhone.trimmingCharacters(in: .whitespacesAndNewlines)
        guard F1SharePhoneFormatter.isValidPhone(phone) else {
            showTip("请输入正确的手机号码")
            return
        }

        do {
            try await AuthAPIClient.shared.changeOwner(deviceKey: deviceKey, newOwnerPhone: phone)
            showOwnerDialog = false
            newOwnerPhone = ""
            showTip("更换车主成功")
            await loadUsers()
        } catch {
            showTip(error.localizedDescription)
        }
    }

    private func showTip(_ text: String) {
        tipMessage = text
        Task {
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            tipMessage = nil
        }
    }
}

#Preview {
    F1HomeScreen(viewModel: .preview)
}

private struct F1SettingsScreen: View {
    @ObservedObject var viewModel: F1ViewModel
    let onBack: () -> Void
    @State private var disarmValue: Double = 0
    @State private var armValue: Double = 0

    var body: some View {
        ZStack {
            LinearGradient(colors: [Color(red: 0.92, green: 0.96, blue: 1.0), Color(red: 0.97, green: 0.98, blue: 1.0)], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 10) {
                    HStack {
                        Button(action: onBack) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 18, weight: .medium))
                                .foregroundStyle(AppColor.titleBlack)
                                
                        }
                        Spacer()
                        Text("设置")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(AppColor.deepBlue)
                        Spacer()
                        Color.clear.frame(width: 18, height: 18)
                    }
                    .padding(.horizontal, 6)

                    F1SettingsCard {
                        Text("设备编号: \(viewModel.selectedDeviceKey.isEmpty ? "--" : viewModel.selectedDeviceKey)")
                            .font(.system(size: 14, weight: .regular))
                            .foregroundStyle(AppColor.secondaryText)
                    }

                    F1SettingsCard {
                        Text(">>>> 解防感应距离: \(Int(disarmValue.rounded()) + 1)档").font(.system(size: 17, weight: .semibold))
                        Slider(value: $disarmValue, in: 0...8, step: 1) { editing in
                            if !editing { viewModel.applyDisarmSensitivity(Int(disarmValue.rounded())) }
                        }
                        HStack { Text("近"); Spacer(); Text("远") }.font(.system(size: 13)).foregroundStyle(Color(red: 0.61, green: 0.65, blue: 0.70))
                    }

                    F1SettingsCard {
                        Text(">>>> 设防感应距离: \(Int(armValue.rounded()) + 1)档").font(.system(size: 17, weight: .semibold))
                        Slider(value: $armValue, in: 0...8, step: 1) { editing in
                            if !editing { viewModel.applyArmSensitivity(Int(armValue.rounded())) }
                        }
                        HStack { Text("近"); Spacer(); Text("远") }.font(.system(size: 13)).foregroundStyle(Color(red: 0.61, green: 0.65, blue: 0.70))
                    }

                    F1SettingsCard {
                        Text(">>>> 报警灵敏度: \(["低", "中", "高"][viewModel.alarmSensitivityIndex])").font(.system(size: 17, weight: .semibold))
                        HStack(spacing: 10) {
                            ForEach(0..<3, id: \.self) { idx in
                                RoundedRectangle(cornerRadius: 8, style: .continuous)
                                    .fill(idx <= viewModel.alarmSensitivityIndex ? .black : Color(red: 0.93, green: 0.94, blue: 0.99))
                                    .frame(maxWidth: .infinity, minHeight: 12, maxHeight: 12)
                                    .onTapGesture { viewModel.applyAlarmSensitivity(idx) }
                            }
                        }
                        HStack { Text("低"); Spacer(); Text("中"); Spacer(); Text("高") }.font(.system(size: 13)).foregroundStyle(Color(red: 0.61, green: 0.65, blue: 0.70))
                    }

                    F1SettingsCard {
                        Text(">>>> 自动关机时间").font(.system(size: 17, weight: .semibold))
                        HStack(spacing: 10) {
                            ForEach([0, 3, 5, 10], id: \.self) { option in
                                let selected = viewModel.autoShutdownMinutes == option
                                Text(option == 0 ? "禁用" : "\(option)分钟")
                                    .font(.system(size: 12, weight: .regular))
                                    .foregroundStyle(selected ? .white : Color(red: 0.45, green: 0.50, blue: 0.55))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(selected ? Color(red: 0.05, green: 0.09, blue: 0.16) : Color(red: 0.93, green: 0.94, blue: 0.99))
                                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                                    .onTapGesture { viewModel.applyAutoShutdown(option) }
                            }
                        }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.top, 14)
                .padding(.bottom, 28)
            }
        }
        .onAppear {
            disarmValue = Double(viewModel.disarmSensitivityLevel)
            armValue = Double(viewModel.armSensitivityLevel)
        }
    }
}

private struct F1SettingsCard<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            content()
        }
        .padding(16)
        .frame(maxWidth: .infinity)
        .background(.white)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

private struct F1TrackMapScreen: View {
    let points: [DeviceTrackPoint]
    let showNoDataHint: Bool
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(AppColor.titleBlack)
                }
                Spacer()
                Text("历史轨迹")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(AppColor.deepBlue)
                Spacer()
                Color.clear.frame(width: 18, height: 18)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(.white)

            TianDiTuTrackWebMapView(points: points)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .overlay(alignment: .top) {
            if showNoDataHint {
                Text("暂无轨迹数据")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Capsule(style: .continuous).fill(Color.black.opacity(0.72)))
                    .padding(.top, 70)
            }
        }
        .background(Color.white.ignoresSafeArea())
    }
}

enum TianDiTuWebMapConfig {
    static let apiKey = "aa26c88f9ffd85c001301709c4ca2557"
}

enum TianDiTuTrackHTMLBuilder {
    static func build(points: [DeviceTrackPoint]) -> String {
        let pointsJSON = encodedPoints(points)
        let apiKey = TianDiTuWebMapConfig.apiKey
        return """
        <!doctype html>
        <html>
        <head>
          <meta charset=\"utf-8\" />
          <meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no\" />
          <style>
            html, body, #map { width: 100%; height: 100%; margin: 0; padding: 0; }
            body { background: #ffffff; }
          </style>
          <script src=\"https://api.tianditu.gov.cn/api?v=4.0&tk=\(apiKey)\"></script>
        </head>
        <body>
          <div id=\"map\"></div>
          <script>
            const map = new T.Map('map');
            const rawPoints = \(pointsJSON);
            const lngLats = rawPoints.map(p => new T.LngLat(p.longitude, p.latitude));

            if (lngLats.length === 0) {
              map.centerAndZoom(new T.LngLat(116.397428, 39.90923), 5);
            } else {
              map.centerAndZoom(lngLats[0], 14);
              const polyline = new T.Polyline(lngLats, {
                color: '#1C73F8',
                weight: 6,
                opacity: 0.92,
                lineStyle: 'solid'
              });
              map.addOverLay(polyline);

              const startMarker = new T.Marker(lngLats[0]);
              map.addOverLay(startMarker);
              if (lngLats.length > 1) {
                const endMarker = new T.Marker(lngLats[lngLats.length - 1]);
                map.addOverLay(endMarker);
              }
              map.setViewport(lngLats);
            }
          </script>
        </body>
        </html>
        """
    }

    private static func encodedPoints(_ points: [DeviceTrackPoint]) -> String {
        let values = points.map { ["latitude": $0.latitude, "longitude": $0.longitude] }
        guard let data = try? JSONSerialization.data(withJSONObject: values),
              let json = String(data: data, encoding: .utf8) else {
            return "[]"
        }
        return json
    }
}

private struct TianDiTuTrackWebMapView: UIViewRepresentable {
    let points: [DeviceTrackPoint]

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView(frame: .zero)
        webView.scrollView.isScrollEnabled = false
        webView.isOpaque = false
        webView.backgroundColor = .white
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        let html = TianDiTuTrackHTMLBuilder.build(points: points)
        guard context.coordinator.lastHTML != html else { return }
        context.coordinator.lastHTML = html
        webView.loadHTMLString(html, baseURL: URL(string: "https://api.tianditu.gov.cn/"))
    }

    final class Coordinator {
        var lastHTML: String = ""
    }
}

enum F1TrackMapDisplayPolicy {
    static func shouldShowNoDataHint(points: [DeviceTrackPoint]) -> Bool {
        points.isEmpty
    }
}

enum F1ProductModelDisplayPolicy {
    static func modelName(from info: DeviceInfoResponse?) -> String {
        info?.deviceModel?.modelName.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }
}

private struct F1ProductDetailsScreen: View {
    let deviceKey: String
    let onBack: () -> Void

    @State private var info: DeviceInfoResponse?
    @State private var loadError: String?

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                topBar
                    .padding(.horizontal, 16)
                    .padding(.top, 12)

                vehicleStats
                    .padding(.horizontal, 16)
                    .padding(.top, 12)

                detailCard
                    .padding(.top, 5)
            }
            .padding(.bottom, 24)
        }
        .background(AppColor.f1Background.ignoresSafeArea())
        .task { await loadDeviceInfo() }
        .alert("加载失败", isPresented: Binding(
            get: { loadError != nil },
            set: { if !$0 { loadError = nil } }
        )) {
            Button("知道了", role: .cancel) {}
        } message: {
            Text(loadError ?? "")
        }
    }

    private var topBar: some View {
        ZStack {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(AppColor.titleBlack)
                }
                Spacer()
            }

            Text("车辆详情")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(AppColor.titleBlack)
        }
    }

    private var vehicleStats: some View {
        ZStack(alignment: .topTrailing) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Image(systemName: "chevron.right.2")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Color(red: 0.76, green: 0.82, blue: 0.94))
                    Text("车辆参数")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(AppColor.titleBlack)
                }

                VStack(alignment: .leading, spacing: 8) {
                    detailDimensionRow(title: "长", value: info?.deviceModel?.length, unit: "CM")
                    detailDimensionRow(title: "宽", value: info?.deviceModel?.width, unit: "CM")
                    detailDimensionRow(title: "高", value: info?.deviceModel?.height, unit: "CM")
                    detailDimensionRow(title: "重", value: info?.deviceModel?.weight, unit: "KG")
                }

                HStack(spacing: 8) {
                    detailStat(title: "电机功率", value: info?.deviceModel?.motorPower, unit: "W")
                    detailStat(title: "续航里程", value: info?.deviceModel?.range, unit: "KM")
                    detailStat(title: "电池容量", value: info?.deviceModel?.batteryCapacity, unit: "Ah")
                }
                .padding(.top, 6)
            }
            .padding(.bottom, 48)

            Image("f2_bike")
                .resizable()
                .scaledToFit()
                .frame(width: 230, height: 280)
                .opacity(0.35)
                .offset(x: 24, y: 28)
        }
        .frame(maxWidth: .infinity, minHeight: 310, alignment: .topLeading)
    }

    private var detailCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionHeader("产品信息")
            detailInfoRow(label: "车架号", value: displayText(info?.frameNo))
            detailInfoRow(label: "车型", value: displayText(F1ProductModelDisplayPolicy.modelName(from: info)))

            sectionHeader("中控信息")
            detailInfoRow(label: "硬件版本号", value: displayText(info?.iotVersion))
            detailInfoRow(label: "软件版本号", value: appVersionText)
            detailInfoRow(label: "IMEI", value: displayText(info?.imei))
        }
        .padding(.bottom, 18)
        .background(
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(.white)
        )
            .frame(maxWidth: .infinity)
    }

    private func sectionHeader(_ title: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "chevron.right.2")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Color(red: 0.76, green: 0.82, blue: 0.94))
            Text(title)
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(AppColor.titleBlack)
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .padding(.bottom, 10)
    }

    private func detailInfoRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.system(size: 15, weight: .regular))
                .foregroundStyle(AppColor.secondaryText)
            Spacer()
            Text(value)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(AppColor.titleBlack)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.white)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color(red: 0.93, green: 0.95, blue: 0.98), lineWidth: 1)
        )
        .padding(.horizontal, 16)
        .padding(.vertical, 5)
    }

    private func detailParam(title: String, value: String?, unit: String) -> some View {
        VStack(spacing: 6) {
            Text(title)
                .font(.system(size: 13, weight: .regular))
                .foregroundStyle(AppColor.secondaryText)
            Text("\(displayNumber(value))\(unit)")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(AppColor.titleBlack)
        }
        .frame(width: 74)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(Color(red: 0.90, green: 0.95, blue: 1.00))
        )
    }

    private func detailDimensionRow(title: String, value: String?, unit: String) -> some View {
        HStack(spacing: 12) {
            Text(title)
                .font(.system(size: 17, weight: .regular))
                .foregroundStyle(AppColor.secondaryText)
                .frame(width: 54, height: 40)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color(red: 0.90, green: 0.95, blue: 1.00))
                )

            Text("\(displayNumber(value))\(unit)")
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(AppColor.titleBlack)
        }
    }

    private func detailStat(title: String, value: String?, unit: String) -> some View {
        VStack(spacing: 6) {
            Text("\(displayNumber(value))\(unit)")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(Color(red: 0.60, green: 0.10, blue: 0.71))
            Text(title)
                .font(.system(size: 12, weight: .regular))
                .foregroundStyle(AppColor.secondaryText)
                .padding(.horizontal, 8)
                .padding(.vertical, 6)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(Color(red: 0.90, green: 0.95, blue: 1.00))
                )
        }
        .frame(width: 96)
    }

    private var appVersionText: String {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
        return displayText(version)
    }

    private func displayText(_ value: String?) -> String {
        let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? "--" : trimmed
    }

    private func displayNumber(_ value: String?) -> String {
        let text = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !text.isEmpty else { return "--" }
        guard let number = Double(text) else { return text }
        if number.truncatingRemainder(dividingBy: 1) == 0 {
            return String(Int(number))
        }
        return String(number)
    }

    private func loadDeviceInfo() async {
        guard !deviceKey.isEmpty else {
            loadError = "请先绑定设备"
            return
        }

        do {
            info = try await AuthAPIClient.shared.fetchDeviceInfo(deviceKey: deviceKey)
        } catch {
            loadError = error.localizedDescription
        }
    }
}

private struct QRScannerScreen: View {
    let onResult: (String) -> Void
    let onClose: () -> Void
    @State private var permissionState: Int = 0

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if permissionState == 2 {
                CameraScannerView(onCode: onResult)
                    .ignoresSafeArea()
            } else if permissionState == 1 {
                VStack(spacing: 12) {
                    Text("未授予摄像头权限，无法使用扫码功能")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                    Button("返回") { onClose() }
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(.black)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                        .background(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
            } else {
                ProgressView().tint(.white)
            }

            VStack {
                HStack(spacing: 12) {
                    Button(action: onClose) {
                        Image(systemName: "arrow.uturn.backward.circle.fill")
                            .font(.system(size: 28, weight: .medium))
                            .foregroundStyle(.white)
                    }
                    Text("请扫描二维码或车架码")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundStyle(.white)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 16)
                .padding(.top, 18)
                .padding(.bottom, 10)
                .background(Color.black.opacity(0.4))
                Spacer()
                Text("放弃扫码可返回手动输码")
                    .font(.system(size: 18, weight: .regular))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(Color.black.opacity(0.4))
            }
        }
        .task { await requestPermission() }
    }

    private func requestPermission() async {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        switch status {
        case .authorized:
            permissionState = 2
        case .notDetermined:
            permissionState = await AVCaptureDevice.requestAccess(for: .video) ? 2 : 1
        default:
            permissionState = 1
        }
    }
}

private struct CameraScannerView: UIViewRepresentable {
    let onCode: (String) -> Void

    func makeUIView(context: Context) -> ScannerUIView {
        let view = ScannerUIView()
        view.onCode = onCode
        view.startRunning()
        return view
    }

    func updateUIView(_ uiView: ScannerUIView, context: Context) {}

    static func dismantleUIView(_ uiView: ScannerUIView, coordinator: ()) {
        uiView.stopRunning()
    }
}

private final class ScannerUIView: UIView, AVCaptureMetadataOutputObjectsDelegate {
    var onCode: ((String) -> Void)?
    private let session = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var didEmit = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        configureSession()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        configureSession()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        previewLayer?.frame = bounds
    }

    func startRunning() {
        guard !session.isRunning else { return }
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.session.startRunning()
        }
    }

    func stopRunning() {
        guard session.isRunning else { return }
        session.stopRunning()
    }

    private func configureSession() {
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else { return }
        session.addInput(input)

        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else { return }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.qr, .code128, .code39, .ean13]

        let preview = AVCaptureVideoPreviewLayer(session: session)
        preview.videoGravity = .resizeAspectFill
        layer.addSublayer(preview)
        previewLayer = preview
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput,
                        didOutput metadataObjects: [AVMetadataObject],
                        from connection: AVCaptureConnection) {
        guard !didEmit,
              let first = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let value = first.stringValue else { return }
        didEmit = true
        onCode?(value)
    }
}

private struct F1OtaUpdateScreen: View {
    @ObservedObject var viewModel: F1OtaViewModel
    let onBack: () -> Void
    @State private var showConfirm = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                HStack {
                    Button("返回", action: onBack)
                        .font(.system(size: 16, weight: .medium))
                    Spacer()
                    Text("OTA 更新")
                        .font(.system(size: 22, weight: .bold))
                    Spacer()
                    Spacer().frame(width: 44)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("蓝牙 MAC: \(viewModel.macAddress.isEmpty ? "-" : viewModel.macAddress)")
                    Text("当前设备版本(0x180A/0x2A26): \(viewModel.currentVersion)")
                    Text("目标升级版本: \(viewModel.packageOptions[viewModel.selectedOptionIndex].version)")
                        .foregroundStyle(Color(red: 0.06, green: 0.55, blue: 0.32))
                }
                .font(.system(size: 14, weight: .regular))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(.white)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

                VStack(alignment: .leading, spacing: 10) {
                    Text("升级文件选择")
                        .font(.system(size: 16, weight: .semibold))
                    ForEach(Array(viewModel.packageOptions.enumerated()), id: \.offset) { index, item in
                        Button {
                            if !viewModel.isRunning {
                                viewModel.selectedOptionIndex = index
                            }
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: viewModel.selectedOptionIndex == index ? "largecircle.fill.circle" : "circle")
                                    .font(.system(size: 18, weight: .regular))
                                    .foregroundStyle(viewModel.selectedOptionIndex == index ? Color(red: 0.00, green: 0.48, blue: 1.00) : Color.gray)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(item.label)
                                        .font(.system(size: 14, weight: .medium))
                                        .foregroundStyle(AppColor.titleBlack)
                                    Text(item.url)
                                        .font(.system(size: 11, weight: .regular))
                                        .foregroundStyle(Color.gray)
                                        .lineLimit(1)
                                }
                                Spacer()
                            }
                            .padding(.vertical, 6)
                        }
                        .buttonStyle(.plain)
                        .disabled(viewModel.isRunning)
                    }
                }
                .padding(14)
                .background(.white)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

                VStack(spacing: 6) {
                    ProgressView(value: Double(viewModel.progress), total: 100)
                        .tint(Color(red: 0.00, green: 0.48, blue: 1.00))
                    Text("进度: \(viewModel.progress)%")
                        .font(.system(size: 14, weight: .semibold))
                    Text(viewModel.progressText)
                        .font(.system(size: 13, weight: .regular))
                        .foregroundStyle(Color.gray)
                }
                .padding(14)
                .background(.white)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

                Button {
                    showConfirm = true
                } label: {
                    Text(viewModel.isRunning ? "处理中..." : "开始升级")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .foregroundStyle(.white)
                        .font(.system(size: 17, weight: .bold))
                        .background(Color(red: 0.00, green: 0.48, blue: 1.00))
                        .clipShape(Capsule(style: .continuous))
                }
                .disabled(viewModel.isRunning)

                Button {
                    Task { await viewModel.refreshVersion() }
                } label: {
                    Text("升级完成后刷新版本")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .foregroundStyle(.white)
                        .font(.system(size: 16, weight: .semibold))
                        .background(Color(red: 0.06, green: 0.55, blue: 0.32))
                        .clipShape(Capsule(style: .continuous))
                }
                .disabled(viewModel.isRunning)

                Spacer(minLength: 0)
            }
            .padding(16)
            .background(AppColor.f1Background.ignoresSafeArea())
            .task {
                await viewModel.refreshVersion()
            }
            .alert("升级确认", isPresented: $showConfirm) {
                Button("取消", role: .cancel) {}
                Button("同意并开始") {
                    Task { await viewModel.startUpgrade() }
                }
            } message: {
                Text("当前版本: \(viewModel.currentVersion)\n目标版本: \(viewModel.packageOptions[viewModel.selectedOptionIndex].version)\n\n升级过程中请勿退出应用。")
            }
            
        }
    }
}
